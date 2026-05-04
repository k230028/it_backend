package com.kdb.it.common.system.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

/**
 * SSO 완료 컨트롤러 테스트
 *
 * <p>agentProc.jsp가 검증된 행번을 서버 세션에 남긴 경우에만 토큰을 발급하고,
 * 공개 쿼리 파라미터 eno만으로는 로그인할 수 없음을 검증합니다.</p>
 */
@WebMvcTest(SsoController.class)
@Import(TestSecurityConfig.class)
@TestPropertySource(properties = {
        "app.frontend-url=http://localhost:3000",
        "cors.allowed-origins=http://localhost:3000,http://localhost:3002",
        "app.sso.allow-direct-eno=false"
})
class SsoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CookieUtil cookieUtil;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/auth/sso/complete - SSO 검증 세션이 있으면 쿠키 발급 후 프론트로 복귀")
    void complete_검증세션있음_쿠키발급후리다이렉트() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("ssoVerifiedEno", "K150024");

        AuthDto.LoginResponse loginResponse = AuthDto.LoginResponse.builder()
                .eno("K150024")
                .empNm("홍길동")
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();

        ResponseCookie accessCookie = ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "access-token")
                .httpOnly(true).path("/").build();
        ResponseCookie refreshCookie = ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "refresh-token")
                .httpOnly(true).path("/api/auth").build();
        ResponseCookie userCookie = ResponseCookie.from("it-portal-user", "user")
                .path("/").build();

        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token")).willReturn(accessCookie);
        given(cookieUtil.createRefreshTokenCookie("refresh-token")).willReturn(refreshCookie);
        given(cookieUtil.createUserInfoCookie(loginResponse)).willReturn(userCookie);

        mockMvc.perform(get("/api/auth/sso/complete")
                        .session(session)
                        .param("next", "/info/projects")
                        .param("origin", "http://localhost:3002"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3002/info/projects"))
                .andExpect(header().exists("Set-Cookie"));

        verify(authService).issueSsoTokens("K150024");
    }

    @Test
    @DisplayName("GET /api/auth/sso/complete - eno 직접 전달은 기본 차단")
    void complete_eno직접전달_차단() throws Exception {
        mockMvc.perform(get("/api/auth/sso/complete")
                        .param("eno", "ITPAD001")
                        .param("origin", "http://localhost:3000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/login?error=sso"));

        verify(authService, never()).issueSsoTokens("ITPAD001");
    }
}
