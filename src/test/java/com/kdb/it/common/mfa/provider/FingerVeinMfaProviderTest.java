package com.kdb.it.common.mfa.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.mfa.domain.MfaPurpose;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지정맥 결과 해시 검증 계약을 고정한다.
 *
 * <p>{@code mfa.md}의 「지정맥인증 연계 보안방안」에 따라 {@code 년월일 + 사번 + 랜덤키 + 검증값 + 고정키}를 SHA-256으로 3회 해시(직전 hex
 * 문자열을 다시 입력)한 값이 인증 결과다.
 *
 * <p>랜덤키는 서버가 매번 새로 만들므로 기대값을 통째로 하드코딩할 수 없다. 대신 이 테스트가 규격만 보고 작성한 {@link #expectedHash}를 두고, 알고리즘
 * 자체는 별도로 계산한 고정 벡터로 검증한다. 즉 고정 벡터가 테스트 헬퍼를 고정하고, 테스트 헬퍼가 운영 구현을 고정한다.
 */
@DisplayName("지정맥 해시 검증")
class FingerVeinMfaProviderTest {

    private static final String FIXED_KEY = "vusgktlsrjffh@";
    private static final String ENO = "K140024";

    /** 2026-08-11 09:00 KST — 서버 날짜가 20260811이 되는 시각이다. */
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("테스트 헬퍼가 규격의 3회 해시와 같은 값을 만든다")
    void helper_matchesIndependentlyComputedVector() {
        // 20260811K140024123456SUCCvusgktlsrjffh@ 를 SHA-256으로 3회 해시한 값
        assertThat(expectedHash("20260811", ENO, "123456", "SUCC"))
                .isEqualTo("33f223eebf0129b9978ba97f3471dd5ccfb3d11d407cf9a78ec8a438ef18f699");
        assertThat(expectedHash("20260811", ENO, "123456", "FAIL"))
                .isEqualTo("dd4a24c32be3c9d35bba33476e97292ba18512c29566b7d31307a52b41170354");
        assertThat(expectedHash("20260811", "O1234567", "987654", "SUCC"))
                .isEqualTo("91214b57ba3d420413fd2241ab50ecb6c02671b9c62e3ca175f790a042df1973");
    }

    @Test
    @DisplayName("start는 서버가 만든 6자리 랜덤키를 challenge에 실어 준다")
    void start_issuesSixDigitRandomKey() {
        MfaChallengeData challenge = provider().start(context("tx-1"));

        assertThat(challenge.randomKey()).matches("\\d{6}");
        assertThat(challenge.challengeId()).isEqualTo("tx-1");
    }

    @Test
    @DisplayName("거래마다 다른 랜덤키를 발급한다")
    void start_issuesDistinctRandomKeysPerTransaction() {
        FingerVeinMfaProvider provider = provider();

        String first = provider.start(context("tx-a")).randomKey();
        String second = provider.start(context("tx-b")).randomKey();

        // 6자리 난수라 드물게 같을 수 있으므로 여러 번 확인한다.
        boolean anyDifferent = !first.equals(second);
        for (int index = 0; index < 20 && !anyDifferent; index++) {
            anyDifferent = !provider.start(context("tx-" + index)).randomKey().equals(first);
        }
        assertThat(anyDifferent).isTrue();
    }

    @Test
    @DisplayName("서버가 만든 랜덤키로 계산한 성공 해시만 통과시킨다")
    void verify_successHashOfIssuedRandomKey_isVerified() {
        FingerVeinMfaProvider provider = provider();
        MfaChallengeData challenge = provider.start(context("tx-hash"));
        String hash = expectedHash("20260811", ENO, challenge.randomKey(), "SUCC");

        MfaVerificationResult result =
                provider.verify(
                        new MfaVerifyContext(
                                context("tx-hash"), challenge.challengeId(), hash, null));

        assertThat(result.verified()).isTrue();
    }

    @Test
    @DisplayName("지정맥 인증이 실패한 해시는 통과시키지 않는다")
    void verify_failureHash_isRejected() {
        FingerVeinMfaProvider provider = provider();
        MfaChallengeData challenge = provider.start(context("tx-hash"));
        String hash = expectedHash("20260811", ENO, challenge.randomKey(), "FAIL");

        MfaVerificationResult result =
                provider.verify(
                        new MfaVerifyContext(
                                context("tx-hash"), challenge.challengeId(), hash, null));

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    @DisplayName("결과 코드 문자열만으로는 통과시키지 않는다")
    void verify_plainResultCode_isRejected() {
        FingerVeinMfaProvider provider = provider();
        MfaChallengeData challenge = provider.start(context("tx-hash"));

        MfaVerificationResult result =
                provider.verify(
                        new MfaVerifyContext(
                                context("tx-hash"), challenge.challengeId(), "FE00", null));

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    @DisplayName("다른 거래의 랜덤키로 만든 해시는 통과시키지 않는다")
    void verify_hashOfAnotherTransaction_isRejected() {
        FingerVeinMfaProvider provider = provider();
        MfaChallengeData target = provider.start(context("tx-target"));
        MfaChallengeData other = provider.start(context("tx-other"));
        String foreignHash = expectedHash("20260811", ENO, other.randomKey(), "SUCC");

        MfaVerificationResult result =
                provider.verify(
                        new MfaVerifyContext(
                                context("tx-target"), target.challengeId(), foreignHash, null));

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    @DisplayName("시작하지 않은 거래의 해시는 통과시키지 않는다")
    void verify_withoutStart_isRejected() {
        MfaVerificationResult result =
                provider()
                        .verify(
                                new MfaVerifyContext(
                                        context("tx-hash"),
                                        "tx-hash",
                                        expectedHash("20260811", ENO, "123456", "SUCC"),
                                        null));

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    @DisplayName("같은 해시를 다시 제출해도 통과시키지 않는다")
    void verify_replayedHash_isRejected() {
        FingerVeinMfaProvider provider = provider();
        MfaChallengeData challenge = provider.start(context("tx-hash"));
        String hash = expectedHash("20260811", ENO, challenge.randomKey(), "SUCC");
        provider.verify(
                new MfaVerifyContext(context("tx-hash"), challenge.challengeId(), hash, null));

        MfaVerificationResult replayed =
                provider.verify(
                        new MfaVerifyContext(
                                context("tx-hash"), challenge.challengeId(), hash, null));

        assertThat(replayed.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    @DisplayName("만료된 거래의 해시는 통과시키지 않는다")
    void verify_expiredTransaction_isRejected() {
        FingerVeinMfaProvider provider = provider();
        MfaStartContext expired =
                new MfaStartContext("tx-expired", ENO, MfaPurpose.LOGIN, Instant.EPOCH);
        MfaChallengeData challenge = provider.start(expired);
        String hash = expectedHash("20260811", ENO, challenge.randomKey(), "SUCC");

        MfaVerificationResult result =
                provider.verify(
                        new MfaVerifyContext(expired, challenge.challengeId(), hash, null));

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    @DisplayName("용량을 넘기면 오래된 거래를 밀어내고 그 해시를 거부한다")
    void verify_evictedTransaction_isRejected() {
        FingerVeinMfaProvider provider = new FingerVeinMfaProvider(FIXED_KEY, CLOCK, 1);
        MfaChallengeData first = provider.start(context("tx-first"));
        String hash = expectedHash("20260811", ENO, first.randomKey(), "SUCC");
        provider.start(context("tx-second"));

        MfaVerificationResult result =
                provider.verify(
                        new MfaVerifyContext(context("tx-first"), "tx-first", hash, null));

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    @DisplayName("대기 거래 상한은 1 이상이어야 한다")
    void constructor_rejectsCapacityBelowOne() {
        assertThatThrownBy(() -> new FingerVeinMfaProvider(FIXED_KEY, CLOCK, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("고정키가 없으면 공급자를 만들 수 없다")
    void constructor_requiresFixedKey() {
        assertThatThrownBy(() -> new FingerVeinMfaProvider("  ", CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FingerVeinMfaProvider(null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private FingerVeinMfaProvider provider() {
        return new FingerVeinMfaProvider(FIXED_KEY, CLOCK);
    }

    private MfaStartContext context(String transactionId) {
        return new MfaStartContext(
                transactionId, ENO, MfaPurpose.LOGIN, Instant.parse("2099-01-01T00:00:00Z"));
    }

    /** 규격만 보고 작성한 기대 해시 계산기다. 운영 구현과 독립적으로 유지한다. */
    private static String expectedHash(String day, String eno, String randomKey, String verdict) {
        String value = day + eno + randomKey + verdict + FIXED_KEY;
        return sha256(sha256(sha256(value)));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
