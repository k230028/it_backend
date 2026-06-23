package com.kdb.it.common.sso;

import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.TestSecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    /**
     * SSO 복귀 상태 쿠키(next/origin) mock 기본 스텁.
     * business()/complete()가 항상 이 메서드들을 호출하므로 NPE 방지용으로 lenient 스텁합니다.
     */
    @BeforeEach
    void stubSsoStateCookies() {
        lenient().when(cookieUtil.createSsoNextCookie(anyString()))
                .thenReturn(ResponseCookie.from(CookieUtil.SSO_NEXT_COOKIE, "n").path("/").build());
        lenient().when(cookieUtil.createSsoOriginCookie(anyString()))
                .thenReturn(ResponseCookie.from(CookieUtil.SSO_ORIGIN_COOKIE, "o").path("/").build());
        lenient().when(cookieUtil.deleteSsoNextCookie())
                .thenReturn(ResponseCookie.from(CookieUtil.SSO_NEXT_COOKIE, "").path("/").maxAge(0).build());
        lenient().when(cookieUtil.deleteSsoOriginCookie())
                .thenReturn(ResponseCookie.from(CookieUtil.SSO_ORIGIN_COOKIE, "").path("/").maxAge(0).build());
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
    @DisplayName("GET /sso/loginProc - 성공 세션이지만 빈 next/origin이면 complete 기본 경로로 이동")
    void loginProc_성공세션_빈nextOrigin_complete기본경로() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("resultCode", "000000");
        session.setAttribute("resultData", "K150024");
        session.setAttribute("ssoNext", " ");
        session.setAttribute("ssoOrigin", "");

        mockMvc.perform(get("/sso/loginProc").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/sso/complete"));

        assertThat(session.getAttribute("ssoVerifiedEno")).isEqualTo("K150024");
    }

    @Test
    @DisplayName("GET /sso/agentProc - loginProc와 동일하게 검증 사번을 세션에 승격하고 complete로 이동")
    void agentProc_GET_성공세션_complete로연결() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("resultCode", "000000");
        session.setAttribute("resultData", "K150024");
        session.setAttribute("ssoNext", "/info/projects");
        session.setAttribute("ssoOrigin", "http://localhost:3002");

        mockMvc.perform(get("/sso/agentProc").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/sso/complete?next=%2Finfo%2Fprojects&origin=http%3A%2F%2Flocalhost%3A3002"));

        assertThat(session.getAttribute("ssoVerifiedEno")).isEqualTo("K150024");
    }

    @Test
    @DisplayName("POST /sso/agentProc - CS 모드 saveToken 복귀(POST)도 동일하게 처리")
    void agentProc_POST_성공세션_complete로연결() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("resultCode", "000000");
        session.setAttribute("resultData", "K150024");

        mockMvc.perform(post("/sso/agentProc").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/sso/complete"));

        assertThat(session.getAttribute("ssoVerifiedEno")).isEqualTo("K150024");
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
        // 토큰 3개(access/refresh/user) + SSO 복귀 상태 쿠키 2개 삭제(next/origin)
        assertThat(response.getHeaders("Set-Cookie")).hasSize(5);
    }

    @Test
    @DisplayName("complete: next/origin이 없으면 기본 프론트 루트로 이동한다")
    void complete_nextOrigin없음_기본루트() throws Exception {
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

        controller.complete("K150024", null, null, request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
    }

    @Test
    @DisplayName("complete: next/origin 파라미터가 없으면 SSO 쿠키에서 최초 요청 URL을 복원한다")
    void complete_파라미터없음_쿠키에서원본URL복원() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = new SsoController(authService, cookieUtil, ssoAgentClient, props);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(controller, "allowedOrigins", "http://localhost:3000,http://localhost:3002");
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        AuthDto.LoginResponse loginResponse = AuthDto.LoginResponse.builder()
                .eno("K150024").accessToken("access-token").refreshToken("refresh-token").build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "access-token").build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "refresh-token").build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "user").build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        // 세션·파라미터엔 next/origin이 없고(ESSO 왕복에서 유실), SSO 시작 시 심은 쿠키에만 존재
        request.setCookies(
                new Cookie(CookieUtil.SSO_NEXT_COOKIE,
                        URLEncoder.encode("/info/projects/123", StandardCharsets.UTF_8)),
                new Cookie(CookieUtil.SSO_ORIGIN_COOKIE,
                        URLEncoder.encode("http://localhost:3002", StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete("K150024", null, null, request, response);

        // 파라미터가 비어도 쿠키에서 복원해 최초 요청 URL로 복귀해야 한다.
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/info/projects/123");
    }

    @Test
    @DisplayName("business: next/origin을 SSO 복귀 쿠키로도 발급한다(세션 유실 대비)")
    void business_복귀쿠키발급() throws Exception {
        SsoProperties props = new SsoProperties(true, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.business("/info/projects/9", "http://localhost:3002", request, response);

        verify(cookieUtil).createSsoNextCookie("/info/projects/9");
        verify(cookieUtil).createSsoOriginCookie("http://localhost:3002");
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
    @DisplayName("checkauth: CS 모드이면 saveToken.html로 POST 자동제출 폼을 렌더링한다(agentId/errCode/secureSessionId)")
    void checkauth_CS모드_saveToken_POST폼렌더링() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("secure-token", "sess-1", "127.0.0.1"))
                .willReturn(new SsoAgentClient.TokenAuthResult("000000", "OK", "K150024", null, true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.checkauth("000000", "secure-token", "sess-1", request, response);

        // GET 리다이렉트가 아니라 POST 폼 HTML이어야 한다.
        assertThat(response.getRedirectedUrl()).isNull();
        String html = response.getContentAsString();
        assertThat(html)
                .contains("method=\"post\"")
                .contains("action=\"https://dintesso.kdb.co.kr:20443/token/saveToken.html\"")
                .contains("name=\"agentId\" value=\"3\"")
                .contains("name=\"errCode\" value=\"000000\"")
                .contains("name=\"secureSessionId\" value=\"sess-1\"")
                // CSP sha256 해시와 일치해야 하는 정확한 제출 스크립트
                .contains("<script>document.forms[0].submit()</script>");
        assertThat(request.getSession().getAttribute("secureSessionId")).isEqualTo("sess-1");
    }

    @Test
    @DisplayName("checkauth: CS 모드 POST 폼은 secureSessionId의 HTML 특수문자를 이스케이프한다(XSS 방지)")
    void checkauth_CS모드_secureSessionId_이스케이프() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        String xss = "\"><script>alert(1)</script>";
        given(ssoAgentClient.authorize("secure-token", xss, "127.0.0.1"))
                .willReturn(new SsoAgentClient.TokenAuthResult("000000", "OK", "K150024", null, true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.checkauth("000000", "secure-token", xss, request, response);

        String html = response.getContentAsString();
        // 주입 시도가 원문 그대로 들어가면 안 된다.
        assertThat(html).doesNotContain("\"><script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("checkauth: secureToken 없는 비정상 호출이면 SSO 재시작 대신 수동 로그인으로 폴백(루프 차단)")
    void checkauth_비정상호출_수동로그인폴백() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.checkauth("000000", "", null, request, response);

        // /sso/business로 되돌리면 무한 루프가 되므로 프론트 수동 로그인으로 보낸다.
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
        verify(ssoAgentClient, never()).authorize(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("checkauth: 비정상 호출 시 origin 쿠키가 있으면 그 origin의 로그인으로 폴백한다")
    void checkauth_비정상호출_origin쿠키사용() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieUtil.SSO_ORIGIN_COOKIE,
                URLEncoder.encode("http://localhost:3002", StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.checkauth(null, null, null, request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/login?error=sso");
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

    @Test
    @DisplayName("complete: 토큰 발급 실패 시 로그인 오류 페이지로 이동한다")
    void complete_토큰발급실패_로그인오류() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("ssoVerifiedEno", "K150024");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(authService.issueSsoTokens("K150024")).willThrow(new IllegalStateException("사용자 없음"));

        controller.complete(null, "/info/projects", "http://localhost:3002", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/login?error=sso");
    }

    @Test
    @DisplayName("complete: 세션 사번이 공백이면 인증 실패로 처리한다")
    void complete_공백세션사번_로그인오류() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("ssoVerifiedEno", " ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete(null, "/info/projects", "http://localhost:3002", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/login?error=sso");
        verify(authService, never()).issueSsoTokens(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("ssoLogout: 모의 모드이면 세션 무효화 후 프론트 로그인으로 이동한다")
    void ssoLogout_모의모드_프론트로그인() throws Exception {
        SsoProperties props = new SsoProperties(true, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("dummy", "value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoLogout(request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login");
    }

    @Test
    @DisplayName("ssoLogout: 실연동 모드이면 ESSO 로그아웃 페이지로 이동한다")
    void ssoLogout_실연동_통합로그아웃() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024",
                "https://dintesso.kdb.co.kr:20443", "https://dintesso.kdb.co.kr:20443", "3", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoLogout(request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("https://dintesso.kdb.co.kr:20443/logout.html");
    }
}
