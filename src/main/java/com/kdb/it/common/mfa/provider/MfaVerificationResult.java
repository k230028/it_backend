package com.kdb.it.common.mfa.provider;

/**
 * 외부 공급자 검증 결과의 안전한 표현이다.
 *
 * <p>FIDO처럼 사용자가 별도 기기에서 승인하는 수단은 결과 조회를 반복해야 하므로, 아직 결과가 정해지지 않은 {@link Outcome#UNDECIDED}를 인증 거부인
 * {@link Outcome#FAILED}와 구분한다. 이 구분이 없으면 정상적인 폴링이 거래의 허용 실패 횟수를 소진해 사용자가 승인하기 전에 거래가 잠긴다.
 */
public record MfaVerificationResult(Outcome outcome) {

    /** 외부 공급자 검증 결과 종류다. */
    public enum Outcome {
        /** 인증이 확정적으로 성공했다. */
        VERIFIED,
        /** 아직 결과가 정해지지 않아 만료 전까지 다시 조회해야 한다. */
        UNDECIDED,
        /** 인증이 거부되었거나 응답을 신뢰할 수 없다. */
        FAILED
    }

    /** 결과 종류가 비어 있는 결과를 만들지 않는다. */
    public MfaVerificationResult {
        if (outcome == null) {
            throw new IllegalArgumentException("검증 결과 종류는 필수입니다.");
        }
    }

    /** 검증 성공 결과를 반환한다. */
    public static MfaVerificationResult success() {
        return new MfaVerificationResult(Outcome.VERIFIED);
    }

    /** 아직 결과가 확정되지 않아 재조회가 필요한 결과를 반환한다. */
    public static MfaVerificationResult undecided() {
        return new MfaVerificationResult(Outcome.UNDECIDED);
    }

    /** 검증 실패 또는 신뢰할 수 없는 응답 결과를 반환한다. */
    public static MfaVerificationResult failure() {
        return new MfaVerificationResult(Outcome.FAILED);
    }

    /**
     * 인증이 확정적으로 성공했는지 반환한다.
     *
     * @return {@link Outcome#VERIFIED}이면 true
     */
    public boolean verified() {
        return outcome == Outcome.VERIFIED;
    }

    /**
     * 결과가 확정되었는지 반환한다. 확정되지 않은 결과는 실패 횟수에 집계하지 않는다.
     *
     * @return {@link Outcome#UNDECIDED}가 아니면 true
     */
    public boolean decided() {
        return outcome != Outcome.UNDECIDED;
    }
}
