package com.kdb.it.exception;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ErrorResponse;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.common.mfa.exception.MfaException;
import com.kdb.it.domain.budget.cost.dto.CostConflictResponse;
import com.kdb.it.domain.budget.cost.exception.CostConflictException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 핸들러
 *
 * <p>{@code @RestControllerAdvice}를 통해 모든 컨트롤러에서 발생하는 예외를 일관된 JSON 형식의 오류 응답으로 변환합니다.
 *
 * <p>오류 응답 형식:
 *
 * <pre>
 * {
 *   "timestamp": "2026-03-04T10:00:00",
 *   "status": 400,
 *   "message": "오류 메시지"
 * }
 * </pre>
 *
 * <p>처리 예외 유형:
 *
 * <ul>
 *   <li>{@link CustomGeneralException}: 비즈니스 로직 예외 → 400
 *   <li>{@link IllegalArgumentException}: 잘못된 인자 (중복, 미존재 등) → 400
 *   <li>{@link IllegalStateException}: 비즈니스 규칙 위반 (결재중 수정 불가 등) → 400
 *   <li>{@link RuntimeException}: 별도 매핑되지 않은 런타임 예외 → 400
 *   <li>{@link Exception}: 예상치 못한 서버 오류 → 500
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 전산예산 미리보기·상신 오류를 코드와 변경 원장 목록으로 반환한다. */
    @ExceptionHandler(ItBudgetApprovalException.class)
    public ResponseEntity<ErrorResponse> handleItBudgetApproval(ItBudgetApprovalException e) {
        log.warn("전산예산 결재 오류: code={}, status={}", e.code(), e.status().value());
        ErrorResponse body =
                new ErrorResponse(
                        LocalDateTime.now(),
                        e.status().value(),
                        e.code(),
                        e.getMessage(),
                        e.changedSources());
        return ResponseEntity.status(e.status()).body(body);
    }

    /** 전산업무비 저장 충돌을 코드와 현재 원장 상태로 반환한다. */
    @ExceptionHandler(CostConflictException.class)
    public ResponseEntity<CostConflictResponse> handleCostConflict(CostConflictException e) {
        log.warn("전산업무비 저장 충돌: code={}, status={}", e.code(), e.status().value());
        CostConflictResponse body =
                new CostConflictResponse(
                        LocalDateTime.now(),
                        e.status().value(),
                        e.code(),
                        e.getMessage(),
                        e.changedBy(),
                        e.changedByEno(),
                        e.changedAt(),
                        e.currentStamp(),
                        e.current());
        return ResponseEntity.status(e.status()).body(body);
    }

    /**
     * 비즈니스 로직 예외 처리 (400 Bad Request)
     *
     * @param e {@link CustomGeneralException}
     * @return 400 응답 + 오류 메시지
     */
    @ExceptionHandler(CustomGeneralException.class)
    public ResponseEntity<Map<String, Object>> handleCustomGeneralException(
            CustomGeneralException e) {
        log.warn("비즈니스 예외 발생: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** MFA 표준 오류를 클라이언트가 분기할 수 있는 코드와 HTTP 상태로 변환한다. */
    @ExceptionHandler(MfaException.class)
    public ResponseEntity<Map<String, Object>> handleMfaException(MfaException e) {
        if (e.providerCode() != null) {
            log.warn(
                    "MFA 거래 거부: code={}, providerCode={}, providerMessage={}",
                    e.errorCode().name(),
                    e.providerCode(),
                    e.providerMessage());
        } else {
            log.warn("MFA 거래 거부: code={}", e.errorCode().name());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", e.errorCode().status().value());
        body.put("code", e.errorCode().name());
        body.put("message", e.errorCode().message());
        if (e.providerCode() != null) {
            body.put("providerCode", e.providerCode());
            body.put("providerMessage", e.providerMessage());
        }
        return ResponseEntity.status(e.errorCode().status()).body(body);
    }

    /**
     * 잘못된 인자 예외 처리 (400 Bad Request)
     *
     * <p>리소스 미존재, 중복 등록 등의 상황에서 발생합니다.
     *
     * @param e {@link IllegalArgumentException}
     * @return 400 응답 + 오류 메시지
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException e) {
        log.warn("잘못된 인자 예외 발생: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 비즈니스 규칙 위반 예외 처리 (400 Bad Request)
     *
     * <p>결재중/결재완료 상태에서의 수정·삭제 시도 등의 상황에서 발생합니다.
     *
     * @param e {@link IllegalStateException}
     * @return 400 응답 + 오류 메시지
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(
            IllegalStateException e) {
        log.warn("비즈니스 규칙 위반 예외 발생: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 서버 데이터 정합성 오류 처리.
     *
     * @param e 데이터 손상 예외
     * @return 500 응답과 진단 문맥
     */
    @ExceptionHandler(DataCorruptionException.class)
    public ResponseEntity<Map<String, Object>> handleDataCorruption(DataCorruptionException e) {
        log.error("서버 데이터 정합성 오류: {}", e.getMessage(), e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    /**
     * Bean Validation 실패 예외 처리 (400 Bad Request)
     *
     * <p>{@code @Valid} 어노테이션이 붙은 요청 DTO의 필드 검증 실패 시 발생합니다. 실패한 필드명과 오류 메시지를 쉼표로 구분하여 반환합니다.
     *
     * @param e {@link MethodArgumentNotValidException} — 필드 검증 실패 정보 포함
     * @return 400 응답 + "필드명: 오류 메시지" 형식의 문자열
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException e) {
        String message =
                e.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .collect(Collectors.joining(", "));
        log.warn("입력값 검증 실패: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 접근 권한 없음 예외 처리 (403 Forbidden)
     *
     * <p>서비스 계층의 소유권/권한 검증({@code OwnershipVerifier}) 실패 시 발생합니다. 포괄 {@code RuntimeException}
     * 핸들러(400)보다 우선 매칭되어 403으로 반환합니다.
     *
     * @param e {@link AccessDeniedException}
     * @return 403 응답 + 오류 메시지
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        log.warn("접근 권한 없음: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /**
     * 리소스 미존재 예외 처리 (404 Not Found)
     *
     * <p>{@link NotFoundException}을 404로 매핑합니다. 잘못된 입력값(400)과 구분하기 위한 전용 핸들러로, 포괄 {@code
     * RuntimeException} 핸들러(400)보다 우선 매칭됩니다.
     *
     * @param e {@link NotFoundException}
     * @return 404 응답 + 오류 메시지
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e) {
        log.warn("리소스 미존재: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * 정적 리소스 미존재 예외 처리 (404 Not Found, 로그 노이즈 제거)
     *
     * <p>브라우저가 자동 요청하는 {@code /favicon.ico}처럼 매핑되지 않은 정적 리소스 요청에서 발생합니다. {@link
     * NoResourceFoundException}은 {@link ResponseStatusException} 하위 타입이라 전용 핸들러가 없으면 스택트레이스와 함께
     * 로깅되어 운영 로그를 오염시킵니다. 클라이언트 실수이지 서버 오류가 아니므로 스택트레이스 없이 DEBUG로만 남기고 404로 응답합니다.
     *
     * @param e {@link NoResourceFoundException}
     * @return 404 응답
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("정적 리소스 미존재: {}", e.getResourcePath());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.");
    }

    /**
     * 지원하지 않는 HTTP 메서드 요청을 처리합니다.
     *
     * @param e 지원하지 않는 HTTP 메서드 예외
     * @return 405 응답
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        log.debug("지원하지 않는 HTTP 메서드: {}", e.getMethod());
        ResponseEntity<Map<String, Object>> errorResponse =
                buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.");
        Set<HttpMethod> supportedMethods = e.getSupportedHttpMethods();
        if (supportedMethods == null || supportedMethods.isEmpty()) {
            return errorResponse;
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(supportedMethods.toArray(HttpMethod[]::new))
                .body(errorResponse.getBody());
    }

    /**
     * {@code consumes}와 일치하지 않는 Content-Type 요청을 처리합니다.
     *
     * <p>본 핸들러가 없으면 하위 {@link jakarta.servlet.ServletException}이라 {@code RuntimeException} 핸들러에도
     * 잡히지 않고 최하단 {@code Exception} 핸들러가 500으로 처리해, 클라이언트가 요청 형식을 고쳐도 재시도가 불가능한 오류로 보이게 됩니다.
     *
     * @param e 지원하지 않는 미디어 타입 예외
     * @return 415 응답
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e) {
        log.debug("지원하지 않는 Content-Type: {}", e.getContentType());
        return buildErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다.");
    }

    /**
     * 명시적 상태코드 예외 처리 (상태코드 보존)
     *
     * <p>{@link ResponseStatusException}이 지정한 HTTP 상태코드(404·500 등)를 그대로 보존합니다. 본 핸들러가 없으면 {@code
     * RuntimeException} 핸들러가 400으로 강등합니다. 5xx는 ERROR, 그 외는 WARN으로 로깅합니다.
     *
     * @param e {@link ResponseStatusException}
     * @return 지정 상태코드 응답 + 사유 메시지
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String message = e.getReason() != null ? e.getReason() : status.getReasonPhrase();
        if (status.is5xxServerError()) {
            log.error("상태코드 예외(5xx): status={}, reason={}", status.value(), message, e);
        } else {
            log.warn("상태코드 예외: status={}, reason={}", status.value(), message, e);
        }
        return buildErrorResponse(status, message);
    }

    /**
     * 별도 매핑되지 않은 런타임 예외 처리 (400 Bad Request)
     *
     * <p>더 구체적인 처리기에 매핑되지 않은 런타임 예외를 일괄 처리합니다. 서버 내부 결함도 이 범위에 포함될 수 있으므로 원인 예외를 경고 로그에 남깁니다.
     *
     * @param e {@link RuntimeException}
     * @return 400 응답 + 일반 오류 메시지
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        // 클라이언트 연결 끊김이 HttpMessageNotWritableException 등으로 래핑되어 들어오면
        // RuntimeException 처리기가 먼저 매칭되므로 원인 예외 연결을 검사해 조용히 처리합니다.
        // (응답을 다시 쓰면 끊긴 연결에 2차 IOException이 발생하므로 본문을 생략한다.)
        if (isClientDisconnect(e)) {
            log.debug("클라이언트 연결이 끊어졌습니다: {}", e.getMessage());
            return null;
        }
        log.warn("런타임 예외 발생: {}", e.getMessage(), e);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "요청을 처리할 수 없습니다.");
    }

    /**
     * 잘못된 Refresh Token 예외 처리 (401 Unauthorized)
     *
     * <p>{@link InvalidRefreshTokenException}이 컨트롤러 밖으로 전파된 경우의 방어선입니다. {@code /api/auth/refresh}는
     * {@code AuthController}의 helper에서 401과 함께 Access·Refresh 쿠키를 직접 삭제하므로 정상 흐름에서는 이 handler를 거치지
     * 않습니다. 쿠키를 다룰 수 없는 비-인증 컨트롤러에서 전파된 경우를 대비해 최소한 상태코드(401)와 재로그인 안내만 보장합니다. {@link
     * InvalidRefreshTokenException}은 {@code RuntimeException} 하위 타입이지만, 더 구체적인 이 handler가 포괄 {@code
     * RuntimeException} 핸들러(400)보다 우선 매칭됩니다.
     *
     * @param e {@link InvalidRefreshTokenException}
     * @return 401 응답 + 재로그인 안내 메시지
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<String> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        // 토큰 값은 남기지 않고 재로그인 필요 사실만 경고 로그로 기록
        log.warn("잘못된 Refresh Token 요청이 전역 핸들러까지 전파되었습니다.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("다시 로그인해 주세요.");
    }

    /**
     * cause 체인에 클라이언트 연결 끊김(다운로드 중단, 탭 닫기 등)이 있는지 판별합니다.
     *
     * <p>{@link AsyncRequestNotUsableException} 또는 Tomcat {@code ClientAbortException}이 원인으로 포함되면
     * 정상적인 클라이언트 취소로 간주합니다. Tomcat 클래스에 대한 컴파일 의존을 피하기 위해 단순 클래스명으로 비교합니다.
     *
     * @param e 검사 대상 예외
     * @return 연결 끊김으로 판단되면 {@code true}
     */
    private boolean isClientDisconnect(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof AsyncRequestNotUsableException
                    || "ClientAbortException".equals(cause.getClass().getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 클라이언트 연결 끊김 처리 (응답 없음)
     *
     * <p>파일 다운로드·미리보기 도중 클라이언트(브라우저)가 연결을 닫으면 발생합니다. 이미 Content-Type이 image/* 등으로 설정된 상태에서 오류 JSON을
     * 쓰려 하면 HttpMessageNotWritableException이 연쇄 발생하므로, void 반환으로 응답 쓰기를 생략합니다.
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
     * @param status HTTP 상태 코드
     * @param message 오류 메시지
     * @return JSON 형태의 오류 응답
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
