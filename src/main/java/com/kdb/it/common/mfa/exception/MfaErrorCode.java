package com.kdb.it.common.mfa.exception;

import org.springframework.http.HttpStatus;

/** 클라이언트가 재인증 흐름을 결정하는 MFA 표준 오류 코드다. */
public enum MfaErrorCode {
    MFA_REQUIRED(HttpStatus.UNAUTHORIZED, "유효한 MFA 증표가 필요합니다."),
    MFA_EXPIRED(HttpStatus.GONE, "MFA 거래가 만료되었습니다."),
    MFA_FAILED(HttpStatus.UNAUTHORIZED, "MFA 인증에 실패했습니다."),
    MFA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MFA 서비스를 사용할 수 없습니다."),
    MFA_LOCKED(HttpStatus.LOCKED, "MFA 거래의 허용 실패 횟수를 초과했습니다.");

    private final HttpStatus status;
    private final String message;

    MfaErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
