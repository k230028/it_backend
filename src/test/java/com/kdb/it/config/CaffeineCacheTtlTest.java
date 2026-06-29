package com.kdb.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Caffeine expireAfterWrite 만료 동작 검증.
 *
 * <p>매뉴얼 {@link Ticker}로 가상 시간을 진행시켜, TTL 경과 후 엔트리가 만료(재로딩 대상)되는지를
 * 결정론적으로 확인합니다. 실제 sleep을 쓰지 않으므로 플래키하지 않습니다.</p>
 */
class CaffeineCacheTtlTest {

    @Test
    @DisplayName("expireAfterWrite TTL 경과 후 엔트리가 만료된다")
    void entryExpiresAfterTtl() {
        AtomicLong nanos = new AtomicLong(0);
        Ticker ticker = nanos::get;
        Cache<String, String> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .ticker(ticker)
                .build();

        cache.put("k", "v");
        assertThat(cache.getIfPresent("k")).isEqualTo("v");

        // 59초 경과 — 아직 유효
        nanos.set(Duration.ofSeconds(59).toNanos());
        cache.cleanUp();
        assertThat(cache.getIfPresent("k")).isEqualTo("v");

        // 61초 경과 — 만료
        nanos.set(Duration.ofSeconds(61).toNanos());
        cache.cleanUp();
        assertThat(cache.getIfPresent("k")).isNull();
    }
}
