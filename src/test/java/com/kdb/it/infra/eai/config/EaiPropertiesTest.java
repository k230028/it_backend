package com.kdb.it.infra.eai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** EaiProperties 컴팩트 생성자 가드 테스트 (T11c). */
class EaiPropertiesTest {

    private EaiProperties props(boolean enabled, String url) {
        return props(enabled, url, "L");
    }

    private EaiProperties props(boolean enabled, String url, String sysEnvTc) {
        return new EaiProperties(
                enabled, url, "MS949", 3000, 3000, sysEnvTc, "IPP", "IPP", "PRM", "PP");
    }

    @Test
    @DisplayName("sysEnvTc가 비어 있으면 L로 보정하고 P는 그대로 유지한다")
    void sysEnvTc_defaultsToLocalAndKeepsProduction() {
        assertThat(props(false, "", " ").sysEnvTc()).isEqualTo("L");
        assertThat(props(false, "", "P").sysEnvTc()).isEqualTo("P");
    }

    @Test
    @DisplayName("sysEnvTc가 1자리가 아니면 기동 시 IllegalStateException")
    void sysEnvTc_notOneChar_throws() {
        assertThatThrownBy(() -> props(false, "", "PROD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eai.sys-env-tc");
    }

    @Test
    @DisplayName("enabled=true 인데 url이 공백이면 IllegalStateException")
    void enabledTrueBlankUrl_throws() {
        assertThatThrownBy(() -> props(true, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eai.url");
    }

    @Test
    @DisplayName("enabled=false 면 url이 공백이어도 허용")
    void disabledBlankUrl_ok() {
        assertThatCode(() -> props(false, "")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enabled=true 이고 url이 있으면 허용")
    void enabledWithUrl_ok() {
        assertThatCode(() -> props(true, "http://eai.internal/std")).doesNotThrowAnyException();
    }
}
