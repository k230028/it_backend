package com.kdb.it.common.sso;

import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SSO 완료 컨트롤러 테스트
 *
 * <p>business(모의)/checkauth(실연동)가 검증된 행번을 세션에 남긴 경우에만 토큰을 발급하고,
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

    @MockitoBean
    private SsoAgentClient ssoAgentClient;

    /**
     * MockMvc로 로드되는 {@link SsoController} 빈에 주입할 {@link SsoProperties}를 제공합니다.
     *
     * <p>WebMvcTest 슬라이스는 {@code @EnableConfigurationProperties}를 로드하지 않으므로
     * 정적 중첩 {@code @TestConfiguration}으로 실제 인스턴스를 등록합니다. loginProc/complete
     * 테스트는 이 값을 사용하지 않으며, business/checkauth 단위 테스트는 별도 인스턴스를 직접 구성합니다.</p>
     */
    @TestConfiguration
    static class SsoTestConfig {
        @Bean
        SsoProperties ssoProperties() {
            return new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        }
    }

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
    @DisplayName("GET /sso/loginProc - checkauth 세션 결과를 complete 단계로 연결")
    void loginProc_성공세션_complete로연결() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("resultCode", "000000");
        session.setAttribute("resultData", "K150024");
        session.setAttribute("ssoNext", "/info/projects");
        session.setAttribute("ssoOrigin", "http://localhost:3002");

        mockMvc.perform(get("/sso/loginProc").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/sso/complete?next=%2Finfo%2Fprojects&origin=http%3A%2F%2Flocalhost%3A3002"));

        org.assertj.core.api.Assertions.assertThat(session.getAttribute("ssoVerifiedEno"))
                .isEqualTo("K150024");
    }

    @Test
    @DisplayName("GET /sso/loginProc - 세션이 없으면 complete 기본 경로로 이동")
    void loginProc_세션없음_complete기본경로() throws Exception {
        mockMvc.perform(get("/sso/loginProc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/sso/complete"));
    }

    @Test
    @DisplayName("GET /sso/loginProc - 성공 코드가 아니면 검증 사번을 세션에 저장하지 않는다")
    void loginProc_실패코드_검증사번저장안함() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("resultCode", "999999");
        session.setAttribute("resultData", "K150024");

        mockMvc.perform(get("/sso/loginProc").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/sso/complete"));

        assertThat(session.getAttribute("ssoVerifiedEno")).isNull();
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

    @Test
    @DisplayName("complete: 허용되지 않은 origin과 외부 next는 기본 프론트 URL 루트로 이동한다")
    void complete_허용되지않은Origin과외부Next_기본프론트루트() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("ssoVerifiedEno", "K150024");
        AuthDto.LoginResponse loginResponse = AuthDto.LoginResponse.builder()
                .eno("K150024")
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "access-token").build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "refresh-token").build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "user").build());

        mockMvc.perform(get("/api/auth/sso/complete")
                        .session(session)
                        .param("next", "https://evil.example")
                        .param("origin", "https://evil.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/"));
    }

    @Test
    @DisplayName("complete: 테스트 설정에서 직접 사번 전달이 허용되면 세션 없이 토큰을 발급한다")
    void complete_직접사번허용_토큰발급() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = new SsoController(authService, cookieUtil, ssoAgentClient, props);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(controller, "allowedOrigins", "http://localhost:3000");
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        AuthDto.LoginResponse loginResponse = AuthDto.LoginResponse.builder()
                .eno("K150024")
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "access-token").build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "refresh-token").build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "user").build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete("K150024", "/dashboard", "http://localhost:3000", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/dashboard");
        assertThat(response.getHeaders("Set-Cookie")).hasSize(3);
    }

    /**
     * mock 모드 SsoController를 직접 구성합니다 (next/origin 세션 저장 + 인증서버 통신 없이 mock 사번 주입 검증용).
     */
    private SsoController newController(SsoProperties props) {
        SsoController controller = new SsoController(authService, cookieUtil, ssoAgentClient, props);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(controller, "allowedOrigins", "http://localhost:3000,http://localhost:3002");
        ReflectionTestUtils.setField(controller, "allowDirectEno", false);
        return controller;
    }

    @Test
    @DisplayName("business: 모의 모드면 인증서버 통신 없이 mock 사번을 세션에 주입하고 loginProc로 이동")
    void business_모의모드_세션주입후loginProc() throws Exception {
        SsoProperties props = new SsoProperties(true, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.business("/info/projects", "http://localhost:3002", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/sso/loginProc");
        assertThat(request.getSession().getAttribute("resultCode")).isEqualTo("000000");
        assertThat(request.getSession().getAttribute("resultData")).isEqualTo("K140024");
        assertThat(request.getSession().getAttribute("ssoNext")).isEqualTo("/info/projects");
        assertThat(request.getSession().getAttribute("ssoOrigin")).isEqualTo("http://localhost:3002");
        verify(ssoAgentClient, never()).isServerAlive();
    }

    @Test
    @DisplayName("business: 실연동 모드에서 인증서버가 살아있으면 ESSO 로그인 페이지로 agentId와 함께 이동")
    void business_실연동_서버정상_로그인페이지이동() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.isServerAlive()).willReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.business("/info/projects", "http://localhost:3002", request, response);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://dintesso.kdb.co.kr:20443/login.html?agentId=3");
    }

    @Test
    @DisplayName("business: 실연동 모드에서 인증서버 통신 실패 시 수동 로그인으로 폴백")
    void business_실연동_서버다운_수동로그인폴백() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.isServerAlive()).willReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.business("/info/projects", "http://localhost:3002", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/login?error=sso");
    }

    @Test
    @DisplayName("checkauth: 토큰 검증 성공 시 resultData를 세션에 저장하고 loginProc로 이동")
    void checkauth_검증성공_세션저장후loginProc() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("secure-token", "sess-1", "127.0.0.1"))
                .willReturn(new SsoAgentClient.TokenAuthResult("000000", "OK", "K150024", null, false));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.checkauth("000000", "secure-token", "sess-1", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/sso/loginProc");
        assertThat(request.getSession().getAttribute("resultData")).isEqualTo("K150024");
        assertThat(request.getSession().getAttribute("resultCode")).isEqualTo("000000");
    }

    @Test
    @DisplayName("checkauth: secureToken 없는 비정상 호출이면 진입점(business)으로 복귀")
    void checkauth_비정상호출_business복귀() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.checkauth("000000", "", null, request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/sso/business");
        verify(ssoAgentClient, never()).authorize(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("checkauth: 토큰 검증 실패 코드면 수동 로그인으로 폴백")
    void checkauth_검증실패_수동로그인폴백() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("secure-token", "sess-1", "127.0.0.1"))
                .willReturn(new SsoAgentClient.TokenAuthResult("310017", "권한없음", "", null, false));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.checkauth("000000", "secure-token", "sess-1", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
    }
}
