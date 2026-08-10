package com.kdb.it.common.mfa.provider;

/** OnePass FIDO 인증 공급자이다. */
public final class FidoMfaProvider implements MfaProvider {

    private final OnePassClient onePassClient;

    public FidoMfaProvider(OnePassClient onePassClient) {
        this.onePassClient = onePassClient;
    }

    @Override
    public MfaChallengeData start(MfaStartContext context) {
        return onePassClient.requestFidoChallenge(context);
    }

    @Override
    public MfaVerificationResult verify(MfaVerifyContext context) {
        return onePassClient.confirmFido(context.startContext(), context.challengeId());
    }
}
