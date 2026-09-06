package com.kdb.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;

/**
 * CacheConfig 단위 테스트.
 *
 * <p>내부 Caffeine 매니저가 5개 캐시(codesByCid/budgetPeriod/notificationUnreadCount/
 * tiptapMetadata/menuAuthMap)를 모두 보유하고 캐시별 TTL/최대크기 spec이 설계대로 적용됐는지, 그리고 애플리케이션이 쓰는
 * {@code @Primary} CacheManager가 트랜잭션 인지 프록시(MED-1)인지 검증합니다. Spring 컨텍스트나 DB 없이 빈을 직접 생성해 실행합니다.
 */
class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    /** 네이티브 TTL/캐시 이름 검사용 — 위임 대상인 실제 Caffeine 매니저 빈. */
    private final CaffeineCacheManager caffeineCacheManager = cacheConfig.caffeineCacheManager();

    /** 애플리케이션이 주입받는 @Primary 매니저 — TransactionAwareCacheManagerProxy로 감싼 빈. */
    private final CacheManager primaryCacheManager = cacheConfig.cacheManager(caffeineCacheManager);

    @Test
    @DisplayName("내부 위임 매니저는 Caffeine 구현이다")
    void caffeineManager_isCaffeine() {
        assertThat(caffeineCacheManager).isInstanceOf(CaffeineCacheManager.class);
    }

    @Test
    @DisplayName("애플리케이션 @Primary CacheManager는 트랜잭션 인지 프록시다 (MED-1)")
    void primaryCacheManager_isTransactionAware() {
        assertThat(primaryCacheManager).isInstanceOf(TransactionAwareCacheManagerProxy.class);
    }

    @Test
    @DisplayName("실제로 사용되는 5개 캐시만 등록한다")
    void registersAllFiveCaches() {
        // codesByType은 @Cacheable 생산자가 없어 채워지지 않는 등록이었으므로 제거했다.
        // 등록 목록은 실제 @Cacheable 이름과 1:1이어야 하며, 여기서 정확히 일치를 강제한다.
        assertThat(caffeineCacheManager.getCacheNames())
                .containsExactlyInAnyOrder(
                        "codesByCid",
                        "budgetPeriod",
                        "notificationUnreadCount",
                        "tiptapMetadata",
                        "menuAuthMap");
    }

    @Test
    @DisplayName("codesByCid/budgetPeriod는 60초 TTL — 다중 인스턴스 stale 한도")
    void staticCaches_sixtySecondTtl() {
        // @CacheEvict는 evict를 실행한 인스턴스에만 적용되므로, 다른 인스턴스의 stale 한도는 곧 TTL이다.
        // 공통코드·예산기간은 가장 널리 참조되는 준정적 데이터라 한도를 초 단위로 유지한다.
        assertExpireAfterWrite("codesByCid", Duration.ofSeconds(60));
        assertExpireAfterWrite("budgetPeriod", Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("menuAuthMap은 60초 TTL — 권한 회수가 다른 인스턴스에 늦게 반영되지 않도록")
    void menuAuthMap_sixtySecondTtl() {
        assertExpireAfterWrite("menuAuthMap", Duration.ofSeconds(60));
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
        CaffeineCache springCache = (CaffeineCache) caffeineCacheManager.getCache(cacheName);
        assertThat(springCache).as("캐시 %s 미등록", cacheName).isNotNull();
        Cache<Object, Object> nativeCache = springCache.getNativeCache();
        Policy.FixedExpiration<Object, Object> expiry =
                nativeCache
                        .policy()
                        .expireAfterWrite()
                        .orElseThrow(
                                () -> new AssertionError(cacheName + ": expireAfterWrite 미설정"));
        assertThat(expiry.getExpiresAfter()).isEqualTo(expected);
    }
}
