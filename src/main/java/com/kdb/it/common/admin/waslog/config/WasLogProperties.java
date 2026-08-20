package com.kdb.it.common.admin.waslog.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WAS 로그 뷰어 설정 — 접두사 {@code app.was-log}.
 *
 * @param bufferCapacity 링버퍼 용량. logback XML의 {@code <capacity>}와 같은 값을 유지한다
 * @param peers 인스턴스ID → 내부 호출용 base URL. 자기 자신을 포함해도 된다
 * @param internalSecret 피어 내부 엔드포인트 공유 비밀값. 비어 있으면 내부 컨트롤러가 등록되지 않는다
 * @param connectTimeoutMs 피어 연결 타임아웃(ms)
 * @param readTimeoutMs 피어 읽기 타임아웃(ms)
 */
@ConfigurationProperties(prefix = "app.was-log")
public record WasLogProperties(
        int bufferCapacity,
        Map<String, String> peers,
        String internalSecret,
        int connectTimeoutMs,
        int readTimeoutMs) {

    /** 누락 기본값 보정. */
    public WasLogProperties {
        if (bufferCapacity <= 0) bufferCapacity = 2000;
        if (peers == null) peers = Map.of();
        if (internalSecret == null) internalSecret = "";
        if (connectTimeoutMs <= 0) connectTimeoutMs = 1000;
        if (readTimeoutMs <= 0) readTimeoutMs = 3000;
    }
}
