package com.kdb.it.common.approval.itbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.config.ItBudgetPreviewProperties;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.common.approval.itbudget.service.ItBudgetPreviewTokenService.Claims;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        claims = claims(NOW, NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("발급한 토큰은 요청자와 모든 digest 및 유효시각을 그대로 검증한다")
    void issueAndVerify_bindsAllClaims() {
        String token = service.issue(claims);

        assertThat(token.split("\\.", -1)).hasSize(3).allMatch(segment -> !segment.isBlank());
        assertThat(service.verify(token, "E10001")).isEqualTo(claims);
    }

    @Test
    @DisplayName("서명 뒤에 문자를 덧붙인 토큰은 올바른 신청자여도 거부한다")
    void verify_alteredToken_rejectsAsInvalid() {
        String token = service.issue(claims);

        assertInvalid(() -> service.verify(token + "x", "E10001"));
    }

    @Test
    @DisplayName("만료 시각과 같거나 지난 서명 정상 토큰은 만료로 구분해 거부한다")
    void verify_expiredToken_rejectsAsExpired() {
        String token = service.issue(claims);
        clock.advance(Duration.ofMinutes(30));

        assertThatThrownBy(() -> service.verify(token, "E10001"))
                .isInstanceOf(ItBudgetApprovalException.class)
                .extracting("code")
                .isEqualTo("IT_BUDGET_PREVIEW_EXPIRED");
    }

    @Test
    @DisplayName("다른 신청자가 제시한 정상 서명 토큰은 요청 결속 오류로 거부한다")
    void verify_differentRequester_rejectsAsInvalid() {
        String token = service.issue(claims);

        assertInvalid(() -> service.verify(token, "E10002"));
    }

    @Test
    @DisplayName("신청자 사번이 없는 검증 요청은 정상 토큰에도 결속 오류로 거부한다")
    void verify_missingRequester_rejectsAsInvalid() {
        String token = service.issue(claims);

        assertInvalid(() -> service.verify(token, null));
    }

    @Test
    @DisplayName("직전 키로 발급한 토큰은 키 회전 중에도 검증한다")
    void verify_previousKeyToken_acceptsDuringRotation() {
        ItBudgetPreviewTokenService previousSigner =
                service(
                        new ItBudgetPreviewProperties(
                                "previous-v1", key('b'), null, null, Duration.ofMinutes(30)));
        String token = previousSigner.issue(claims);

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
        String token = service.issue(claims).replaceFirst("^[^.]+", "unknown-v9");

        assertInvalid(() -> service.verify(token, "E10001"));
    }

    @Test
    @DisplayName("직전 키의 ID만 남은 불완전한 회전 설정은 키를 추측하지 않고 거부한다")
    void verify_incompletePreviousKeyPair_rejectsAsInvalid() {
        ItBudgetPreviewTokenService incompleteRotation =
                service(
                        new ItBudgetPreviewProperties(
                                "active-v2",
                                key('a'),
                                "previous-v1",
                                null,
                                Duration.ofMinutes(30)));

        assertInvalid(() -> incompleteRotation.verify("previous-v1.A.A", "E10001"));
    }

    @Test
    @DisplayName("문법상 Base64URL이지만 디코딩할 수 없는 서명은 거부한다")
    void verify_nonDecodableBase64UrlSignature_rejectsAsInvalid() {
        assertInvalid(() -> service.verify("active-v2.A.A", "E10001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"request", "source", "payload", "preview"})
    @DisplayName("필수 digest가 하나라도 빠진 claims는 발급하지 않는다")
    void issue_missingDigest_rejectsInvalidClaims(String missingDigest) {
        Claims invalidClaims =
                switch (missingDigest) {
                    case "request" ->
                            new Claims(
                                    claims.requesterEno(),
                                    "",
                                    claims.sourceSetDigest(),
                                    claims.payloadSetDigest(),
                                    claims.previewDigest(),
                                    claims.issuedAt(),
                                    claims.expiresAt());
                    case "source" ->
                            new Claims(
                                    claims.requesterEno(),
                                    claims.requestDigest(),
                                    "",
                                    claims.payloadSetDigest(),
                                    claims.previewDigest(),
                                    claims.issuedAt(),
                                    claims.expiresAt());
                    case "payload" ->
                            new Claims(
                                    claims.requesterEno(),
                                    claims.requestDigest(),
                                    claims.sourceSetDigest(),
                                    "",
                                    claims.previewDigest(),
                                    claims.issuedAt(),
                                    claims.expiresAt());
                    case "preview" ->
                            new Claims(
                                    claims.requesterEno(),
                                    claims.requestDigest(),
                                    claims.sourceSetDigest(),
                                    claims.payloadSetDigest(),
                                    "",
                                    claims.issuedAt(),
                                    claims.expiresAt());
                    default -> throw new IllegalArgumentException("알 수 없는 digest fixture");
                };

        assertThatThrownBy(() -> service.issue(invalidClaims))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claims");
    }

    private ItBudgetPreviewTokenService service(ItBudgetPreviewProperties properties) {
        return new ItBudgetPreviewTokenService(
                properties, new ObjectMapper().findAndRegisterModules(), clock);
    }

    private Claims claims(Instant issuedAt, Instant expiresAt) {
        return new Claims("E10001", hex('a'), hex('b'), hex('c'), hex('d'), issuedAt, expiresAt);
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
