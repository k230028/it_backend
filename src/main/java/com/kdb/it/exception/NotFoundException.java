package com.kdb.it.exception;

/**
 * 리소스 미존재 예외 (404 Not Found).
 *
 * <p>조회 대상 리소스(신청서·문서·파일 등)가 존재하지 않을 때 던집니다. {@code GlobalExceptionHandler}가 404로 매핑합니다. 잘못된
 * 입력값(400)과 구분하기 위해 {@link IllegalArgumentException} 대신 본 예외를 사용합니다.
 */
public class NotFoundException extends RuntimeException {

    /**
     * @param message 사용자/클라이언트에 노출 가능한 미존재 사유
     */
    public NotFoundException(String message) {
        super(message);
    }

    /**
     * 원인 예외를 함께 보존하는 생성자.
     *
     * <p>하위 계층에서 잡은 예외를 감싸 던질 때 cause 체인을 유지해 진단성을 확보합니다.
     *
     * @param message 사용자/클라이언트에 노출 가능한 미존재 사유
     * @param cause 미존재 판정의 원인이 된 예외(진단용 cause 체인 보존)
     */
    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
