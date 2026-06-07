package com.kdb.it.infra.eai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EaiMessageBuilderTest {

    private static final Charset MS949 = Charset.forName("MS949");

    @Test
    @DisplayName("lpad: 숫자 타입은 '0', 그 외 타입은 공백으로 좌측 패딩")
    void lpad_padsLeft() {
        assertThat(EaiMessageBuilder.lpad(MS949, "N", 5, "42")).isEqualTo("00042");
        assertThat(EaiMessageBuilder.lpad(MS949, "C", 5, "ab")).isEqualTo("   ab");
    }

    @Test
    @DisplayName("lpad: null/빈 문자열은 전체 패딩")
    void lpad_nullBecomesFullPad() {
        assertThat(EaiMessageBuilder.lpad(MS949, "C", 3, null)).isEqualTo("   ");
        assertThat(EaiMessageBuilder.lpad(MS949, "N", 3, "")).isEqualTo("000");
    }

    @Test
    @DisplayName("lpad: MS949에서 한글 1자는 2바이트로 계산되어 패딩 폭이 줄어든다")
    void lpad_koreanIsTwoBytes() {
        assertThat(EaiMessageBuilder.lpad(MS949, "C", 4, "가")).isEqualTo("  가");
        assertThat("가".getBytes(MS949)).hasSize(2);
    }

    @Test
    @DisplayName("lpad: 내용 바이트가 offset을 초과하면 IndexOutOfBoundsException")
    void lpad_overflowThrows() {
        assertThatThrownBy(() -> EaiMessageBuilder.lpad(MS949, "C", 1, "abc"))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
