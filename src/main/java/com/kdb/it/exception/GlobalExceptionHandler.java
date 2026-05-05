package com.kdb.it.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 핸들러
 *
 * <p>
 * {@code @RestControllerAdvice}를 통해 모든 컨트롤러에서 발생하는
 * 예외를 일관된 JSON 형식의 오류 응답으로 변환합니다.
 * </p>
 *
 * <p>
 * 오류 응답 형식:
 * </p>
 * <pre>
 * {
 *   "timestamp": "2026-03-04T10:00:00",
 *   "status": 400,
 *   "message": "오류 메시지"
 * }
 * </pre>
 *
 * <p>
 * 처리 예외 유형:
 * </p>
 * <ul>
 * <li>{@link CustomGeneralException}: 비즈니스 로직 예외 → 400</li>
 * <li>{@link IllegalArgumentException}: 잘못된 인자 (중복, 미존재 등) → 400</li>
 * <li>{@link IllegalStateException}: 비즈니스 규칙 위반 (결재중 수정 불가 등) → 400</li>
 * <li>{@link RuntimeException}: 런타임 예외 (인증 실패 등) → 400</li>
 * <li>{@link Exception}: 예상치 못한 서버 오류 → 500</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 비즈니스 로직 예외 처리 (400 Bad Request)
     *
     * @param e {@link CustomGeneralException}
     * @return 400 응답 + 오류 메시지
     */
    @ExceptionHandler(CustomGeneralException.class)
    public ResponseEntity<Map<String, Object>> handleCustomGeneralException(CustomGeneralException e) {
        log.warn("비즈니스 예외 발생: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 잘못된 인자 예외 처리 (400 Bad Request)
     *
     * <p>리소스 미존재, 중복 등록 등의 상황에서 발생합니다.</p>
     *
     * @param e {@link IllegalArgumentException}
     * @return 400 응답 + 오류 메시지
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("잘못된 인자 예외 발생: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 비즈니스 규칙 위반 예외 처리 (400 Bad Request)
     *
     * <p>결재중/결재완료 상태에서의 수정·삭제 시도 등의 상황에서 발생합니다.</p>
     *
     * @param e {@link IllegalStateException}
     * @return 400 응답 + 오류 메시지
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException e) {
        log.warn("비즈니스 규칙 위반 예외 발생: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 런타임 예외 처리 (400 Bad Request)
     *
     * <p>인증 실패(사번 미존재, 비밀번호 불일치, 토큰 오류 등) 상황에서 발생합니다.</p>
     *
     * @param e {@link RuntimeException}
     * @return 400 응답 + 오류 메시지
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.warn("런타임 예외 발생: {}", e.getMessage(), e);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 클라이언트 연결 끊김 처리 (응답 없음)
     *
     * <p>파일 다운로드·미리보기 도중 클라이언트(브라우저)가 연결을 닫으면 발생합니다.
     * 이미 Content-Type이 image/* 등으로 설정된 상태에서 오류 JSON을 쓰려 하면
     * HttpMessageNotWritableException이 연쇄 발생하므로, void 반환으로 응답 쓰기를 생략합니다.</p>
     *
     * @param e {@link AsyncRequestNotUsableException}
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException e) {
        // 클라이언트 정상 취소(다운로드 중단, 탭 닫기 등) — ERROR 로그 불필요
        log.debug("클라이언트 연결이 끊어졌습니다: {}", e.getMessage());
    }

    /**
     * 예상치 못한 서버 오류 처리 (500 Internal Server Error)
     *
     * @param e {@link Exception}
     * @return 500 응답 + 일반 오류 메시지
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("서버 내부 오류 발생: {}", e.getMessage(), e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }

    /**
     * 표준 오류 응답 생성 헬퍼 메서드
     *
     * @param status  HTTP 상태 코드
     * @param message 오류 메시지
     * @return JSON 형태의 오류 응답
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
