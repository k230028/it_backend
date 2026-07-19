package com.kdb.it.exception;

/**
 * Refresh Token 검증·조회·회전 실패를 HTTP 401 계약으로 전달하는 전용 예외.
 *
 * <p>
 * Refresh 갱신 경로에서 서명·용도 검증 실패, DB 미존재, 만료, 재사용 감지처럼
 * 클라이언트에 재로그인이 필요한 분기를 하나의 타입으로 통일합니다. 컨트롤러와
 * {@code GlobalExceptionHandler}는 이 예외를 HTTP 401과 인증 쿠키 삭제로 변환합니다.
 * </p>
 *
 * <p>
 * 내부 원인(서명 실패·미존재·만료·재사용)은 토큰 값을 남기지 않고 서버 로그로만 구분하며,
 * 클라이언트에는 단일 메시지만 노출합니다.
 * </p>
 */
public class InvalidRefreshTokenException extends RuntimeException {

    /** 클라이언트 노출용 단일 메시지로 예외를 생성합니다. */
    public InvalidRefreshTokenException() {
        super("유효하지 않은 Refresh Token입니다.");
    }
}
