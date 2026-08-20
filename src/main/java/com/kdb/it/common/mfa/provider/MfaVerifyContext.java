package com.kdb.it.common.mfa.provider;

import java.util.Objects;

/**
 * MFA challenge의 명시적 검증 요청 정보이다.
 *
 * @param providerTransactionId OnePass 서비스 거래 식별자(svcTrId). FIDO만 값이 있고 다른 수단은 null이다.
 */
public record MfaVerifyContext(
        MfaStartContext startContext,
        String challengeId,
        String verificationValue,
        String providerTransactionId) {

    public MfaVerifyContext {
        Objects.requireNonNull(startContext, "MFA 시작 거래는 필수입니다.");
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("challenge 식별자는 필수입니다.");
        }
        verificationValue = verificationValue == null ? "" : verificationValue;
    }
}
