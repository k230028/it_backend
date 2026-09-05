package com.kdb.it.common.admin.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerMetricsCollectorTest {

    private static final Instant NOW = Instant.parse("2026-09-05T03:00:00Z");

    @Mock private ServerMetricsProbe probe;

    private static ServerMetricsDto.Sample sample(Instant at) {
        return new ServerMetricsDto.Sample(
                at, 1.0, null, 1, null, null, null, null, 1L, null, null, 1, 0L, null, null, null,
                null);
    }

    @Test
    @DisplayName("collect는 현재 시각으로 샘플링해 이력에 기록한다")
    void collect_이력기록() {
        ServerMetricsHistory history = new ServerMetricsHistory(5);
        given(probe.sample(NOW)).willReturn(sample(NOW));
        ServerMetricsCollector collector =
                new ServerMetricsCollector(probe, history, Clock.fixed(NOW, ZoneOffset.UTC));

        collector.collect();

        assertThat(history.latest()).map(ServerMetricsDto.Sample::at).contains(NOW);
    }

    @Test
    @DisplayName("collect는 프로브 예외를 삼켜 스케줄러를 살리고, sampleNow는 그대로 던진다")
    void collect_예외삼킴_sampleNow_전파() {
        ServerMetricsHistory history = new ServerMetricsHistory(5);
        given(probe.sample(NOW)).willThrow(new IllegalStateException("MXBean 실패"));
        ServerMetricsCollector collector =
                new ServerMetricsCollector(probe, history, Clock.fixed(NOW, ZoneOffset.UTC));

        collector.collect();
        assertThat(history.latest()).isEmpty();

        assertThatThrownBy(collector::sampleNow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MXBean 실패");
    }
}
