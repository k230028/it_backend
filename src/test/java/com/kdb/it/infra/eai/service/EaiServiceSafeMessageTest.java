package com.kdb.it.infra.eai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** EaiService.safeMessage 단위 테스트 (T11c) — 예외 메시지 안전 추출 4분기. */
class EaiServiceSafeMessageTest {

    @Test
    @DisplayName("메시지가 null이면 예외 클래스의 단순명을 반환한다")
    void nullMessage_returnsSimpleClassName() {
        Throwable e = new RuntimeException((String) null);
        assertThat(EaiService.safeMessage(e)).isEqualTo("RuntimeException");
    }

    @Test
    @DisplayName("메시지가 공백이면 예외 클래스의 단순명을 반환한다")
    void blankMessage_returnsSimpleClassName() {
        Throwable e = new IllegalStateException("   ");
        assertThat(EaiService.safeMessage(e)).isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("정상 메시지는 그대로 반환한다")
    void normalMessage_returnedAsIs() {
        Throwable e = new RuntimeException("연결 거부");
        assertThat(EaiService.safeMessage(e)).isEqualTo("연결 거부");
    }

    @Test
    @DisplayName("200자를 초과하는 메시지는 200자에서 잘리고 생략 표기가 붙는다")
    void overLengthMessage_cappedAt200() {
        String longMsg = "x".repeat(250);
        String result = EaiService.safeMessage(new RuntimeException(longMsg));

        assertThat(result).hasSize(200 + "...(생략)".length());
        assertThat(result).isEqualTo("x".repeat(200) + "...(생략)");
    }
}
