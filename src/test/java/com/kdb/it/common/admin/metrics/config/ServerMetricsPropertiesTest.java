package com.kdb.it.common.admin.metrics.config;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("주기·이력 길이가 0 이하이면 기본값(10초·60분)으로 보정한다")
    void 기본값보정() {
        ServerMetricsProperties properties = new ServerMetricsProperties(0L, -1);
        assertThat(properties.sampleIntervalMs()).isEqualTo(10_000L);
        assertThat(properties.historyMinutes()).isEqualTo(60);
        assertThat(properties.historyCapacity()).isEqualTo(360);
    }

    @Test
    @DisplayName("주기가 이력 길이보다 길어도 용량은 최소 1이다")
    void historyCapacity_최소1() {
        ServerMetricsProperties properties = new ServerMetricsProperties(120_000L, 1);
        assertThat(properties.historyCapacity()).isEqualTo(1);
    }
}
