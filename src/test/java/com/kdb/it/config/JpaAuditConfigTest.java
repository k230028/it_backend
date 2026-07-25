package com.kdb.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JpaAuditConfig 단위 테스트
 *
 * <p>{@code auditorProvider()}가 비인증 요청(인증정보 없음/미인증/anonymous)에서는 {@link Optional#empty()}를 반환하고, 정상
 * 인증된 요청에서만 사번을 반환하는지 검증합니다.
 */
class JpaAuditConfigTest {

    private final JpaAuditConfig config = new JpaAuditConfig();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 정보가 없으면(null) Optional.empty()를 반환한다")
    void getCurrentAuditor_인증정보없음_빈값반환() {
        SecurityContextHolder.clearContext();

        Optional<String> auditor = config.auditorProvider().getCurrentAuditor();

        assertThat(auditor).isEmpty();
    }

    @Test
    @DisplayName("인증되지 않은(isAuthenticated=false) 토큰이면 Optional.empty()를 반환한다")
    void getCurrentAuditor_미인증토큰_빈값반환() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("E0001", "credentials"));

        Optional<String> auditor = config.auditorProvider().getCurrentAuditor();

        assertThat(auditor).isEmpty();
    }

    @Test
    @DisplayName("AnonymousAuthenticationToken이면 Optional.empty()를 반환한다")
    void getCurrentAuditor_익명인증토큰_빈값반환() {
        List<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new AnonymousAuthenticationToken("anonymousKey", "anonymousUser", authorities));

        Optional<String> auditor = config.auditorProvider().getCurrentAuditor();

        assertThat(auditor).isEmpty();
    }

    @Test
    @DisplayName("정상 인증된 사용자면 사번을 반환한다")
    void getCurrentAuditor_정상인증_사번반환() {
        List<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList("ROLE_USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "E0001", "credentials", authorities));

        Optional<String> auditor = config.auditorProvider().getCurrentAuditor();

        assertThat(auditor).contains("E0001");
    }
}
