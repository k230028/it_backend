package com.kdb.it.common.admin.waslog.client;

/** 피어 인스턴스 위임 호출 실패. 호출부는 예외를 삼키지 말고 응답의 peerError로 표면화한다. */
public class WasLogPeerException extends RuntimeException {

    public WasLogPeerException(String message, Throwable cause) {
        super(message, cause);
    }
}
