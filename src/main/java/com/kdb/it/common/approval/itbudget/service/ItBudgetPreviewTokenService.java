package com.kdb.it.common.approval.itbudget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.config.ItBudgetPreviewProperties;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 전산예산 미리보기의 요청 해시 집합을 만료형 HMAC 토큰으로 결속한다. */
@Service
@EnableConfigurationProperties(ItBudgetPreviewProperties.class)
public class ItBudgetPreviewTokenService {

    private static final String INVALID_CODE = "IT_BUDGET_PREVIEW_INVALID";
    private static final String INVALID_MESSAGE = "미리보기 정보가 올바르지 않습니다.";
    private static final String EXPIRED_CODE = "IT_BUDGET_PREVIEW_EXPIRED";
    private static final String EXPIRED_MESSAGE = "미리보기 유효 시간이 만료되었습니다.";
    private static final Pattern BASE64URL_SEGMENT = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);
    private static final int HMAC_SHA256_LENGTH = 32;

    private final ItBudgetPreviewProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ItBudgetPreviewTokenService(
            ItBudgetPreviewProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        validateProperties();
        this.objectMapper =
                objectMapper
                        .copy()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = clock;
    }

    /**
     * 활성 키로 서버가 만든 미리보기 결속 정보를 서명한다.
     *
     * @param claims 신청자·요청 해시 집합·발급 및 만료 시각
     * @return {@code kid.base64url(claims).base64url(HMAC-SHA-256)} 형식의 불투명 토큰
     * @throws IllegalStateException 활성 키 설정 또는 토큰 직렬화가 올바르지 않을 때
     */
    public String issue(Claims claims) {
        requireUsableClaims(claims);
        String keyId = requireSigningKeyId(properties.activeKeyId());
        String signingKey = requireSigningKey(properties.activeSigningKey());
        String encodedClaims = encodeClaims(claims);
        String signingInput = keyId + "." + encodedClaims;
        return signingInput + "." + encode(sign(signingKey, signingInput));
    }

    /**
     * 토큰 서명·신청자·만료를 검증하고 결속된 요청 해시 집합을 반환한다.
     *
     * @param token 미리보기 발급 시 받은 불투명 토큰
     * @param requesterEno 현재 인증 주체의 사번
     * @return 서명 검증을 마친 미리보기 결속 정보
     * @throws ItBudgetApprovalException 토큰 형식·서명·신청자 결속이 잘못됐거나 만료됐을 때
     */
    public Claims verify(String token, String requesterEno) {
        String[] parts = splitToken(token);
        String signingKey = signingKeyFor(parts[0]);
        byte[] actualSignature = decode(parts[2]);
        if (actualSignature.length != HMAC_SHA256_LENGTH) {
            throw invalid();
        }
        String signingInput = parts[0] + "." + parts[1];
        verifySignature(sign(signingKey, signingInput), actualSignature);

        Claims claims = decodeClaims(parts[1]);
        requireValidClaims(claims);
        if (!MessageDigest.isEqual(
                claims.requesterEno().getBytes(StandardCharsets.UTF_8), safeBytes(requesterEno))) {
            throw invalid();
        }
        if (!Instant.now(clock).isBefore(claims.expiresAt())) {
            throw expired();
        }
        return claims;
    }

    /** 미리보기 발급 결과와 상신 요청을 함께 결속하는 서명 claims다. */
    public record Claims(
            String requesterEno,
            String requestDigest,
            String sourceSetDigest,
            String payloadSetDigest,
            String previewDigest,
            Instant issuedAt,
            Instant expiresAt) {}

    private String[] splitToken(String token) {
        if (token == null) {
            throw invalid();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3
                || !KEY_ID.matcher(parts[0]).matches()
                || !isCanonicalBase64Url(parts[1])
                || !isCanonicalBase64Url(parts[2])) {
            throw invalid();
        }
        return parts;
    }

    private String signingKeyFor(String keyId) {
        if (keyId.equals(properties.activeKeyId())) {
            return requireSigningKey(properties.activeSigningKey());
        }
        if (keyId.equals(properties.previousKeyId()) && hasText(properties.previousSigningKey())) {
            return properties.previousSigningKey();
        }
        throw invalid();
    }

    private String encodeClaims(Claims claims) {
        try {
            return encode(objectMapper.writeValueAsBytes(claims));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("미리보기 서명 생성에 실패했습니다.", exception);
        }
    }

    private Claims decodeClaims(String encodedClaims) {
        try {
            return objectMapper.readValue(decode(encodedClaims), Claims.class);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private byte[] sign(String key, String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("미리보기 서명 생성에 실패했습니다.", exception);
        }
    }

    private void verifySignature(byte[] expected, byte[] actual) {
        if (!MessageDigest.isEqual(expected, actual)) {
            throw invalid();
        }
    }

    private byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private boolean isCanonicalBase64Url(String value) {
        if (!BASE64URL_SEGMENT.matcher(value).matches()) {
            return false;
        }
        try {
            return value.equals(encode(Base64.getUrlDecoder().decode(value)));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String requireSigningKeyId(String keyId) {
        return requireKeyId(keyId, "활성");
    }

    private String requireKeyId(String keyId, String keyName) {
        if (!hasText(keyId) || !KEY_ID.matcher(keyId).matches()) {
            throw new IllegalStateException("미리보기 " + keyName + " 키 ID 설정이 올바르지 않습니다.");
        }
        return keyId;
    }

    private String requireSigningKey(String key) {
        if (!hasText(key) || key.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("미리보기 서명 키는 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
        return key;
    }

    private void requireUsableClaims(Claims claims) {
        try {
            requireValidClaims(claims);
        } catch (ItBudgetApprovalException exception) {
            throw new IllegalStateException("미리보기 서명 claims가 올바르지 않습니다.", exception);
        }
    }

    private void requireValidClaims(Claims claims) {
        if (claims == null
                || !hasText(claims.requesterEno())
                || !hasText(claims.requestDigest())
                || !hasText(claims.sourceSetDigest())
                || !hasText(claims.payloadSetDigest())
                || !hasText(claims.previewDigest())
                || claims.issuedAt() == null
                || claims.expiresAt() == null
                || !claims.expiresAt().isAfter(claims.issuedAt())
                || !claims.expiresAt().equals(claims.issuedAt().plus(properties.ttl()))) {
            throw invalid();
        }
    }

    /** 수동 생성 경로도 운영과 같은 키 회전 및 30분 수명 불변식을 지킨다. */
    private void validateProperties() {
        String activeKeyId = requireSigningKeyId(properties.activeKeyId());
        requireSigningKey(properties.activeSigningKey());
        boolean hasPreviousKeyId = hasText(properties.previousKeyId());
        boolean hasPreviousSigningKey = hasText(properties.previousSigningKey());
        if (hasPreviousKeyId != hasPreviousSigningKey) {
            throw new IllegalStateException("미리보기 직전 키 ID와 서명 키는 함께 설정해야 합니다.");
        }
        if (hasPreviousKeyId) {
            String previousKeyId = requireKeyId(properties.previousKeyId(), "직전");
            requireSigningKey(properties.previousSigningKey());
            if (activeKeyId.equals(previousKeyId)) {
                throw new IllegalStateException("미리보기 활성 키 ID와 직전 키 ID는 서로 달라야 합니다.");
            }
        }
        if (!PREVIEW_TTL.equals(properties.ttl())) {
            throw new IllegalStateException(
                    "app.approval.it-budget.preview.ttl은 정확히 PT30M이어야 합니다.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private byte[] safeBytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    private ItBudgetApprovalException invalid() {
        return new ItBudgetApprovalException(
                HttpStatus.BAD_REQUEST, INVALID_CODE, INVALID_MESSAGE, List.of());
    }

    private ItBudgetApprovalException expired() {
        return new ItBudgetApprovalException(
                HttpStatus.CONFLICT, EXPIRED_CODE, EXPIRED_MESSAGE, List.of());
    }
}
