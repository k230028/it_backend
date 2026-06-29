package com.kdb.it.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 캐시 설정 클래스.
 *
 * <p>{@link org.springframework.cache.annotation.Cacheable @Cacheable} 등 Spring 캐시 추상화를 활성화하고
 * Caffeine 기반 캐시 매니저를 등록합니다. JPA Auditing({@link JpaAuditConfig})과 분리해 두어, 캐시만 필요한
 * 단위 테스트가 JPA 메타모델 없이도 컨텍스트를 로드할 수 있습니다.</p>
 *
 * <p><b>Caffeine 전환(P5/T13):</b> 기존 {@code ConcurrentMapCacheManager}는 TTL을 지원하지 않아 정합성을
 * 쓰기 시 즉시 evict(@CacheEvict)로만 보장했습니다. Caffeine으로 교체해 캐시별 TTL/최대크기를 부여하고,
 * 외부 변경·evict 누락에 대한 안전망(stale 한도)을 둡니다. 기존 {@code @Cacheable}/{@code @CacheEvict}
 * 의미(특히 공통코드 {@code codesByCid}/{@code budgetPeriod}의 쓰기 시 evict-all, CLAUDE §5.5.1)는
 * 그대로 유지되며, CacheManager 구현만 교체됩니다.</p>
 *
 * <p><b>캐시별 정책:</b></p>
 * <ul>
 *   <li>{@code codesByType}/{@code codesByCid}/{@code budgetPeriod}/{@code menuAuthMap} — 준정적 참조
 *       데이터. 1시간 TTL(쓰기 시 이미 {@code @CacheEvict}로 무효화하므로 TTL은 안전망).</li>
 *   <li>{@code tiptapMetadata} — 10분 TTL. 추가로 {@code ProjectService} create/update/delete가
 *       {@code @CacheEvict(allEntries=true)}로 즉시 무효화(사업 목록 변경 반영).</li>
 *   <li>{@code notificationUnreadCount} — 60초 TTL, 사용자(eno)별 키. 쓰기 경로에서 evict하지만 TTL로
 *       evict 누락 시에도 stale 한도를 60초로 제한.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 준정적 참조 데이터(공통코드/예산기간/메뉴권한) TTL — 쓰기 evict 보유, TTL은 안전망. */
    private static final Duration STATIC_TTL = Duration.ofHours(1);
    /** 준정적 캐시 최대 엔트리 수. 코드 그룹/연도/권한맵 키 수가 적어 넉넉히 둠. */
    private static final long STATIC_MAX_SIZE = 1_000L;

    /** Tiptap 변수 카탈로그 TTL — 쓰기 evict 보강 + 준정적 카탈로그라 10분 안전망. */
    private static final Duration TIPTAP_TTL = Duration.ofMinutes(10);
    /** Tiptap 카탈로그 캐시 최대 엔트리 수(키: 'ALL' 또는 부서코드별). */
    private static final long TIPTAP_MAX_SIZE = 500L;

    /** 알림 미읽음 카운트 TTL — 사용자별 고빈도 조회, evict 누락 대비 60초 stale 한도. */
    private static final Duration UNREAD_TTL = Duration.ofSeconds(60);
    /** 미읽음 카운트 캐시 최대 엔트리 수(사용자 약 3,000명 기준 여유). */
    private static final long UNREAD_MAX_SIZE = 10_000L;

    /**
     * Caffeine 기반 캐시 매니저.
     *
     * <p>캐시별로 {@link CaffeineCacheManager#registerCustomCache(String, com.github.benmanes.caffeine.cache.Cache)}
     * 로 명시 등록하여 각자의 TTL/최대크기를 강제합니다. 등록된 6개 캐시는 기존 {@code ConcurrentMapCacheManager}가
     * 등록하던 이름과 동일합니다(드롭 없음).</p>
     *
     * @return 6개 캐시가 per-cache spec으로 등록된 {@link CaffeineCacheManager}
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // 준정적 참조 데이터: 1시간 TTL (쓰기 시 @CacheEvict로 즉시 무효화 — §5.5.1)
        manager.registerCustomCache("codesByType", buildCache(STATIC_TTL, STATIC_MAX_SIZE));
        manager.registerCustomCache("codesByCid", buildCache(STATIC_TTL, STATIC_MAX_SIZE));
        manager.registerCustomCache("budgetPeriod", buildCache(STATIC_TTL, STATIC_MAX_SIZE));
        manager.registerCustomCache("menuAuthMap", buildCache(STATIC_TTL, STATIC_MAX_SIZE));

        // Tiptap 변수 카탈로그: 10분 TTL + ProjectService 쓰기 evict 보강
        manager.registerCustomCache("tiptapMetadata", buildCache(TIPTAP_TTL, TIPTAP_MAX_SIZE));

        // 알림 미읽음 카운트: 60초 TTL, 사용자(eno)별 키
        manager.registerCustomCache("notificationUnreadCount", buildCache(UNREAD_TTL, UNREAD_MAX_SIZE));

        return manager;
    }

    /** 주어진 TTL(expireAfterWrite)과 최대 엔트리 수로 Caffeine 네이티브 캐시를 생성합니다. */
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> buildCache(Duration ttl, long maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build();
    }
}
