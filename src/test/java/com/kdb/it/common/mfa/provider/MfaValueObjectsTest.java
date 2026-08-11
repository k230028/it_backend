package com.kdb.it.common.mfa.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.mfa.domain.MfaMethod;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** MFA 값 객체와 공급자 레지스트리의 입력 검증 계약을 고정한다. */
class MfaValueObjectsTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-11T00:01:30Z");

    @Test
    void startContext_거래식별자가_비어_있으면_거부한다() {
        assertThatThrownBy(() -> new MfaStartContext(null, "E10001", MfaPurpose.LOGIN, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaStartContext("  ", "E10001", MfaPurpose.LOGIN, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startContext_사원번호가_비어_있으면_거부한다() {
        assertThatThrownBy(() -> new MfaStartContext("tx-1", null, MfaPurpose.LOGIN, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaStartContext("tx-1", "", MfaPurpose.LOGIN, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startContext_목적과_만료시각은_필수다() {
        assertThatThrownBy(() -> new MfaStartContext("tx-1", "E10001", null, EXPIRES_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MfaStartContext("tx-1", "E10001", MfaPurpose.LOGIN, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void verifyContext_시작거래와_challenge식별자는_필수다() {
        MfaStartContext start = startContext();

        assertThatThrownBy(() -> new MfaVerifyContext(null, "challenge-1", ""))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MfaVerifyContext(start, null, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaVerifyContext(start, " ", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyContext_검증값이_null이면_빈_문자열로_접는다() {
        MfaVerifyContext context = new MfaVerifyContext(startContext(), "challenge-1", null);

        assertThat(context.verificationValue()).isEmpty();
    }

    @Test
    void challengeData_식별자와_만료시각을_검증한다() {
        assertThatThrownBy(() -> new MfaChallengeData(null, "qr", null, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaChallengeData(" ", "qr", null, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaChallengeData("challenge-1", "qr", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void challengeData_QR이_없어도_생성된다() {
        assertThat(new MfaChallengeData("challenge-1", null, null, EXPIRES_AT).qrData()).isNull();
    }

    @Test
    void registry_기본생성자는_어떤_인증수단도_활성화하지_않는다() {
        MfaProviderRegistry registry = new MfaProviderRegistry();

        assertThatThrownBy(() -> registry.start(MfaMethod.FIDO, startContext()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registry_등록되지_않은_인증수단은_거부한다() {
        MfaProviderRegistry registry =
                new MfaProviderRegistry(Map.of(MfaMethod.MOTP, new MockMfaProvider()));

        assertThatThrownBy(() -> registry.start(MfaMethod.FINGER_VEIN, startContext()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                registry.verify(
                                        MfaMethod.FIDO,
                                        new MfaVerifyContext(startContext(), "challenge-1", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registry_인증수단이_null이면_거부한다() {
        MfaProviderRegistry registry =
                new MfaProviderRegistry(Map.of(MfaMethod.MOTP, new MockMfaProvider()));

        assertThatThrownBy(() -> registry.start(null, startContext()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registry_등록된_인증수단은_공급자에게_위임한다() {
        MfaProviderRegistry registry =
                new MfaProviderRegistry(Map.of(MfaMethod.MOTP, new MockMfaProvider()));
        MfaStartContext context = startContext();

        MfaChallengeData challenge = registry.start(MfaMethod.MOTP, context);
        MfaVerificationResult result =
                registry.verify(
                        MfaMethod.MOTP, new MfaVerifyContext(context, challenge.challengeId(), ""));

        assertThat(challenge.challengeId()).isEqualTo(context.transactionId());
        assertThat(result.verified()).isTrue();
    }

    private static MfaStartContext startContext() {
        return new MfaStartContext("tx-1", "E10001", MfaPurpose.LOGIN, EXPIRES_AT);
    }
}
