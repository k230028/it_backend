package com.kdb.it.common.mfa.provider;

/** OnePass mOTP 인증 공급자이다. */
public final class MotpMfaProvider implements MfaProvider {

    private final OnePassClient onePassClient;

    public MotpMfaProvider(OnePassClient onePassClient) {
        this.onePassClient = onePassClient;
    }

    @Override
    public MfaChallengeData start(MfaStartContext context) {
        return onePassClient.requestMotpChallenge(context);
    }

    @Override
    public MfaVerificationResult verify(MfaVerifyContext context) {
        return onePassClient.verifyMotp(
                context.startContext(), context.challengeId(), context.verificationValue());
    }
}
