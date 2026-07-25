package com.kdb.it.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JPA Auditing(감사) 설정 클래스
 *
 * <p>엔티티 생성/수정 시 자동으로 작성자 정보를 기록하는 JPA Auditing 기능을 활성화합니다.
 *
 * <p>이 설정을 통해 {@link com.kdb.it.domain.entity.BaseEntity}의 아래 필드가 자동으로 채워집니다:
 *
 * <ul>
 *   <li>{@code @CreatedBy} → {@code FST_ENR_USID}: 최초 생성자 사번
 *   <li>{@code @LastModifiedBy} → {@code LST_CHG_USID}: 마지막 수정자 사번
 *   <li>{@code @CreatedDate} → {@code FST_ENR_DTM}: 최초 생성일시 (자동)
 *   <li>{@code @LastModifiedDate} → {@code LST_CHG_DTM}: 마지막 수정일시 (자동)
 * </ul>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditConfig {

    /**
     * 현재 로그인한 사용자(사번)를 반환하는 AuditorAware 빈 등록.
     *
     * <p>JPA가 엔티티를 저장/수정할 때 이 빈을 호출하여 {@code @CreatedBy}, {@code @LastModifiedBy} 필드에 현재 사용자의 사번을
     * 자동으로 기록합니다.
     *
     * <p>동작 원리:
     *
     * <ol>
     *   <li>Spring Security의 {@link SecurityContextHolder}에서 현재 인증 정보 조회
     *   <li>인증 정보가 없거나(null), 미인증 상태이거나, {@link AnonymousAuthenticationToken}인 경우 → {@link
     *       Optional#empty()} 반환 (필드 미기록)
     *   <li>정상 인증된 경우 → {@code authentication.getName()}으로 JWT principal의 사번 반환
     * </ol>
     *
     * <p>로그인/SSO/토큰 회전처럼 이 Bean이 빈 값을 반환하는(anonymous) 흐름에서 감사자 기록이 필요한 엔티티는 이 Bean에 의존하지 않고 {@link
     * com.kdb.it.domain.entity.BaseEntity#initializeAuditActors(String)} / {@link
     * com.kdb.it.domain.entity.BaseEntity#changeAuditActor(String)}로 감사자를 명시적으로 채웁니다. 실제 적용 사례:
     * {@code Clognh.createLoginSuccess}/{@code createLoginFailure}/{@code createLogout}은 고정값 {@code
     * "SYSTEM"}을, {@code Crtokm.create}/{@code markRotated}는 토큰 소유자 사번을 채우며, {@code AuthService}의
     * 로그인·SSO·토큰 회전 경로가 이 팩토리들을 통해 Refresh Token/로그인 이력을 생성합니다. 이 Bean 내부에 "SYSTEM" 등 전역 기본값을 두지 않는
     * 이유는, 그런 기본값이 배치/스케줄러 등 실제로 감사자를 채워야 하는 경로의 누락을 감춰버리기 때문입니다.
     *
     * @return 현재 인증된 사용자의 사번을 담은 {@link Optional} (비인증 시 {@link Optional#empty()})
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            // SecurityContextHolder에서 현재 요청의 인증 정보 조회
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            boolean isNotAuthenticated =
                    authentication == null
                            || !authentication.isAuthenticated()
                            || authentication instanceof AnonymousAuthenticationToken;

            if (isNotAuthenticated) {
                // 비인증 요청(인증정보 없음/미인증/anonymous): Optional.empty() 반환
                // → JPA Auditing이 FST_ENR_USID/LST_CHG_USID 필드를 기록하지 않음 (null 유지)
                // anonymous 토큰은 isAuthenticated()가 true를 반환하므로 별도로 명시 검사한다.
                return Optional.empty();
            }

            // JWT 필터가 authentication.name에 설정한 사번(eno)을 반환
            return Optional.ofNullable(authentication.getName());
        };
    }
}
