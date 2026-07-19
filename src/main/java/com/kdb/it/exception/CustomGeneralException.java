package com.kdb.it.exception;

/**
 * 일반적인 비즈니스 로직 예외 처리 클래스
 *
 * <p>{@link RuntimeException}을 상속하는 비검사 예외(Unchecked Exception)로,
 * 애플리케이션의 비즈니스 로직에서 발생하는 일반적인 오류를 표현합니다.</p>
 *
 * <p>사용 범위: 서비스/컨트롤러 전반에서 비즈니스 예외 발생 시 사용 (UserController, GuideDocService, CodeService 등).</p>
 * <p>HTTP 400으로 변환됩니다 (GlobalExceptionHandler @ControllerAdvice 처리).</p>
 *
 * <p>{@code GlobalExceptionHandler}가 이 예외를 HTTP 400 표준 오류 응답으로 변환합니다.</p>
 */
public class CustomGeneralException extends RuntimeException {

    /**
     * 오류 메시지만 포함하는 생성자
     *
     * <p>원인 예외(cause) 없이 메시지만으로 예외를 생성합니다.</p>
     *
     * @param message 예외 설명 메시지
     */
    public CustomGeneralException(String message) {
        super(message);
    }

    /**
     * 오류 메시지와 원인 예외를 포함하는 생성자
     *
     * <p>다른 예외를 래핑(wrapping)하여 컨텍스트 정보를 추가할 때 사용합니다.
     * 스택 트레이스에 원인 예외 정보가 함께 출력됩니다.</p>
     *
     * @param message 예외 설명 메시지
     * @param cause   이 예외의 원인이 된 예외 (체이닝용)
     */
    public CustomGeneralException(String message, Throwable cause) {
        super(message, cause);
    }
}
