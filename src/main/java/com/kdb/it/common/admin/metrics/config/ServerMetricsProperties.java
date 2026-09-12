package com.kdb.it.common.admin.metrics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서버 자원 사용량 수집 설정 — 접두사 {@code app.server-metrics}.
 *
 * <p>피어 인스턴스 목록·내부 비밀값·타임아웃은 WAS 로그 뷰어와 같은 인프라 사실이므로 {@code app.was-log.*}를 그대로 공유한다.
 *
 * <p>값이 없을 때만 기본값(10초·60분)을 적용한다. 0 이하로 명시된 값은 잘못된 설정이므로 기본값으로 숨기지 않고 기동을 차단한다.
 *
 * @param sampleIntervalMs 샘플링 주기(ms). {@code @Scheduled} 고정 주기와 링버퍼 용량 계산의 단일 출처. 누락 시 10000
 * @param historyMinutes 인메모리 링버퍼가 보관하는 이력 길이(분). 누락 시 60
 */
@ConfigurationProperties(prefix = "app.server-metrics")
public record ServerMetricsProperties(Long sampleIntervalMs, Integer historyMinutes) {

    /** 누락 시 적용하는 샘플링 주기(ms). */
    static final long DEFAULT_SAMPLE_INTERVAL_MS = 10_000L;

    /** 누락 시 적용하는 이력 길이(분). */
    static final int DEFAULT_HISTORY_MINUTES = 60;

    /**
     * 누락 값은 기본값으로 채우고, 0 이하로 명시된 값은 거부한다.
     *
     * @throws IllegalArgumentException {@code app.server-metrics.sample-interval-ms} 또는 {@code
     *     app.server-metrics.history-minutes}가 0 이하로 명시된 경우
     */
    public ServerMetricsProperties {
        if (sampleIntervalMs == null) {
            sampleIntervalMs = DEFAULT_SAMPLE_INTERVAL_MS;
        } else if (sampleIntervalMs <= 0) {
            throw new IllegalArgumentException(
                    "app.server-metrics.sample-interval-ms는 1 이상이어야 합니다: " + sampleIntervalMs);
        }
        if (historyMinutes == null) {
            historyMinutes = DEFAULT_HISTORY_MINUTES;
        } else if (historyMinutes <= 0) {
            throw new IllegalArgumentException(
                    "app.server-metrics.history-minutes는 1 이상이어야 합니다: " + historyMinutes);
        }
    }

    /** 링버퍼 용량(샘플 개수). 최소 1. */
    public int historyCapacity() {
        long capacity = historyMinutes * 60_000L / sampleIntervalMs;
        return (int) Math.max(1, Math.min(capacity, Integer.MAX_VALUE));
    }
}
