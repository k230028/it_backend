package com.kdb.it.common.admin.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.admin.metrics.client.ServerMetricsPeerClient;
import com.kdb.it.common.admin.metrics.client.ServerMetricsPeerException;
import com.kdb.it.common.admin.metrics.config.ServerMetricsProperties;
import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerMetricsServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T03:00:00Z");
    private static final ServerMetricsProperties PROPERTIES =
            new ServerMetricsProperties(10_000L, 60);

    @Mock private ServerMetricsCollector collector;
    @Mock private ServerMetricsPeerClient peerClient;

    private static ServerMetricsDto.Sample sample(Instant at, double cpu) {
        return new ServerMetricsDto.Sample(
                at, cpu, null, 1, null, null, null, null, 1L, null, null, 1, 0L, null, null, null,
                null);
    }

    private ServerMetricsService service(ServerMetricsHistory history, Map<String, String> peers) {
        return new ServerMetricsService(
                "SVR1",
                PROPERTIES,
                new WasLogProperties(2000, peers, "secret", 1000, 3000),
                history,
                collector,
                peerClient,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("local: 이력이 있으면 최신 샘플과 시계열을 그대로 돌려주고 즉시 샘플링하지 않는다")
    void local_이력있음() {
        ServerMetricsHistory history = new ServerMetricsHistory(5);
        history.record(sample(NOW.minusSeconds(10), 10.0));
        history.record(sample(NOW, 20.0));

        ServerMetricsDto.InstanceMetrics local = service(history, Map.of()).local();

        assertThat(local.instanceId()).isEqualTo("SVR1");
        assertThat(local.self()).isTrue();
        assertThat(local.latest().systemCpuPct()).isEqualTo(20.0);
        assertThat(local.history()).hasSize(2);
        assertThat(local.peerError()).isNull();
        verify(collector, never()).sampleNow();
    }

    @Test
    @DisplayName("local: 스케줄러가 돌기 전이면 즉시 한 번 샘플링해 빈 화면을 만들지 않는다")
    void local_수집전_즉시샘플링() {
        ServerMetricsHistory history = new ServerMetricsHistory(5);
        given(collector.sampleNow())
                .willAnswer(
                        invocation -> {
                            ServerMetricsDto.Sample s = sample(NOW, 5.0);
                            history.record(s);
                            return s;
                        });

        ServerMetricsDto.InstanceMetrics local = service(history, Map.of()).local();

        assertThat(local.latest().systemCpuPct()).isEqualTo(5.0);
        assertThat(local.history()).hasSize(1);
    }

    @Test
    @DisplayName("aggregate: 피어가 없으면 자기 자신만 담고 주기·이력 길이를 초·분 단위로 싣는다")
    void aggregate_피어없음() {
        ServerMetricsHistory history = new ServerMetricsHistory(5);
        history.record(sample(NOW, 1.0));

        ServerMetricsDto.Response response =
                service(history, Map.of("SVR1", "http://localhost:28080")).aggregate();

        assertThat(response.serverTime()).isEqualTo(NOW);
        assertThat(response.sampleIntervalSec()).isEqualTo(10);
        assertThat(response.historyMinutes()).isEqualTo(60);
        assertThat(response.instances())
                .extracting(ServerMetricsDto.InstanceMetrics::instanceId)
                .containsExactly("SVR1");
        verify(peerClient, never()).fetch(any(), any());
    }

    @Test
    @DisplayName("aggregate: 피어 응답은 self=false로 바로잡아 인스턴스ID 순으로 합친다")
    void aggregate_피어성공() {
        ServerMetricsHistory history = new ServerMetricsHistory(5);
        history.record(sample(NOW, 1.0));
        ServerMetricsDto.Sample peerSample = sample(NOW, 77.0);
        given(peerClient.fetch("http://svr2:28080", "SVR2"))
                .willReturn(
                        new ServerMetricsDto.InstanceMetrics(
                                "SVR2", true, peerSample, List.of(peerSample.toPoint()), null));

        ServerMetricsDto.Response response =
                service(
                                history,
                                Map.of(
                                        "SVR2", "http://svr2:28080",
                                        "SVR1", "http://svr1:28080"))
                        .aggregate();

        assertThat(response.instances())
                .extracting(
                        ServerMetricsDto.InstanceMetrics::instanceId,
                        ServerMetricsDto.InstanceMetrics::self)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("SVR1", true),
                        org.assertj.core.groups.Tuple.tuple("SVR2", false));
        ServerMetricsDto.InstanceMetrics svr2 = response.instances().get(1);
        assertThat(svr2.latest().systemCpuPct()).isEqualTo(77.0);
        assertThat(svr2.history()).hasSize(1);
        assertThat(svr2.peerError()).isNull();
    }

    @Test
    @DisplayName("aggregate: 피어 조회 실패는 빈 이력으로 위장하지 않고 peerError로 싣는다")
    void aggregate_피어실패() {
        ServerMetricsHistory history = new ServerMetricsHistory(5);
        history.record(sample(NOW, 1.0));
        given(peerClient.fetch("http://svr2:28080", "SVR2"))
                .willThrow(new ServerMetricsPeerException("SVR2 인스턴스 조회 실패: timeout", null));

        ServerMetricsDto.Response response =
                service(history, Map.of("SVR2", "http://svr2:28080")).aggregate();

        ServerMetricsDto.InstanceMetrics svr2 = response.instances().get(1);
        assertThat(svr2.instanceId()).isEqualTo("SVR2");
        assertThat(svr2.self()).isFalse();
        assertThat(svr2.latest()).isNull();
        assertThat(svr2.history()).isEmpty();
        assertThat(svr2.peerError()).isEqualTo("SVR2 인스턴스 조회 실패: timeout");
    }

    @Test
    @DisplayName("aggregate: URL이 비어 있는 피어 설정은 건너뛴다")
    void aggregate_빈URL피어_건너뜀() {
        ServerMetricsHistory history = new ServerMetricsHistory(5);
        history.record(sample(NOW, 1.0));

        ServerMetricsDto.Response response = service(history, Map.of("SVR2", "")).aggregate();

        assertThat(response.instances()).hasSize(1);
        verify(peerClient, never()).fetch(any(), any());
    }
}
