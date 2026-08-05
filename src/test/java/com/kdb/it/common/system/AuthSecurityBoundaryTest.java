package com.kdb.it.common.system;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.controller.AuthController;
import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증 API의 실제 보안 필터 체인 경계 테스트.
 *
 * <p>{@link com.kdb.it.config.TestSecurityConfig}로 우회하는 {@code AuthControllerTest}와 달리 운영 {@link
 * SecurityConfig}를 그대로 로드하여, 어떤 인증 엔드포인트가 익명 접근을 허용하는지 검증합니다.
 *
 * <p>로그아웃이 익명 접근을 허용해야 하는 이유: Access Token이 만료·삭제된 뒤에야 호출되는 정리 API이므로 인증을 요구하면 정작 필요한 순간에 401이 되어
 * 서버의 Refresh Token 패밀리가 남습니다. 소유자 확인은 {@link AuthController#logout}이 Refresh 쿠키 값으로 수행합니다.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JacksonConfig.class})
class AuthSecurityBoundaryTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthService authService;
    @MockitoBean private CookieUtil cookieUtil;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/auth/logout - 인증 없이도 필터 체인을 통과해 Refresh 쿠키로 패밀리를 폐기한다")
    void logout_익명요청_필터체인통과() throws Exception {
        given();

        mockMvc.perform(
                        post("/api/auth/logout")
                                .cookie(new Cookie("refreshToken", "refresh-value")))
                .andExpect(status().isOk());

        verify(authService).logoutByRefreshToken(eq("refresh-value"), eq(null), any(), any());
    }

    @Test
    @DisplayName("POST /api/auth/logout - Refresh 쿠키가 없으면 아무것도 폐기하지 않고 쿠키만 정리한다")
    void logout_쿠키없는익명요청_폐기없음() throws Exception {
        given();

        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isOk());

        verify(authService, org.mockito.Mockito.never())
                .logoutByRefreshToken(any(), any(), any(), any());
        verify(authService, org.mockito.Mockito.never()).logout(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/auth/session - 로그아웃과 달리 익명 요청은 401로 막힌다")
    void session_익명요청_401() throws Exception {
        mockMvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
    }

    /** 로그아웃 응답의 쿠키 삭제 헤더 생성을 위한 공통 스텁. */
    private void given() {
        ResponseCookie deleteCookie = ResponseCookie.from("dummy", "").maxAge(0).build();
        org.mockito.BDDMockito.given(cookieUtil.deleteAccessTokenCookie()).willReturn(deleteCookie);
        org.mockito.BDDMockito.given(cookieUtil.deleteRefreshTokenCookie())
                .willReturn(deleteCookie);
        org.mockito.BDDMockito.given(cookieUtil.deleteUserInfoCookie()).willReturn(deleteCookie);
    }
}
