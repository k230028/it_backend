package com.kdb.it.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 서버 비밀키와 용도 구분자를 사용해 인증 토큰의 결정적 HMAC 지문을 생성합니다. */
@Component
public class TokenFingerprint {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String REFRESH_TOKEN_DOMAIN = "refresh-token:";
    private static final String MFA_DOMAIN = "mfa:";

    /** JWT 서명키와 분리된 토큰 지문 전용 HMAC 키입니다. */
    private final SecretKeySpec key;

    /**
     * 토큰 지문 생성 전용 비밀키를 주입합니다.
     *
     * <p>{@code security.token-fingerprint-secret}는 JWT 서명키와 분리해 회전·권한 범위를 독립적으로 관리합니다.
     */
    public TokenFingerprint(@Value("${security.token-fingerprint-secret}") String secret) {
        if (secret == null
                || secret.isBlank()
                || (secret.startsWith("${") && secret.endsWith("}"))) {
            throw new IllegalStateException("필수 프로퍼티 미설정: security.token-fingerprint-secret");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "security.token-fingerprint-secret은 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String forRefreshToken(String token) {
        return fingerprint(REFRESH_TOKEN_DOMAIN, token);
    }

    public String forMfa(String value) {
        return fingerprint(MFA_DOMAIN, value);
    }

    private String fingerprint(String domain, String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of()
                    .formatHex(mac.doFinal((domain + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
