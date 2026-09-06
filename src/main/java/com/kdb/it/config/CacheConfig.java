package com.kdb.it.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 캐시 설정 클래스.
 *
 * <p>{@link org.springframework.cache.annotation.Cacheable @Cacheable} 등 Spring 캐시 추상화를 활성화하고
 * Caffeine 기반 캐시 매니저를 등록합니다. JPA Auditing({@link JpaAuditConfig})과 분리해 두어, 캐시만 필요한 단위 테스트가 JPA 메타모델
 * 없이도 컨텍스트를 로드할 수 있습니다.
 *
 * <p><b>Caffeine 전환(P5/T13):</b> 기존 {@code ConcurrentMapCacheManager}는 TTL을 지원하지 않아 정합성을 쓰기 시 즉시
 * evict(@CacheEvict)로만 보장했습니다. Caffeine으로 교체해 캐시별 TTL/최대크기를 부여하고, 외부 변경·evict 누락에 대한 안전망(stale 한도)을
 * 둡니다. 기존 {@code @Cacheable}/{@code @CacheEvict} 의미(특히 공통코드 {@code codesByCid}/{@code
 * budgetPeriod}의 쓰기 시 evict-all, CLAUDE §5.5.1)는 그대로 유지되며, CacheManager 구현만 교체됩니다.
 *
 * <p><b>트랜잭션 정합(P5 polish/MED-1):</b> 애플리케이션이 사용하는 캐시 매니저는 {@link
 * TransactionAwareCacheManagerProxy}로 감싸 {@code @CacheEvict}/{@code @CachePut}의 캐시 쓰기를 트랜잭션 <b>커밋
 * 후</b>로 지연합니다. 이렇게 하면 evict와 커밋 사이의 짧은 구간에 동시 읽기가 stale 데이터를 다시 캐싱하는 경합이 사라져, 공통코드({@code
 * codesByCid}/{@code budgetPeriod}) evict-on-write도 더 견고해집니다. 트랜잭션이 없으면(또는 활성 트랜잭션 미동기화 시) 캐시 쓰기는
 * 기존처럼 즉시 수행됩니다.
 *
 * <p><b>캐시별 정책:</b>
 *
 * <ul>
 *   <li>{@code codesByCid}/{@code budgetPeriod}/{@code menuAuthMap} — 준정적 참조 데이터. 60초 TTL(쓰기 시
 *       {@code @CacheEvict}로 즉시 무효화되지만, 그것은 evict를 실행한 인스턴스에만 적용되므로 TTL이 다른 인스턴스의 stale 한도가 된다).
 *   <li>{@code tiptapMetadata} — 10분 TTL. 추가로 {@code ProjectService} create/update/delete가
 *       {@code @CacheEvict(allEntries=true)}로 즉시 무효화(사업 목록 변경 반영).
 *   <li>{@code notificationUnreadCount} — 60초 TTL, 사용자(eno)별 키. 쓰기 경로에서 evict하지만 TTL로 evict 누락 시에도
 *       stale 한도를 60초로 제한.
 * </ul>
 *
 * <p><b>다중 인스턴스 정합(AP 2대):</b> 모든 캐시는 인스턴스 로컬이며 무효화를 인스턴스 간에 전파하지 않습니다. 따라서 각 캐시의 TTL이 곧 다른 인스턴스가 옛
 * 값을 보는 최대 시간입니다. 이 구조에서 stale 한도를 줄이는 수단은 TTL뿐이므로, 새 캐시를 추가할 때는 최대 크기와 함께 <b>"이 데이터가 다른 AP에서 몇 초까지
 * 옛 값이어도 되는가"</b>를 근거로 TTL을 정합니다. 변경을 즉시 전파해야 하는 데이터가 생기면 TTL 단축으로는 부족하며, WAS 로그·서버 메트릭이 쓰는 피어 내부
 * 엔드포인트({@code app.was-log.peers} + 공유 비밀 헤더) 방식으로 무효화를 브로드캐스트해야 합니다. 현재는 채택하지 않았습니다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 준정적 참조 데이터(공통코드/예산기간/메뉴권한) TTL.
     *
     * <p>이 값은 성능 튜닝 값이 아니라 <b>다중 인스턴스의 stale 한도</b>다. 캐시는 인스턴스 로컬이고 {@code @CacheEvict}는 evict를 실행한
     * 인스턴스에만 적용되므로, 관리자가 한쪽 AP에서 공통코드나 메뉴 권한을 바꿔도 반대편 AP는 이 TTL이 지나야 새 값을 읽는다. 과거 1시간이었으나 가장 널리
     * 참조되는 데이터의 한도가 알림 미읽음 수(60초)보다 길어 역전되어 있었고, 메뉴 권한 회수 반영도 그만큼 지연됐다.
     *
     * <p>대상 데이터가 작고(코드 그룹·연도·권한맵) 조회 1회로 재구성되므로 60초로 줄여도 DB 부하는 인스턴스당 분당 수 회 수준이다.
     */
    private static final Duration STATIC_TTL = Duration.ofSeconds(60);

    /** 준정적 캐시 최대 엔트리 수. 코드 그룹/연도/권한맵 키 수가 적어 넉넉히 둠. */
    private static final long STATIC_MAX_SIZE = 1_000L;

    /**
     * Tiptap 변수 카탈로그 TTL — write-expiry(마지막 재생성 후 고정 시간 경과 시 만료). 카탈로그는 활성 사업 목록으로 재구성되며,
     * ProjectService 쓰기 evict가 1차 무효화·이 TTL이 멀티 인스턴스 안전망.
     */
    private static final Duration TIPTAP_TTL = Duration.ofMinutes(10);

    /** Tiptap 카탈로그 캐시 최대 엔트리 수(키: 'ALL' 또는 부서코드별). */
    private static final long TIPTAP_MAX_SIZE = 500L;

    /**
     * 알림 미읽음 카운트 TTL — 단일 프로세스 인메모리 캐시. evict-on-write가 1차 무효화이며, TTL(60s)은 멀티 인스턴스 배포 시 evict 누락
     * 대비 안전망.
     */
    private static final Duration UNREAD_TTL = Duration.ofSeconds(60);

    /** 미읽음 카운트 캐시 최대 엔트리 수(사용자 약 3,000명 기준 여유). */
    private static final long UNREAD_MAX_SIZE = 10_000L;

    /**
     * 내부(실제) Caffeine 캐시 매니저.
     *
     * <p>캐시별로 {@link CaffeineCacheManager#registerCustomCache(String,
     * com.github.benmanes.caffeine.cache.Cache)} 로 명시 등록하여 각자의 TTL/최대크기를 강제합니다. 등록 목록은 실제
     * {@code @Cacheable} 이름과 1:1로 유지합니다 — 생산자 없는 이름을 등록해 두면 채워지지 않는 캐시가 설정에만 남아, 뒤에 읽는 사람이 그 데이터가
     * 캐시된다고 오해합니다. 이 빈은 {@link #cacheManager(CaffeineCacheManager)} 프록시의 내부 위임 대상이며, 테스트가 네이티브
     * TTL(expireAfterWrite)을 직접 검사할 때 주입받습니다.
     *
     * @return 5개 캐시가 per-cache spec으로 등록된 {@link CaffeineCacheManager}
     */
    @Bean
    public CaffeineCacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // 준정적 참조 데이터: 60초 TTL (쓰기 시 @CacheEvict로 즉시 무효화 — §5.5.1)
        // 공통코드·메뉴 권한 등 준정적 조회가 사용하는 캐시 이름을 애플리케이션과 맞춥니다.
        manager.registerCustomCache("codesByCid", buildCache(STATIC_TTL, STATIC_MAX_SIZE));
        manager.registerCustomCache("budgetPeriod", buildCache(STATIC_TTL, STATIC_MAX_SIZE));
        manager.registerCustomCache("menuAuthMap", buildCache(STATIC_TTL, STATIC_MAX_SIZE));

        // Tiptap 변수 카탈로그: 10분 TTL + ProjectService 쓰기 evict 보강
        manager.registerCustomCache("tiptapMetadata", buildCache(TIPTAP_TTL, TIPTAP_MAX_SIZE));

        // 알림 미읽음 카운트: 60초 TTL, 사용자(eno)별 키
        manager.registerCustomCache(
                "notificationUnreadCount", buildCache(UNREAD_TTL, UNREAD_MAX_SIZE));

        return manager;
    }

    /**
     * 애플리케이션이 사용하는 캐시 매니저({@link Primary}).
     *
     * <p>{@link TransactionAwareCacheManagerProxy}로 내부 Caffeine 매니저를 감싸, {@code @CacheEvict}/
     * {@code @CachePut}의 캐시 쓰기를 트랜잭션 커밋 후로 지연합니다(MED-1). evict와 커밋 사이 경합으로 stale 데이터가 재적재되는 위험을
     * 제거하며, 공통코드 evict-on-write(§5.5.1)도 함께 견고해집니다.
     *
     * @param caffeineCacheManager 위임 대상 내부 Caffeine 매니저
     * @return 트랜잭션 인지 캐시 매니저 프록시
     */
    @Bean
    @Primary
    public CacheManager cacheManager(CaffeineCacheManager caffeineCacheManager) {
        return new TransactionAwareCacheManagerProxy(caffeineCacheManager);
    }

    /** 주어진 TTL(expireAfterWrite)과 최대 엔트리 수로 Caffeine 네이티브 캐시를 생성합니다. */
    private Cache<Object, Object> buildCache(Duration ttl, long maxSize) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    }
}
