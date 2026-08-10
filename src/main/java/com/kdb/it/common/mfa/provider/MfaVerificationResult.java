package com.kdb.it.common.mfa.provider;

/** 외부 공급자 검증 결과의 안전한 성공 여부 표현이다. */
public record MfaVerificationResult(boolean verified) {

    /** 검증 성공 결과를 반환한다. */
    public static MfaVerificationResult success() {
        return new MfaVerificationResult(true);
    }

    /** 검증 실패 또는 공급자 미응답 결과를 반환한다. */
    public static MfaVerificationResult failure() {
        return new MfaVerificationResult(false);
    }
}
