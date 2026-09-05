package com.kdb.it.common.approval.itbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.config.ItBudgetPreviewProperties;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.common.approval.itbudget.service.ItBudgetPreviewTokenService.Claims;
import com.kdb.it.common.approval.itbudget.service.ItBudgetPreviewTokenService.IssuedPreview;
import com.kdb.it.common.approval.itbudget.service.ItBudgetPreviewTokenService.PreviewBinding;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 전산예산 미리보기 토큰의 요청 결속과 서명 경계를 검증한다. */
class ItBudgetPreviewTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T05:30:00Z");

    private MutableClock clock;
    private ItBudgetPreviewTokenService service;
    private PreviewBinding binding;
    private Claims claims;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service =
                service(
                        new ItBudgetPreviewProperties(
                                "active-v2",
                                key('a'),
                                "previous-v1",
                                key('b'),
                                Duration.ofMinutes(30)));
        binding = binding();
        claims = claims(NOW, NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("발급한 토큰은 요청자와 모든 digest 및 유효시각을 그대로 검증한다")
    void issueAndVerify_bindsAllClaims() {
        IssuedPreview issued = service.issue(binding);

        assertThat(issued.token().split("\\.", -1))
                .hasSize(3)
                .allMatch(segment -> !segment.isBlank());
        assertThat(issued.claims()).isEqualTo(claims);
        assertThat(service.verify(issued.token(), "E10001")).isEqualTo(claims);
    }

    @Test
    @DisplayName("서명 뒤에 문자를 덧붙인 토큰은 올바른 신청자여도 거부한다")
    void verify_alteredToken_rejectsAsInvalid() {
        String token = service.issue(binding).token();

        assertInvalid(() -> service.verify(token + "x", "E10001"));
    }

    @Test
    @DisplayName("만료 시각과 같거나 지난 서명 정상 토큰은 만료로 구분해 거부한다")
    void verify_expiredToken_rejectsAsExpired() {
        String token = service.issue(binding).token();
        clock.advance(Duration.ofMinutes(30));

        assertThatThrownBy(() -> service.verify(token, "E10001"))
                .isInstanceOf(ItBudgetApprovalException.class)
                .extracting("code")
                .isEqualTo("IT_BUDGET_PREVIEW_EXPIRED");
    }

    @Test
    @DisplayName("발급 시각과 만료 시각은 호출자 입력이 아닌 주입 Clock의 정확히 30분으로 정한다")
    void issue_usesInjectedClockForExactThirtyMinuteClaims() {
        IssuedPreview issued = service.issue(binding);

        assertThat(issued.claims())
                .isEqualTo(
                        new Claims(
                                "E10001",
                                hex('a'),
                                hex('b'),
                                hex('c'),
                                hex('d'),
                                NOW,
                                NOW.plusSeconds(1800)));
    }

    @Test
    @DisplayName("발급 직후 Clock 정밀도가 이동해도 자기 토큰은 유효하다")
    void verify_immediatelyAfterIssue_acceptsTokenAcrossClockPrecision() {
        IssuedPreview issued = service.issue(binding);
        clock.advance(Duration.ofNanos(1));

        assertThat(service.verify(issued.token(), "E10001")).isEqualTo(issued.claims());
    }

    @Test
    @DisplayName("서명은 정상이어도 30분이 아닌 claims TTL은 검증 단계에서 거부한다")
    void verify_nonThirtyMinuteTtl_rejectsAsInvalid() {
        Claims thirtyOneMinuteClaims = claims(NOW, NOW.plus(Duration.ofMinutes(31)));
        String token = signedToken("active-v2", key('a'), thirtyOneMinuteClaims);

        assertInvalid(() -> service.verify(token, "E10001"));
    }

    @Test
    @DisplayName("서명은 정상이어도 미래 발급 시각 토큰은 INVALID로 거부한다")
    void verify_futureIssuedAt_rejectsAsInvalid() {
        Instant futureIssuedAt = NOW.plus(Duration.ofMinutes(1));
        String token =
                signedToken(
                        "active-v2",
                        key('a'),
                        claims(futureIssuedAt, futureIssuedAt.plus(Duration.ofMinutes(30))));

        assertInvalid(() -> service.verify(token, "E10001"));
    }

    @Test
    @DisplayName("Instant 최대값 경계의 위조 시간 claims는 DateTimeException 대신 INVALID로 닫힌다")
    void verify_extremeTemporalClaims_rejectsAsInvalid() {
        Instant issuedAt = Instant.MAX.minusSeconds(1);
        String token = signedToken("active-v2", key('a'), claims(issuedAt, Instant.MAX));

        assertInvalid(() -> service.verify(token, "E10001"));
    }

    @Test
    @DisplayName("수동 구성도 정확히 30분이 아닌 토큰 TTL을 허용하지 않는다")
    void constructor_nonThirtyMinuteConfiguredTtl_failsFast() {
        assertThatThrownBy(
                        () ->
                                service(
                                        new ItBudgetPreviewProperties(
                                                "active-v2",
                                                key('a'),
                                                null,
                                                null,
                                                Duration.ofMinutes(31))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ttl");
    }

    @Test
    @DisplayName("수동 구성도 32바이트보다 짧은 활성 서명 키를 허용하지 않는다")
    void constructor_shortActiveSigningKey_failsFast() {
        assertThatThrownBy(
                        () ->
                                service(
                                        new ItBudgetPreviewProperties(
                                                "active-v2",
                                                "short-signing-key",
                                                null,
                                                null,
                                                Duration.ofMinutes(30))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    @Test
    @DisplayName("다른 신청자가 제시한 정상 서명 토큰은 요청 결속 오류로 거부한다")
    void verify_differentRequester_rejectsAsInvalid() {
        String token = service.issue(binding).token();

        assertInvalid(() -> service.verify(token, "E10002"));
    }

    @Test
    @DisplayName("신청자 사번이 없는 검증 요청은 정상 토큰에도 결속 오류로 거부한다")
    void verify_missingRequester_rejectsAsInvalid() {
        String token = service.issue(binding).token();

        assertInvalid(() -> service.verify(token, null));
    }

    @Test
    @DisplayName("직전 키로 발급한 토큰은 키 회전 중에도 검증한다")
    void verify_previousKeyToken_acceptsDuringRotation() {
        ItBudgetPreviewTokenService previousSigner =
                service(
                        new ItBudgetPreviewProperties(
                                "previous-v1", key('b'), null, null, Duration.ofMinutes(30)));
        String token = previousSigner.issue(binding).token();

        assertThat(service.verify(token, "E10001")).isEqualTo(claims);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "active-v2..signature",
                "active-v2.%%%%.signature",
                "active-v2.eyJ9.%%%%",
                "active-v2.eyJ9.signature.extra"
            })
    @DisplayName("잘못된 토큰 grammar와 Base64URL 값은 예외 세부정보 없이 거부한다")
    void verify_malformedToken_rejectsAsInvalid(String token) {
        assertInvalid(() -> service.verify(token, "E10001"));
    }

    @Test
    @DisplayName("알 수 없는 kid의 토큰은 서명 키를 추측하지 않고 거부한다")
    void verify_unknownKid_rejectsAsInvalid() {
        String token = service.issue(binding).token().replaceFirst("^[^.]+", "unknown-v9");

        assertInvalid(() -> service.verify(token, "E10001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"active.v2", "active/v2", "active v2"})
    @DisplayName("수동 구성에서도 토큰 grammar 밖의 활성 키 ID는 즉시 거부한다")
    void constructor_malformedActiveKeyId_failsFast(String activeKeyId) {
        assertThatThrownBy(
                        () ->
                                service(
                                        new ItBudgetPreviewProperties(
                                                activeKeyId,
                                                key('a'),
                                                null,
                                                null,
                                                Duration.ofMinutes(30))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("키 ID");
    }

    @Test
    @DisplayName("수동 구성에서도 활성·직전 키 ID가 같으면 회전을 시작하지 않는다")
    void constructor_duplicateActiveAndPreviousKeyId_failsFast() {
        assertThatThrownBy(
                        () ->
                                service(
                                        new ItBudgetPreviewProperties(
                                                "active-v2",
                                                key('a'),
                                                "active-v2",
                                                key('b'),
                                                Duration.ofMinutes(30))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("달라야");
    }

    @Test
    @DisplayName("직전 키의 ID만 남은 불완전한 수동 회전 설정은 생성 시 거부한다")
    void constructor_incompletePreviousKeyPair_failsFast() {
        assertThatThrownBy(
                        () ->
                                service(
                                        new ItBudgetPreviewProperties(
                                                "active-v2",
                                                key('a'),
                                                "previous-v1",
                                                null,
                                                Duration.ofMinutes(30))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("함께 설정");
    }

    @Test
    @DisplayName("문법상 Base64URL이지만 디코딩할 수 없는 서명은 거부한다")
    void verify_nonDecodableBase64UrlSignature_rejectsAsInvalid() {
        assertInvalid(() -> service.verify("active-v2.A.A", "E10001"));
    }

    @Test
    @DisplayName("HMAC-SHA-256 길이가 아닌 canonical 서명은 거부한다")
    void verify_shortDecodedSignature_rejectsAsInvalid() {
        String claimsSegment = service.issue(binding).token().split("\\.", -1)[1];

        assertInvalid(() -> service.verify("active-v2." + claimsSegment + ".AA", "E10001"));
    }

    @Test
    @DisplayName("동일 바이트로 디코딩되는 마지막 서명 글자 alias도 canonical token이 아니므로 거부한다")
    void verify_signatureFinalCharacterAlias_rejectsAsInvalid() {
        String token = service.issue(binding).token();
        String[] parts = token.split("\\.", -1);
        String aliasedSignature = aliasLastBase64UrlCharacter(parts[2]);

        assertThat(Base64.getUrlDecoder().decode(aliasedSignature))
                .isEqualTo(Base64.getUrlDecoder().decode(parts[2]));
        assertInvalid(
                () -> service.verify(parts[0] + "." + parts[1] + "." + aliasedSignature, "E10001"));
    }

    @Test
    @DisplayName("claims segment를 같은 바이트 alias로 서명해도 canonical encoding이 아니면 거부한다")
    void verify_claimsFinalCharacterAlias_rejectsAsInvalid() {
        PreviewBinding aliasBinding =
                new PreviewBinding(
                        claims.requesterEno(),
                        claims.requestDigest() + "x",
                        claims.sourceSetDigest(),
                        claims.payloadSetDigest(),
                        claims.previewDigest());
        String canonicalToken = service.issue(aliasBinding).token();
        String[] parts = canonicalToken.split("\\.", -1);
        String aliasedClaims = aliasLastBase64UrlCharacter(parts[1]);
        String token = signedToken("active-v2", key('a'), aliasedClaims);

        assertThat(Base64.getUrlDecoder().decode(aliasedClaims))
                .isEqualTo(Base64.getUrlDecoder().decode(parts[1]));
        assertInvalid(() -> service.verify(token, "E10001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"request", "source", "payload", "preview"})
    @DisplayName("필수 digest가 하나라도 빠진 claims는 발급하지 않는다")
    void issue_missingDigest_rejectsInvalidClaims(String missingDigest) {
        PreviewBinding invalidBinding =
                switch (missingDigest) {
                    case "request" ->
                            new PreviewBinding(
                                    binding.requesterEno(),
                                    "",
                                    binding.sourceSetDigest(),
                                    binding.payloadSetDigest(),
                                    binding.previewDigest());
                    case "source" ->
                            new PreviewBinding(
                                    binding.requesterEno(),
                                    binding.requestDigest(),
                                    "",
                                    binding.payloadSetDigest(),
                                    binding.previewDigest());
                    case "payload" ->
                            new PreviewBinding(
                                    binding.requesterEno(),
                                    binding.requestDigest(),
                                    binding.sourceSetDigest(),
                                    "",
                                    binding.previewDigest());
                    case "preview" ->
                            new PreviewBinding(
                                    binding.requesterEno(),
                                    binding.requestDigest(),
                                    binding.sourceSetDigest(),
                                    binding.payloadSetDigest(),
                                    "");
                    default -> throw new IllegalArgumentException("알 수 없는 digest fixture");
                };

        assertThatThrownBy(() -> service.issue(invalidBinding))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("binding");
    }

    private ItBudgetPreviewTokenService service(ItBudgetPreviewProperties properties) {
        return new ItBudgetPreviewTokenService(
                properties, new ObjectMapper().findAndRegisterModules(), clock);
    }

    private Claims claims(Instant issuedAt, Instant expiresAt) {
        return new Claims("E10001", hex('a'), hex('b'), hex('c'), hex('d'), issuedAt, expiresAt);
    }

    private PreviewBinding binding() {
        return new PreviewBinding("E10001", hex('a'), hex('b'), hex('c'), hex('d'));
    }

    private String signedToken(String keyId, String signingKey, Claims tokenClaims) {
        try {
            String encodedClaims =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    new ObjectMapper()
                                            .findAndRegisterModules()
                                            .writeValueAsBytes(tokenClaims));
            return signedToken(keyId, signingKey, encodedClaims);
        } catch (Exception exception) {
            throw new AssertionError("테스트 토큰 서명에 실패했습니다.", exception);
        }
    }

    private String signedToken(String keyId, String signingKey, String encodedClaims) {
        try {
            String signingInput = keyId + "." + encodedClaims;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            "HmacSHA256"));
            return signingInput
                    + "."
                    + Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    mac.doFinal(
                                            signingInput.getBytes(
                                                    java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new AssertionError("테스트 토큰 서명에 실패했습니다.", exception);
        }
    }

    private String aliasLastBase64UrlCharacter(String canonicalSegment) {
        byte[] decoded = Base64.getUrlDecoder().decode(canonicalSegment);
        if (decoded.length % 3 == 0) {
            throw new AssertionError("alias regression fixture must use an unpadded final quantum");
        }
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int lastIndex = alphabet.indexOf(canonicalSegment.charAt(canonicalSegment.length() - 1));
        return canonicalSegment.substring(0, canonicalSegment.length() - 1)
                + alphabet.charAt(lastIndex + 1);
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(ItBudgetApprovalException.class)
                .extracting("code")
                .isEqualTo("IT_BUDGET_PREVIEW_INVALID");
    }

    private static String hex(char character) {
        return Stream.generate(() -> String.valueOf(character))
                .limit(64)
                .collect(Collectors.joining());
    }

    private static String key(char character) {
        return Stream.generate(() -> String.valueOf(character))
                .limit(40)
                .collect(Collectors.joining());
    }

    /** 테스트에서만 현재 시각을 이동해 만료 경계를 재현한다. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
