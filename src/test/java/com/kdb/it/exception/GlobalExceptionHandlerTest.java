package com.kdb.it.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * GlobalExceptionHandler 단위 테스트
 *
 * <p>
 * GlobalExceptionHandler 를 직접 인스턴스화하여 각 @ExceptionHandler 메서드가
 * 올바른 HTTP 상태코드와 JSON 오류 응답 본문(timestamp/status/message)을 반환하는지 검증합니다.
 * Spring 컨텍스트 없이 순수 단위 테스트로 동작합니다.
 * </p>
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ---- 성공 케이스 ----

    /** CustomGeneralException 발생 시 400 Bad Request 와 비즈니스 오류 메시지를 반환해야 합니다. */
    @Test
    @DisplayName("handleCustomGeneralException - 비즈니스 예외 발생 시 400 반환")
    void handleCustomGeneralException_비즈니스예외_400반환() {
        // Arrange
        CustomGeneralException ex = new CustomGeneralException("비즈니스 로직 오류 메시지");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleCustomGeneralException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("message", "비즈니스 로직 오류 메시지");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    /** IllegalArgumentException 발생 시 400 Bad Request 와 오류 메시지를 반환해야 합니다. */
    @Test
    @DisplayName("handleIllegalArgumentException - 잘못된 인자 예외 발생 시 400 반환")
    void handleIllegalArgumentException_잘못된인자예외_400반환() {
        // Arrange
        IllegalArgumentException ex = new IllegalArgumentException("잘못된 인자 오류 메시지");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgumentException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("message", "잘못된 인자 오류 메시지");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    /** IllegalStateException 발생 시 400 Bad Request 와 오류 메시지를 반환해야 합니다. */
    @Test
    @DisplayName("handleIllegalStateException - 비즈니스 규칙 위반 예외 발생 시 400 반환")
    void handleIllegalStateException_규칙위반예외_400반환() {
        // Arrange
        IllegalStateException ex = new IllegalStateException("비즈니스 규칙 위반 메시지");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalStateException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("message", "비즈니스 규칙 위반 메시지");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    // ---- 실패 케이스 ----

    /**
     * RuntimeException 발생 시 원본 메시지가 아닌 고정 오류 메시지를 반환해야 합니다.
     * 인증 오류 등 내부 정보 노출을 방지하기 위한 설계입니다.
     */
    @Test
    @DisplayName("handleRuntimeException - 런타임 예외 발생 시 고정 메시지로 400 반환")
    void handleRuntimeException_런타임예외_고정메시지400반환() {
        // Arrange
        RuntimeException ex = new RuntimeException("런타임 오류 메시지");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleRuntimeException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("message", "요청을 처리할 수 없습니다.");
    }

    /** RuntimeException 발생 시 원본 예외 메시지가 응답 본문에 포함되지 않아야 합니다. */
    @Test
    @DisplayName("handleRuntimeException - 런타임 예외의 원본 메시지는 응답에 노출되지 않아야 함")
    void handleRuntimeException_원본메시지_응답에미포함() {
        // Arrange
        RuntimeException ex = new RuntimeException("런타임 오류 메시지");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleRuntimeException(ex);

        // Assert: 원본 메시지가 응답 본문에 노출되지 않아야 함
        assertThat(response.getBody()).doesNotContainValue("런타임 오류 메시지");
    }

    /**
     * CustomGeneralException 은 RuntimeException 하위 클래스이지만,
     * 더 구체적인 핸들러가 우선 처리되어 원본 메시지를 그대로 반환해야 합니다.
     */
    @Test
    @DisplayName("handleCustomGeneralException - RuntimeException 핸들러보다 우선 처리되어 원본 메시지 반환")
    void handleCustomGeneralException_RuntimeException보다_우선처리() {
        // Arrange
        CustomGeneralException ex = new CustomGeneralException("비즈니스 로직 오류 메시지");

        // Act: CustomGeneralException 핸들러 직접 호출
        ResponseEntity<Map<String, Object>> response = handler.handleCustomGeneralException(ex);

        // Assert: 고정 메시지가 아닌 원본 메시지가 반환되어야 함
        assertThat(response.getBody()).containsEntry("message", "비즈니스 로직 오류 메시지");
        assertThat(response.getBody()).doesNotContainValue("요청을 처리할 수 없습니다.");
    }

    /** AccessDeniedException 발생 시 403 Forbidden 과 원본 메시지를 반환해야 합니다. */
    @Test
    @DisplayName("handleAccessDenied - 권한 없음 예외 발생 시 403 반환")
    void handleAccessDenied_권한없음_403반환() {
        // Arrange
        AccessDeniedException ex = new AccessDeniedException("본인 또는 관리자만 수행할 수 있습니다.");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("status", 403);
        assertThat(response.getBody()).containsEntry("message", "본인 또는 관리자만 수행할 수 있습니다.");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    /** NotFoundException 발생 시 404 Not Found 와 원본 메시지를 반환해야 합니다. */
    @Test
    @DisplayName("handleNotFound - 리소스 미존재 예외 발생 시 404 반환")
    void handleNotFound_리소스미존재_404반환() {
        // Arrange
        NotFoundException ex = new NotFoundException("신청서를 찾을 수 없습니다: APF-2026-0001");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsEntry("message", "신청서를 찾을 수 없습니다: APF-2026-0001");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    /** ResponseStatusException 발생 시 지정한 상태코드(500 등)를 그대로 보존해 반환해야 합니다. */
    @Test
    @DisplayName("handleResponseStatus - 지정 상태코드를 보존하여 반환")
    void handleResponseStatus_상태코드보존() {
        // Arrange
        ResponseStatusException ex = new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "계획 스냅샷 직렬화에 실패했습니다.");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody()).containsEntry("message", "계획 스냅샷 직렬화에 실패했습니다.");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    /**
     * NoResourceFoundException(예: /favicon.ico)은 ERROR/500이 아니라 조용한 404로 처리되어야 합니다.
     * 전용 핸들러가 없으면 포괄 Exception 핸들러(500, 스택트레이스)로 떨어져 로그를 오염시킵니다.
     */
    @Test
    @DisplayName("handleNoResourceFound - 정적 리소스 미존재 시 404 반환(서버 오류 아님)")
    void handleNoResourceFound_정적리소스미존재_404반환() {
        // Arrange
        NoResourceFoundException ex =
                new NoResourceFoundException(HttpMethod.GET, "favicon.ico", "No static resource favicon.ico");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleNoResourceFound(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsKey("timestamp");
    }

    // ---- 엣지 케이스 ----

    /** 예상치 못한 Exception 발생 시 500 Internal Server Error 를 반환해야 합니다. */
    @Test
    @DisplayName("handleException - 일반 예외 발생 시 500 반환")
    void handleException_일반예외_500반환() throws Exception {
        // Arrange
        Exception ex = new Exception("예상치 못한 서버 오류");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody()).containsEntry("message", "서버 내부 오류가 발생했습니다.");
    }

    /** 오류 응답은 timestamp, status, message 세 필드를 모두 포함해야 합니다. */
    @Test
    @DisplayName("buildErrorResponse - 오류 응답에 timestamp/status/message 필드가 모두 포함되어야 함")
    void buildErrorResponse_응답구조_세필드모두포함() {
        // Arrange
        CustomGeneralException ex = new CustomGeneralException("테스트 오류");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleCustomGeneralException(ex);

        // Assert: 표준 오류 응답 구조 검증
        assertThat(response.getBody()).containsKeys("timestamp", "status", "message");
    }

    /** 500 오류 응답의 메시지는 원본 예외 메시지를 노출하지 않고 고정된 일반 메시지를 반환해야 합니다. */
    @Test
    @DisplayName("handleException - 500 응답은 원본 예외 메시지를 노출하지 않아야 함")
    void handleException_500응답_원본메시지미노출() throws Exception {
        // Arrange
        Exception ex = new Exception("내부 DB 오류 상세 정보");

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleException(ex);

        // Assert
        assertThat(response.getBody()).doesNotContainValue("내부 DB 오류 상세 정보");
        assertThat(response.getBody()).containsEntry("message", "서버 내부 오류가 발생했습니다.");
    }
}
