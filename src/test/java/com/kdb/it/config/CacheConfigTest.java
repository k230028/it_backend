package com.kdb.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

/**
 * CacheConfig 단위 테스트.
 *
 * <p>CacheManager가 Caffeine 기반인지, 6개 캐시(codesByType/codesByCid/budgetPeriod/
 * notificationUnreadCount/tiptapMetadata/menuAuthMap)를 모두 보유하는지, 캐시별 TTL/최대크기
 * spec이 설계대로 적용됐는지 검증합니다. Spring 컨텍스트나 DB 없이 빈을 직접 생성해 실행합니다.</p>
 */
class CacheConfigTest {

    private final CacheManager cacheManager = new CacheConfig().cacheManager();

    @Test
    @DisplayName("CacheManager는 Caffeine 구현이다")
    void cacheManager_isCaffeine() {
        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
    }

    @Test
    @DisplayName("기존 6개 캐시 이름을 모두 보유한다 (드롭 없음)")
    void registersAllSixCaches() {
        assertThat(cacheManager.getCacheNames())
                .containsExactlyInAnyOrder(
                        "codesByType", "codesByCid", "budgetPeriod",
                        "notificationUnreadCount", "tiptapMetadata", "menuAuthMap");
    }

    @Test
    @DisplayName("codesByCid/budgetPeriod/codesByType는 1시간 TTL")
    void staticCaches_oneHourTtl() {
        assertExpireAfterWrite("codesByCid", Duration.ofHours(1));
        assertExpireAfterWrite("budgetPeriod", Duration.ofHours(1));
        assertExpireAfterWrite("codesByType", Duration.ofHours(1));
    }

    @Test
    @DisplayName("menuAuthMap은 1시간 TTL")
    void menuAuthMap_oneHourTtl() {
        assertExpireAfterWrite("menuAuthMap", Duration.ofHours(1));
    }

    @Test
    @DisplayName("tiptapMetadata는 10분 TTL")
    void tiptapMetadata_tenMinuteTtl() {
        assertExpireAfterWrite("tiptapMetadata", Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("notificationUnreadCount는 60초 TTL")
    void notificationUnreadCount_sixtySecondTtl() {
        assertExpireAfterWrite("notificationUnreadCount", Duration.ofSeconds(60));
    }

    /** Caffeine 네이티브 캐시의 expireAfterWrite 설정값이 기대 Duration과 일치하는지 검증. */
    private void assertExpireAfterWrite(String cacheName, Duration expected) {
        CaffeineCache springCache = (CaffeineCache) cacheManager.getCache(cacheName);
        assertThat(springCache).as("캐시 %s 미등록", cacheName).isNotNull();
        Cache<Object, Object> nativeCache = springCache.getNativeCache();
        Policy.FixedExpiration<Object, Object> expiry =
                nativeCache.policy().expireAfterWrite()
                        .orElseThrow(() -> new AssertionError(cacheName + ": expireAfterWrite 미설정"));
        assertThat(expiry.getExpiresAfter()).isEqualTo(expected);
    }
}
