package com.kdb.it.common.admin.metrics.service;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 고정 주기로 자원 사용량을 샘플링해 {@link ServerMetricsHistory}에 쌓는다.
 *
 * <p>스케줄링은 {@code NotificationSchedulingConfig}의 {@code @EnableScheduling}으로 이미 활성이다. 이 작업이 추가되면서
 * {@code spring.task.scheduling.pool.size}를 함께 올렸다 — 외부 전송에 막힌 알림 재시도가 수집 스레드까지 점유하지 않게 한다.
 */
@Slf4j
@Component
public class ServerMetricsCollector {

    private final ServerMetricsProbe probe;
    private final ServerMetricsHistory history;
    private final Clock clock;

    @Autowired
    public ServerMetricsCollector(ServerMetricsProbe probe, ServerMetricsHistory history) {
        this(probe, history, Clock.systemUTC());
    }

    ServerMetricsCollector(ServerMetricsProbe probe, ServerMetricsHistory history, Clock clock) {
        this.probe = probe;
        this.history = history;
        this.clock = clock;
    }

    /** 주기 샘플링. 프로브 예외는 로그로 남기고 다음 주기를 기다린다 — 스케줄러 스레드를 죽이지 않는다. */
    @Scheduled(
            fixedRateString = "${app.server-metrics.sample-interval-ms:10000}",
            initialDelayString = "${app.server-metrics.initial-delay-ms:5000}")
    public void collect() {
        try {
            sampleNow();
        } catch (RuntimeException e) {
            log.warn("서버 자원 사용량 샘플링 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 즉시 한 번 샘플링해 이력에 기록하고 그 샘플을 돌려준다.
     *
     * @return 방금 기록한 샘플
     * @throws RuntimeException 프로브가 값을 읽지 못한 경우 (호출자가 표면화한다)
     */
    public ServerMetricsDto.Sample sampleNow() {
        ServerMetricsDto.Sample sample = probe.sample(Instant.now(clock));
        history.record(sample);
        return sample;
    }
}
