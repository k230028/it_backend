package com.kdb.it.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * JPA Auditing(감사) 설정 클래스
 *
 * <p>엔티티 생성/수정 시 자동으로 작성자 정보를 기록하는 JPA Auditing 기능을 활성화합니다.</p>
 *
 * <p>이 설정을 통해 {@link com.kdb.it.domain.entity.BaseEntity}의
 * 아래 필드가 자동으로 채워집니다:</p>
 * <ul>
 *   <li>{@code @CreatedBy} → {@code FST_ENR_USID}: 최초 생성자 사번</li>
 *   <li>{@code @LastModifiedBy} → {@code LST_CHG_USID}: 마지막 수정자 사번</li>
 *   <li>{@code @CreatedDate} → {@code FST_ENR_DTM}: 최초 생성일시 (자동)</li>
 *   <li>{@code @LastModifiedDate} → {@code LST_CHG_DTM}: 마지막 수정일시 (자동)</li>
 * </ul>
 */
@Configuration
@EnableJpaAuditing
@EnableCaching
public class JpaAuditConfig {

    /** 인메모리 캐시 매니저 (공통코드 등 정적 데이터 캐싱용) */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("codesByType", "codesByCid", "budgetPeriod");
    }

    /**
     * 현재 로그인한 사용자(사번)를 반환하는 AuditorAware 빈 등록.
     *
     * <p>JPA가 엔티티를 저장/수정할 때 이 빈을 호출하여 {@code @CreatedBy},
     * {@code @LastModifiedBy} 필드에 현재 사용자의 사번을 자동으로 기록합니다.</p>
     *
     * <p>동작 원리:</p>
     * <ol>
     *   <li>Spring Security의 {@link SecurityContextHolder}에서 현재 인증 정보 조회</li>
     *   <li>인증되지 않은 경우(비로그인, anonymous) → {@link Optional#empty()} 반환 (필드 미기록)</li>
     *   <li>인증된 경우 → {@code authentication.getName()}으로 JWT principal의 사번 반환</li>
     * </ol>
     *
     * @return 현재 인증된 사용자의 사번을 담은 {@link Optional}
     *         (비로그인 시 {@link Optional#empty()})
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            // SecurityContextHolder에서 현재 요청의 인증 정보 조회
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                // 로그인되지 않은 경우(비인증 요청): Optional.empty() 반환
                // → JPA Auditing이 FST_ENR_USID/LST_CHG_USID 필드를 기록하지 않음 (null 유지)
                // 배치/스케줄러 등 비HTTP 컨텍스트에서는 "SYSTEM" 등 기본값 설정을 검토할 수 있습니다.
                return Optional.empty();
            }

            // JWT 필터가 authentication.name에 설정한 사번(eno)을 반환
            return Optional.ofNullable(authentication.getName());
        };
    }
}
