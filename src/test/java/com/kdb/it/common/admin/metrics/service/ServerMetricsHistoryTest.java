package com.kdb.it.common.admin.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.admin.metrics.config.ServerMetricsProperties;
import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServerMetricsHistoryTest {

    private static ServerMetricsDto.Sample sample(int second, double cpu) {
        return new ServerMetricsDto.Sample(
                Instant.parse("2026-09-05T00:00:00Z").plusSeconds(second),
                cpu,
                null,
                4,
                null,
                1000L,
                500L,
                800L,
                200L,
                null,
                null,
                10,
                60L,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("수집 전에는 최신 샘플이 비어 있고 시계열도 비어 있다")
    void 수집전_비어있음() {
        ServerMetricsHistory history = new ServerMetricsHistory(3);
        assertThat(history.latest()).isEmpty();
        assertThat(history.points()).isEmpty();
    }

    @Test
    @DisplayName("용량을 넘기면 가장 오래된 샘플부터 버리고 오래된 순을 유지한다")
    void 용량초과_오래된샘플부터제거() {
        ServerMetricsHistory history = new ServerMetricsHistory(3);
        for (int i = 0; i < 5; i++) history.record(sample(i * 10, i));

        assertThat(history.points())
                .extracting(ServerMetricsDto.Point::systemCpuPct)
                .containsExactly(2.0, 3.0, 4.0);
        assertThat(history.latest()).map(ServerMetricsDto.Sample::systemCpuPct).contains(4.0);
    }

    @Test
    @DisplayName("시계열 점은 메모리·힙 바이트를 백분율로 바꿔 담는다")
    void points_백분율변환() {
        ServerMetricsHistory history = new ServerMetricsHistory(1);
        history.record(sample(0, 12.3));

        ServerMetricsDto.Point point = history.points().getFirst();
        assertThat(point.memUsedPct()).isEqualTo(50.0);
        assertThat(point.heapUsedPct()).isEqualTo(25.0);
        assertThat(point.load1m()).isNull();
    }

    @Test
    @DisplayName("용량 0 이하와 null 샘플은 거부한다")
    void 잘못된입력_거부() {
        assertThatThrownBy(() -> new ServerMetricsHistory(0))
                .isInstanceOf(IllegalArgumentException.class);
        ServerMetricsHistory history = new ServerMetricsHistory(1);
        assertThatThrownBy(() -> history.record(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("설정 생성자는 샘플링 주기와 이력 길이에서 계산한 용량을 그대로 쓴다")
    void 설정생성자_용량계산() {
        // 10초 주기로 60분을 보관하면 360개다. 이 값이 어긋나면 이력 그래프가
        // 설정한 구간보다 짧거나 길게 잘린다.
        ServerMetricsProperties properties = new ServerMetricsProperties(10_000L, 60);

        ServerMetricsHistory history = new ServerMetricsHistory(properties);

        assertThat(history.capacity()).isEqualTo(360);
        assertThat(properties.historyCapacity()).isEqualTo(360);
    }

    @Test
    @DisplayName("capacity()는 링버퍼가 실제로 유지하는 샘플 수와 일치한다")
    void capacity_실제보관수와일치() {
        ServerMetricsHistory history = new ServerMetricsHistory(3);
        for (int i = 0; i < 10; i++) history.record(sample(i * 10, i));

        assertThat(history.capacity()).isEqualTo(3);
        assertThat(history.points()).hasSize(history.capacity());
    }
}
