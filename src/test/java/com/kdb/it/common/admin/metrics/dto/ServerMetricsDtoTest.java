package com.kdb.it.common.admin.metrics.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServerMetricsDtoTest {

    @Test
    @DisplayName("백분율은 소수 첫째 자리로 반올림하고 분모가 없으면 null이다")
    void percent_반올림과분모없음() {
        assertThat(ServerMetricsDto.Sample.percent(333L, 1000L)).isEqualTo(33.3);
        assertThat(ServerMetricsDto.Sample.percent(1L, 3L)).isEqualTo(33.3);
        assertThat(ServerMetricsDto.Sample.percent(100L, 0L)).isNull();
        assertThat(ServerMetricsDto.Sample.percent(100L, null)).isNull();
        assertThat(ServerMetricsDto.Sample.percent(null, 100L)).isNull();
    }
}
