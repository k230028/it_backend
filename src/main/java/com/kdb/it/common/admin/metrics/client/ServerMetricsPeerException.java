package com.kdb.it.common.admin.metrics.client;

/** 피어 인스턴스 자원 사용량 조회 실패. 메시지는 응답의 {@code peerError}로 관리자 화면에 그대로 노출된다. */
public class ServerMetricsPeerException extends RuntimeException {

    public ServerMetricsPeerException(String message, Throwable cause) {
        super(message, cause);
    }
}
