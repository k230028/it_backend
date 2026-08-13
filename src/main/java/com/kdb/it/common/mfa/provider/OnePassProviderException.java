package com.kdb.it.common.mfa.provider;

/** OnePass가 정상 통신 뒤 반환한 업무 오류 코드와 메시지를 보존한다. */
public final class OnePassProviderException extends RuntimeException {

    private final String providerCode;
    private final String providerMessage;

    public OnePassProviderException(String providerCode, String providerMessage) {
        super("OnePass MFA 요청이 거부되었습니다.");
        this.providerCode = providerCode;
        this.providerMessage = providerMessage;
    }

    public String providerCode() {
        return providerCode;
    }

    public String providerMessage() {
        return providerMessage;
    }
}
