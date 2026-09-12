package com.kdb.it.common.admin.metrics.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServerMetricsPropertiesTest {

    @Test
    @DisplayName("링버퍼 용량은 이력 길이(분)를 샘플링 주기로 나눈 샘플 수다")
    void historyCapacity_주기와이력길이로계산() {
        ServerMetricsProperties properties = new ServerMetricsProperties(10_000L, 60);
        assertThat(properties.historyCapacity()).isEqualTo(360);
    }

    @Test
    @DisplayName("주기·이력 길이가 누락(null)이면 기본값(10초·60분)을 적용한다")
    void 누락값_기본값적용() {
        ServerMetricsProperties properties = new ServerMetricsProperties(null, null);
        assertThat(properties.sampleIntervalMs()).isEqualTo(10_000L);
        assertThat(properties.historyMinutes()).isEqualTo(60);
        assertThat(properties.historyCapacity()).isEqualTo(360);
    }

    @Test
    @DisplayName("주기가 0 이하로 명시되면 기본값으로 숨기지 않고 속성명을 담은 예외로 차단한다")
    void 주기_0이하_예외() {
        assertThatThrownBy(() -> new ServerMetricsProperties(0L, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.server-metrics.sample-interval-ms")
                .hasMessageContaining("0");
        assertThatThrownBy(() -> new ServerMetricsProperties(-5L, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.server-metrics.sample-interval-ms");
    }

    @Test
    @DisplayName("이력 길이가 0 이하로 명시되면 기본값으로 숨기지 않고 속성명을 담은 예외로 차단한다")
    void 이력길이_0이하_예외() {
        assertThatThrownBy(() -> new ServerMetricsProperties(10_000L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.server-metrics.history-minutes");
        assertThatThrownBy(() -> new ServerMetricsProperties(10_000L, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.server-metrics.history-minutes")
                .hasMessageContaining("-1");
    }

    @Test
    @DisplayName("주기가 이력 길이보다 길어도 용량은 최소 1이다")
    void historyCapacity_최소1() {
        ServerMetricsProperties properties = new ServerMetricsProperties(120_000L, 1);
        assertThat(properties.historyCapacity()).isEqualTo(1);
    }
}
