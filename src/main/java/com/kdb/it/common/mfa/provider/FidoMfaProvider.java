package com.kdb.it.common.mfa.provider;

/** OnePass FIDO 인증 공급자다. 상태는 MfaService가 영속 계층(TPRMPP_CMFATM)에 보관하고 컨텍스트로 전달한다. */
public final class FidoMfaProvider implements MfaProvider {

    private final OnePassClient onePassClient;

    public FidoMfaProvider(OnePassClient onePassClient) {
        this.onePassClient = onePassClient;
    }

    @Override
    public MfaChallengeData start(MfaStartContext context) {
        return onePassClient.startFido(context);
    }

    /**
     * 컨텍스트로 전달된 서비스 거래 식별자로 OnePass 결과를 확인한다.
     *
     * <p>challenge 식별자 일치 검증은 {@code MfaService.isExpectedProviderChallenge}가 저장된 해시로 이 메서드 호출 이전에
     * 이미 수행하므로 여기서 다시 확인하지 않는다.
     */
    @Override
    public MfaVerificationResult verify(MfaVerifyContext context) {
        String svcTrId = context.providerTransactionId();
        if (svcTrId == null) {
            return MfaVerificationResult.undecided();
        }
        return onePassClient.confirmFido(svcTrId);
    }
}
