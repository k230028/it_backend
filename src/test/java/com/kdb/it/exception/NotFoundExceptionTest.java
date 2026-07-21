package com.kdb.it.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NotFoundException 단위 테스트 — 메시지 보존 및 RuntimeException 상속 검증. */
class NotFoundExceptionTest {

    @Test
    @DisplayName("메시지를 그대로 보존한다")
    void preservesMessage() {
        NotFoundException ex = new NotFoundException("신청서를 찾을 수 없습니다: APF-2026-0001");
        assertThat(ex.getMessage()).isEqualTo("신청서를 찾을 수 없습니다: APF-2026-0001");
    }

    @Test
    @DisplayName("RuntimeException 하위 타입이다")
    void isRuntimeException() {
        assertThat(new NotFoundException("x")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("원인 예외(cause)를 보존한다")
    void preservesCause() {
        Throwable cause = new IllegalStateException("원본 원인");
        NotFoundException ex = new NotFoundException("문서를 찾을 수 없습니다", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).isEqualTo("문서를 찾을 수 없습니다");
    }
}
