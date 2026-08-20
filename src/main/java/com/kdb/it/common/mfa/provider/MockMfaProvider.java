package com.kdb.it.common.mfa.provider;

/** local-ext 전용 공급자로, 대화상자를 연 것만으로는 성공하지 않고 verify 요청에서만 성공한다. */
public final class MockMfaProvider implements MfaProvider {

    @Override
    public MfaChallengeData start(MfaStartContext context) {
        // 모의 공급자는 실제 에이전트를 호출하지 않지만 화면 흐름이 같아야 하므로 형식만 맞춘 랜덤키를 준다.
        return new MfaChallengeData(
                context.transactionId(), null, "000000", context.expiresAt(), null);
    }

    @Override
    public MfaVerificationResult verify(MfaVerifyContext context) {
        return MfaVerificationResult.success();
    }
}
