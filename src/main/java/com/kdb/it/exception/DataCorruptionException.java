package com.kdb.it.exception;

/** DB 데이터가 애플리케이션 계약을 만족하지 않아 안전하게 처리할 수 없을 때 발생하는 예외. */
public class DataCorruptionException extends RuntimeException {

    public DataCorruptionException(String message) {
        super(message);
    }

    public DataCorruptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
