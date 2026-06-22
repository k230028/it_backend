package com.kdb.it.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 설정 클래스.
 *
 * <p>{@link org.springframework.cache.annotation.Cacheable @Cacheable} 등 Spring 캐시 추상화를 활성화하고
 * 인메모리 캐시 매니저를 등록합니다. JPA Auditing({@link JpaAuditConfig})과 분리해 두어, 캐시만 필요한
 * 단위 테스트가 JPA 메타모델 없이도 컨텍스트를 로드할 수 있습니다.</p>
 *
 * <p><b>TTL 한계:</b> {@link ConcurrentMapCacheManager}는 TTL을 지원하지 않습니다. 따라서 정합성은
 * 쓰기 시 즉시 evict(@CacheEvict)로 보장합니다. 다중 인스턴스/외부 변경 안전망으로 TTL이 필요하면
 * Caffeine 등 도입을 후속 과제로 검토합니다.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 인메모리 캐시 매니저 (공통코드/예산기간 등 정적 데이터 + 알림 카운트·tiptap·메뉴권한 캐싱용) */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "codesByType", "codesByCid", "budgetPeriod",
                "notificationUnreadCount", "tiptapMetadata", "menuAuthMap");
    }
}
