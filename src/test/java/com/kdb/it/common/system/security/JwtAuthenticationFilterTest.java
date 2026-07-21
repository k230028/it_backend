package com.kdb.it.common.system.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.util.CookieUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * JwtAuthenticationFilter 단위 테스트
 *
 * <p>JWT 토큰 추출(쿠키/Authorization 헤더)·용도 검증·SecurityContext 설정 흐름을 검증합니다.
 * HttpServletRequest·HttpServletResponse·FilterChain은 Mockito.mock()으로 대체합니다. Oracle DB 없이 실행됩니다.
 *
 * <p>커버리지 60% 달성을 위해 추가 (2026-04-29)
 *
 * <p>SEC-04: 필터는 Access 용도 allowlist 오버로드 {@code jwtUtil.validateToken(jwt,
 * JwtUtil.TOKEN_USE_ACCESS, true)}만 호출합니다. {@code access} 또는 클레임 없는 레거시 토큰만 인증되고 {@code
 * refresh}·unknown 용도는 거부됩니다. 쿠키·Bearer 두 경로가 동일한 오버로드를 사용하므로 스텁은 3-인자 형태로 통일합니다 (기존 단일 인자 스텁
 * 마이그레이션 완료).
 *
 * <p>Authorization Bearer 헤더 폴백은 {@code app.auth.allow-bearer-header}로 게이트됩니다 (운영 기본 false,
 * dev/swagger만 true). 헤더 폴백을 검증하는 테스트는 {@link ReflectionTestUtils}로 {@code allowBearerHeader}=true를
 * 설정합니다 (TASK 보안 #1).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;

    @InjectMocks private JwtAuthenticationFilter jwtAuthenticationFilter;

    /** 각 테스트 기본값: 헤더 폴백 비활성(운영 기본). 헤더 검증 테스트에서만 true로 오버라이드. */
    @BeforeEach
    void resetBearerFlag() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "allowBearerHeader", false);
    }

    /** 각 테스트 후 SecurityContext 초기화 (테스트 간 상태 오염 방지) */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ───────────────────────────────────────────────────────
    // 헬퍼: 요청 목 및 클레임 스텁
    // ───────────────────────────────────────────────────────

    /** accessToken 쿠키에 주어진 토큰 값을 담은 요청 목을 생성한다. */
    private HttpServletRequest cookieRequest(String tokenValue) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Cookie accessCookie = new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, tokenValue);
        given(request.getCookies()).willReturn(new Cookie[] {accessCookie});
        given(request.getRequestURI()).willReturn("/api/test");
        return request;
    }

    /** 쿠키 없이 Authorization Bearer 헤더에 주어진 토큰 값을 담은 요청 목을 생성한다. */
    private HttpServletRequest bearerRequest(String tokenValue) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getCookies()).willReturn(null);
        given(request.getHeader("Authorization")).willReturn("Bearer " + tokenValue);
        given(request.getRequestURI()).willReturn("/api/test");
        return request;
    }

    /** 인증 설정에 필요한 클레임 추출값을 스텁한다(허용 케이스 전용). */
    private void stubClaims(String tokenValue) {
        given(jwtUtil.getEnoFromToken(tokenValue)).willReturn("E10001");
        given(jwtUtil.getAthIdsFromToken(tokenValue)).willReturn(List.of("ITPZZ001"));
        given(jwtUtil.getBbrCFromToken(tokenValue)).willReturn("BBR001");
    }

    // ───────────────────────────────────────────────────────
    // Access 용도 allowlist 행렬 (SEC-04 Task 2)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("행렬1: accessToken 쿠키의 Access 토큰(오버로드 true) → 인증 설정")
    void 행렬_쿠키Access_인증설정() throws Exception {
        // given
        String jwt = "cookie.access.token";
        HttpServletRequest request = cookieRequest(jwt);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        given(jwtUtil.validateToken(jwt, JwtUtil.TOKEN_USE_ACCESS, true)).willReturn(true);
        stubClaims(jwt);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("행렬2: accessToken 쿠키의 Refresh 토큰(오버로드 false) → 미인증")
    void 행렬_쿠키Refresh_미인증() throws Exception {
        // given
        String jwt = "cookie.refresh.token";
        HttpServletRequest request = cookieRequest(jwt);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        given(jwtUtil.validateToken(jwt, JwtUtil.TOKEN_USE_ACCESS, true)).willReturn(false);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("행렬3: accessToken 쿠키의 unknown 용도(오버로드 false) → 미인증")
    void 행렬_쿠키Unknown_미인증() throws Exception {
        // given
        String jwt = "cookie.unknown.token";
        HttpServletRequest request = cookieRequest(jwt);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        given(jwtUtil.validateToken(jwt, JwtUtil.TOKEN_USE_ACCESS, true)).willReturn(false);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("행렬4: 레거시 Access 토큰(용도 없음, 오버로드 true) → 인증 설정")
    void 행렬_레거시Access_인증설정() throws Exception {
        // given
        String jwt = "legacy.access.token";
        HttpServletRequest request = cookieRequest(jwt);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        given(jwtUtil.validateToken(jwt, JwtUtil.TOKEN_USE_ACCESS, true)).willReturn(true);
        stubClaims(jwt);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("행렬5: Bearer Refresh 토큰(allowBearerHeader=true, 오버로드 false) → 미인증")
    void 행렬_BearerRefresh_미인증() throws Exception {
        // given
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "allowBearerHeader", true);
        String jwt = "bearer.refresh.token";
        HttpServletRequest request = bearerRequest(jwt);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        given(jwtUtil.validateToken(jwt, JwtUtil.TOKEN_USE_ACCESS, true)).willReturn(false);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("행렬6: Bearer Access 토큰(allowBearerHeader=true, 오버로드 true) → 인증 설정")
    void 행렬_BearerAccess_인증설정() throws Exception {
        // given
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "allowBearerHeader", true);
        String jwt = "bearer.access.token";
        HttpServletRequest request = bearerRequest(jwt);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        given(jwtUtil.validateToken(jwt, JwtUtil.TOKEN_USE_ACCESS, true)).willReturn(true);
        stubClaims(jwt);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("행렬7: Bearer Access 토큰(allowBearerHeader=false, 검증 호출 없음) → 미인증")
    void 행렬_BearerAccess_플래그false_미인증() throws Exception {
        // given
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "allowBearerHeader", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bearer.access.token");
        FilterChain filterChain = mock(FilterChain.class);

        // when
        jwtAuthenticationFilter.doFilter(request, new MockHttpServletResponse(), filterChain);

        // then: 헤더 폴백 비활성이므로 토큰이 추출되지 않아 용도 검증이 호출되지 않는다
        verify(jwtUtil, never()).validateToken(anyString(), anyString(), anyBoolean());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain)
                .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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

        // then: 용도 검증 오버로드는 호출되지 않아야 함
        verify(jwtUtil, never()).validateToken(anyString(), anyString(), anyBoolean());
        // 필터 체인은 반드시 통과해야 함
        verify(filterChain).doFilter(request, response);
    }

    // ───────────────────────────────────────────────────────
    // JwtUtil 예외 발생 → 인증 미설정, 필터 체인 통과
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: JwtUtil에서 예외가 발생해도 필터 체인은 반드시 통과한다")
    void doFilterInternal_JwtUtil예외_필터체인통과() throws Exception {
        // given
        String token = "error.causing.token";
        HttpServletRequest request = cookieRequest(token);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        // JwtUtil 내부 예외 발생 시뮬레이션 (용도 검증 오버로드)
        given(jwtUtil.validateToken(token, JwtUtil.TOKEN_USE_ACCESS, true))
                .willThrow(new RuntimeException("JWT 파싱 오류"));

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then: 예외에도 불구하고 필터 체인은 반드시 통과해야 함
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    // ───────────────────────────────────────────────────────
    // 다른 이름의 쿠키만 있는 경우 → Authorization 헤더 폴백 (플래그 true)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName(
            "doFilterInternal: 헤더 폴백 허용(true) + accessToken 쿠키 없음 + 다른 쿠키만이면 Authorization 헤더로 폴백한다")
    void doFilterInternal_다른이름쿠키_Authorization헤더폴백() throws Exception {
        // given
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "allowBearerHeader", true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        // accessToken이 아닌 다른 쿠키
        Cookie otherCookie = new Cookie("sessionId", "some-session");
        given(request.getCookies()).willReturn(new Cookie[] {otherCookie});
        given(request.getHeader("Authorization")).willReturn(null);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then: 토큰이 추출되지 않으므로 용도 검증 오버로드 미호출
        verify(jwtUtil, never()).validateToken(anyString(), anyString(), anyBoolean());
        verify(filterChain).doFilter(request, response);
    }

    // ───────────────────────────────────────────────────────
    // 헤더 폴백 게이트 (TASK 보안 #1) — 오버로드 1회 호출 검증
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("allowBearerHeader=true 면 Authorization Bearer 토큰을 추출·용도 검증한다(오버로드 1회 호출)")
    void bearerUsed_whenFlagTrue() throws Exception {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "allowBearerHeader", true);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer some.jwt.token");
        given(jwtUtil.validateToken("some.jwt.token", JwtUtil.TOKEN_USE_ACCESS, true))
                .willReturn(false);
        FilterChain chain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilter(req, new MockHttpServletResponse(), chain);

        verify(jwtUtil, times(1)).validateToken("some.jwt.token", JwtUtil.TOKEN_USE_ACCESS, true);
        verify(chain)
                .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
