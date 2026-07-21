package com.kdb.it.infra.eai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GwePropertiesTest {

    @Test
    @DisplayName("GWE IF_ID 미설정 시 현재 임시값을 기본 사용한다")
    void defaults() {
        assertThat(new GweProperties(null).ifId()).isEqualTo("IPPG00000001");
    }

    @Test
    @DisplayName("GWE IF_ID는 KDB 전문 규격 12자리여야 한다")
    void invalidLength() {
        assertThatThrownBy(() -> new GweProperties("SHORT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12자리");
    }
}
