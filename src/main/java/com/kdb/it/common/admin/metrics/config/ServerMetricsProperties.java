package com.kdb.it.common.admin.metrics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서버 자원 사용량 수집 설정 — 접두사 {@code app.server-metrics}.
 *
 * <p>피어 인스턴스 목록·내부 비밀값·타임아웃은 WAS 로그 뷰어와 같은 인프라 사실이므로 {@code app.was-log.*}를 그대로 공유한다.
 *
 * @param sampleIntervalMs 샘플링 주기(ms). {@code @Scheduled} 고정 주기와 링버퍼 용량 계산의 단일 출처
 * @param historyMinutes 인메모리 링버퍼가 보관하는 이력 길이(분)
 */
@ConfigurationProperties(prefix = "app.server-metrics")
public record ServerMetricsProperties(long sampleIntervalMs, int historyMinutes) {

    /** 누락·비정상 값 보정. */
    public ServerMetricsProperties {
        if (sampleIntervalMs <= 0) sampleIntervalMs = 10_000L;
        if (historyMinutes <= 0) historyMinutes = 60;
    }

    /** 링버퍼 용량(샘플 개수). 최소 1. */
    public int historyCapacity() {
        long capacity = historyMinutes * 60_000L / sampleIntervalMs;
        return (int) Math.max(1, Math.min(capacity, Integer.MAX_VALUE));
    }
}
