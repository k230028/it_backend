package com.kdb.it.common.system.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import com.kdb.it.common.util.CookieUtil;

/**
 * JwtAuthenticationFilter 단위 테스트
 *
 * <p>
 * JWT 토큰 추출(쿠키/Authorization 헤더)·검증·SecurityContext 설정 흐름을 검증합니다.
 * HttpServletRequest·HttpServletResponse·FilterChain은 Mockito.mock()으로 대체합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 * <p>커버리지 60% 달성을 위해 추가 (2026-04-29)</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /** 각 테스트 후 SecurityContext 초기화 (테스트 간 상태 오염 방지) */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ───────────────────────────────────────────────────────
    // 토큰 없음 → 필터 체인 통과, 인증 미설정
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: 쿠키와 Authorization 헤더 모두 없으면 인증을 설정하지 않고 필터 체인을 통과한다")
    void doFilterInternal_토큰없음_인증설정안됨() throws Exception {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        given(request.getCookies()).willReturn(null);
        given(request.getHeader("Authorization")).willReturn(null);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then: validateToken은 호출되지 않아야 함
        verify(jwtUtil, never()).validateToken(anyString());
        // 필터 체인은 반드시 통과해야 함
        verify(filterChain).doFilter(request, response);
    }

    // ───────────────────────────────────────────────────────
    // 유효한 쿠키 토큰 → SecurityContext 인증 설정
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: 유효한 accessToken 쿠키가 있으면 SecurityContext에 인증 정보를 설정한다")
    void doFilterInternal_유효한쿠키_인증설정됨() throws Exception {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        String validToken = "valid.jwt.token";
        Cookie accessCookie = new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, validToken);
        given(request.getCookies()).willReturn(new Cookie[]{accessCookie});

        given(jwtUtil.validateToken(validToken)).willReturn(true);
        given(jwtUtil.getEnoFromToken(validToken)).willReturn("E10001");
        given(jwtUtil.getAthIdsFromToken(validToken)).willReturn(List.of("ITPZZ001"));
        given(jwtUtil.getBbrCFromToken(validToken)).willReturn("BBR001");
        given(request.getRequestURI()).willReturn("/api/test");

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then: SecurityContext에 인증이 설정되어야 함
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    // ───────────────────────────────────────────────────────
    // 유효한 Authorization Bearer 헤더 → SecurityContext 인증 설정
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: 쿠키가 없고 유효한 Bearer 토큰 헤더가 있으면 인증 정보를 설정한다")
    void doFilterInternal_유효한BearerHeader_인증설정됨() throws Exception {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        String validToken = "valid.bearer.token";
        given(request.getCookies()).willReturn(null);
        given(request.getHeader("Authorization")).willReturn("Bearer " + validToken);

        given(jwtUtil.validateToken(validToken)).willReturn(true);
        given(jwtUtil.getEnoFromToken(validToken)).willReturn("E10001");
        given(jwtUtil.getAthIdsFromToken(validToken)).willReturn(List.of("ITPAD001"));
        given(jwtUtil.getBbrCFromToken(validToken)).willReturn("BBR001");
        given(request.getRequestURI()).willReturn("/api/admin/test");

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    // ───────────────────────────────────────────────────────
    // 유효하지 않은 토큰 → 인증 미설정, 필터 체인 통과
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: 검증 실패 토큰이면 SecurityContext에 인증 정보를 설정하지 않고 필터 체인을 통과한다")
    void doFilterInternal_무효토큰_인증설정안됨() throws Exception {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        String invalidToken = "invalid.jwt.token";
        Cookie accessCookie = new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, invalidToken);
        given(request.getCookies()).willReturn(new Cookie[]{accessCookie});
        given(request.getRequestURI()).willReturn("/api/test");

        // 토큰 검증 실패 응답
        given(jwtUtil.validateToken(invalidToken)).willReturn(false);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then: SecurityContext에 인증이 설정되지 않아야 함
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    // ───────────────────────────────────────────────────────
    // JwtUtil 예외 발생 → 인증 미설정, 필터 체인 통과
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: JwtUtil에서 예외가 발생해도 필터 체인은 반드시 통과한다")
    void doFilterInternal_JwtUtil예외_필터체인통과() throws Exception {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        String token = "error.causing.token";
        Cookie accessCookie = new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, token);
        given(request.getCookies()).willReturn(new Cookie[]{accessCookie});
        given(request.getRequestURI()).willReturn("/api/test");

        // JwtUtil 내부 예외 발생 시뮬레이션
        given(jwtUtil.validateToken(token)).willThrow(new RuntimeException("JWT 파싱 오류"));

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then: 예외에도 불구하고 필터 체인은 반드시 통과해야 함
        verify(filterChain).doFilter(request, response);
    }

    // ───────────────────────────────────────────────────────
    // 다른 이름의 쿠키만 있는 경우 → Authorization 헤더 폴백
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: accessToken 쿠키가 없고 다른 이름의 쿠키만 있으면 Authorization 헤더로 폴백한다")
    void doFilterInternal_다른이름쿠키_Authorization헤더폴백() throws Exception {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        // accessToken이 아닌 다른 쿠키
        Cookie otherCookie = new Cookie("sessionId", "some-session");
        given(request.getCookies()).willReturn(new Cookie[]{otherCookie});
        given(request.getHeader("Authorization")).willReturn(null);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then: 토큰이 추출되지 않으므로 validateToken 미호출
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }
}
