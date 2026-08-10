package com.kdb.it.common.mfa.exception;

/** MFA 표준 오류 코드만 외부로 노출하는 예외다. */
public class MfaException extends RuntimeException {

    private final MfaErrorCode errorCode;

    public MfaException(MfaErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public MfaErrorCode errorCode() {
        return errorCode;
    }
}
