package com.kdb.it.common.mfa.exception;

/** MFA 표준 오류와 선택적인 외부 공급자 업무 오류를 전달하는 예외다. */
public class MfaException extends RuntimeException {

    private final MfaErrorCode errorCode;
    private final String providerCode;
    private final String providerMessage;

    public MfaException(MfaErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public MfaException(MfaErrorCode errorCode, String providerCode, String providerMessage) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.providerCode = providerCode;
        this.providerMessage = providerMessage;
    }

    public MfaErrorCode errorCode() {
        return errorCode;
    }

    public String providerCode() {
        return providerCode;
    }

    public String providerMessage() {
        return providerMessage;
    }
}
