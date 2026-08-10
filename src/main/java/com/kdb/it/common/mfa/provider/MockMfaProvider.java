package com.kdb.it.common.mfa.provider;

/** local-ext 전용 공급자로, 대화상자를 연 것만으로는 성공하지 않고 verify 요청에서만 성공한다. */
public final class MockMfaProvider implements MfaProvider {

    @Override
    public MfaChallengeData start(MfaStartContext context) {
        return new MfaChallengeData(context.transactionId(), null, context.expiresAt());
    }

    @Override
    public MfaVerificationResult verify(MfaVerifyContext context) {
        return MfaVerificationResult.success();
    }
}
