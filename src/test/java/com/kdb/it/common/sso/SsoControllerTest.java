package com.kdb.it.common.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.TestSecurityConfig;
import jakarta.servlet.http.Cookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SSO 완료 컨트롤러 테스트
 *
 * <p>business(모의)/checkauth(실연동)가 검증된 행번을 세션에 남긴 경우에만 토큰을 발급하고, 공개 쿼리 파라미터 eno만으로는 로그인할 수 없음을
 * 검증합니다.
 */
@WebMvcTest(SsoController.class)
@Import(TestSecurityConfig.class)
@TestPropertySource(
        properties = {
            "app.frontend-url=http://localhost:3000",
            "cors.allowed-origins=http://localhost:3000,http://localhost:3002",
            "app.sso.allow-direct-eno=false"
        })
class SsoControllerTest {

    private static final String FORWARDED_IP_SENTINEL = "203.0.113.77";
    private static final String REMOTE_ADDR_SENTINEL = "198.51.100.42";
    private static final String TARGET_TOKEN_SENTINEL = "token-RAW-7Q2";
    private static final String TARGET_SESSION_SENTINEL = "session-RAW-8R3";
    private static final String ORIGIN_SENTINEL = "origin-RAW-5T9";
    private static final String RESULT_CODE_SENTINEL = "result-code-RAW-4U6";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthService authService;

    @MockitoBean private CookieUtil cookieUtil;

    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @MockitoBean private SsoAgentClient ssoAgentClient;

    /**
     * MockMvc로 로드되는 {@link SsoController} 빈에 주입할 {@link SsoProperties}를 제공합니다.
     *
     * <p>WebMvcTest 슬라이스는 {@code @EnableConfigurationProperties}를 로드하지 않으므로 정적 중첩
     * {@code @TestConfiguration}으로 실제 인스턴스를 등록합니다. loginProc/complete 테스트는 이 값을 사용하지 않으며,
     * business/checkauth 단위 테스트는 별도 인스턴스를 직접 구성합니다.
     */
    @TestConfiguration
    static class SsoTestConfig {
        @Bean
        SsoProperties ssoProperties() {
            return new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        }
    }

    /**
     * SSO 복귀 상태 쿠키(next/origin) mock 기본 스텁. business()/complete()가 항상 이 메서드들을 호출하므로 NPE 방지용으로
     * lenient 스텁합니다.
     */
    @BeforeEach
    void stubSsoStateCookies() {
        lenient()
                .when(cookieUtil.createSsoNextCookie(anyString()))
                .thenReturn(ResponseCookie.from(CookieUtil.SSO_NEXT_COOKIE, "n").path("/").build());
        lenient()
                .when(cookieUtil.createSsoOriginCookie(anyString()))
                .thenReturn(
                        ResponseCookie.from(CookieUtil.SSO_ORIGIN_COOKIE, "o").path("/").build());
        lenient()
                .when(cookieUtil.deleteSsoNextCookie())
                .thenReturn(
                        ResponseCookie.from(CookieUtil.SSO_NEXT_COOKIE, "")
                                .path("/")
                                .maxAge(0)
                                .build());
        lenient()
                .when(cookieUtil.deleteSsoOriginCookie())
                .thenReturn(
                        ResponseCookie.from(CookieUtil.SSO_ORIGIN_COOKIE, "")
                                .path("/")
                                .maxAge(0)
                                .build());
        // SSO 검증 완료 쿠키: 서명 토큰은 "signed:<사번>" 형태로 흉내 내고, 복원은 테스트마다 명시 스텁한다.
        lenient()
                .when(jwtUtil.generateSsoVerifiedToken(anyString()))
                .thenAnswer(invocation -> "signed:" + invocation.getArgument(0));
        lenient().when(jwtUtil.resolveSsoVerifiedEno(any())).thenReturn(Optional.empty());
        lenient()
                .when(cookieUtil.createSsoVerifiedCookie(anyString()))
                .thenAnswer(
                        invocation ->
                                ResponseCookie.from(
                                                CookieUtil.SSO_VERIFIED_COOKIE,
                                                invocation.getArgument(0))
                                        .path("/api/auth/sso")
                                        .maxAge(60)
                                        .build());
        lenient()
                .when(cookieUtil.deleteSsoVerifiedCookie())
                .thenReturn(
                        ResponseCookie.from(CookieUtil.SSO_VERIFIED_COOKIE, "")
                                .path("/api/auth/sso")
                                .maxAge(0)
                                .build());
    }

    /** {@code sso-verified} 쿠키 원문. {@link #stubVerified(String)}와 짝으로 사용한다. */
    private static final String VERIFIED_TOKEN = "verified-token";

    private static Cookie verifiedCookie() {
        return new Cookie(CookieUtil.SSO_VERIFIED_COOKIE, VERIFIED_TOKEN);
    }

    private static Cookie ssoStateCookie(String name, String value) {
        return new Cookie(name, URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    /** {@code sso-verified} 쿠키가 주어진 사번으로 검증되도록 스텁한다. */
    private void stubVerified(String eno) {
        given(jwtUtil.resolveSsoVerifiedEno(VERIFIED_TOKEN)).willReturn(Optional.of(eno));
    }

    private static boolean deletesSsoVerifiedCookie(String setCookie) {
        return setCookie.startsWith(CookieUtil.SSO_VERIFIED_COOKIE + "=")
                && setCookie.contains("Max-Age=0");
    }

    private static boolean issuesSsoVerifiedCookie(String setCookie, String eno) {
        return setCookie.startsWith(CookieUtil.SSO_VERIFIED_COOKIE + "=signed:" + eno)
                && setCookie.contains("Max-Age=60");
    }

    private List<String> formattedMessages(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static Stream<String> unsafeResultCodes() {
        return Stream.of(
                RESULT_CODE_SENTINEL + "\rCR",
                RESULT_CODE_SENTINEL + "\nLF",
                RESULT_CODE_SENTINEL + "\tTAB",
                RESULT_CODE_SENTINEL + "\u001bESC\u0000NUL",
                RESULT_CODE_SENTINEL + "\u2028LS\u2029PS",
                RESULT_CODE_SENTINEL + "A".repeat(40),
                RESULT_CODE_SENTINEL + "{}%n%s");
    }

    @Test
    @DisplayName("GET /api/auth/sso/complete - SSO 검증 쿠키가 유효하면 토큰 쿠키 발급 후 프론트로 복귀")
    void complete_검증쿠키유효_쿠키발급후리다이렉트() throws Exception {
        stubVerified("K150024");

        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .empNm("홍길동")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();

        ResponseCookie accessCookie =
                ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "access-token")
                        .httpOnly(true)
                        .path("/")
                        .build();
        ResponseCookie refreshCookie =
                ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "refresh-token")
                        .httpOnly(true)
                        .path("/api/auth")
                        .build();
        ResponseCookie userCookie = ResponseCookie.from("it-portal-user", "user").path("/").build();

        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token")).willReturn(accessCookie);
        given(cookieUtil.createRefreshTokenCookie("refresh-token")).willReturn(refreshCookie);
        given(cookieUtil.createUserInfoCookie(loginResponse)).willReturn(userCookie);

        mockMvc.perform(
                        get("/api/auth/sso/complete")
                                .cookie(verifiedCookie())
                                .param("next", "/info/projects")
                                .param("origin", "http://localhost:3002"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3002/info/projects"))
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(request().sessionAttributeDoesNotExist("ssoVerifiedEno"));

        verify(authService).issueSsoTokens("K150024");
    }

    @Test
    @DisplayName("GET /sso/loginProc - 복귀 상태 쿠키(next/origin)를 complete 쿼리로 넘긴다")
    void loginProc_복귀쿠키_complete로연결() throws Exception {
        mockMvc.perform(
                        get("/sso/loginProc")
                                .cookie(
                                        ssoStateCookie(
                                                CookieUtil.SSO_NEXT_COOKIE, "/info/projects"),
                                        ssoStateCookie(
                                                CookieUtil.SSO_ORIGIN_COOKIE,
                                                "http://localhost:3002")))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        redirectedUrl(
                                "/api/auth/sso/complete?next=%2Finfo%2Fprojects&origin=http%3A%2F%2Flocalhost%3A3002"))
                .andExpect(request().sessionAttributeDoesNotExist("ssoVerifiedEno"));
    }

    @Test
    @DisplayName("loginProc: 복귀 쿠키의 토큰·세션·origin 원문을 로그에 남기지 않고 리다이렉트한다")
    void loginProc_복귀쿠키민감정보로그미노출_리다이렉트유지() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        String next =
                "/x?secureToken="
                        + TARGET_TOKEN_SENTINEL
                        + "&secureSessionId="
                        + TARGET_SESSION_SENTINEL;
        String origin = "https://" + ORIGIN_SENTINEL + ".example";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                ssoStateCookie(CookieUtil.SSO_NEXT_COOKIE, next),
                ssoStateCookie(CookieUtil.SSO_ORIGIN_COOKIE, origin));
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(SsoController.class);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        try {
            controller.loginProc(response, request);

            assertThat(response.getRedirectedUrl())
                    .isEqualTo(
                            "/api/auth/sso/complete?next=%2Fx%3FsecureToken%3Dtoken-RAW-7Q2%26secureSessionId%3Dsession-RAW-8R3&origin=https%3A%2F%2Forigin-RAW-5T9.example");
            assertThat(formattedMessages(appender))
                    .noneMatch(message -> message.contains(TARGET_TOKEN_SENTINEL))
                    .noneMatch(message -> message.contains(TARGET_SESSION_SENTINEL))
                    .noneMatch(message -> message.contains(ORIGIN_SENTINEL));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    @Test
    @DisplayName("complete는 성공 시 SSO 검증 쿠키를 삭제해 브라우저가 다시 보내지 못하게 한다")
    void complete_성공_검증쿠키삭제() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        stubVerified("K150024");
        stubSsoTokenIssue();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(verifiedCookie());
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete(null, "/dashboard", null, request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/dashboard");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(SsoControllerTest::deletesSsoVerifiedCookie);
        assertThat(request.getSession(false)).isNull();
        verify(authService).issueSsoTokens("K150024");
    }

    @Test
    @DisplayName("complete는 검증 실패 시에도 SSO 검증 쿠키를 삭제하고 서버 세션을 만들지 않는다")
    void complete_검증실패_검증쿠키삭제() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(verifiedCookie());
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete(null, "/info/projects", null, request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(SsoControllerTest::deletesSsoVerifiedCookie);
        assertThat(request.getSession(false)).isNull();
        verify(authService, never()).issueSsoTokens(anyString());
    }

    @Test
    @DisplayName("GET /sso/loginProc - 복귀 쿠키가 없으면 complete 기본 경로로 이동")
    void loginProc_복귀쿠키없음_complete기본경로() throws Exception {
        mockMvc.perform(get("/sso/loginProc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/sso/complete"));
    }

    @Test
    @DisplayName("GET /sso/loginProc - 빈 next/origin 쿠키면 complete 기본 경로로 이동")
    void loginProc_빈nextOrigin쿠키_complete기본경로() throws Exception {
        mockMvc.perform(
                        get("/sso/loginProc")
                                .cookie(
                                        ssoStateCookie(CookieUtil.SSO_NEXT_COOKIE, " "),
                                        ssoStateCookie(CookieUtil.SSO_ORIGIN_COOKIE, "")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/sso/complete"));
    }

    @Test
    @DisplayName("GET /sso/agentProc - loginProc와 동일하게 복귀 쿠키를 complete 쿼리로 넘긴다")
    void agentProc_GET_복귀쿠키_complete로연결() throws Exception {
        mockMvc.perform(
                        get("/sso/agentProc")
                                .cookie(
                                        ssoStateCookie(
                                                CookieUtil.SSO_NEXT_COOKIE, "/info/projects"),
                                        ssoStateCookie(
                                                CookieUtil.SSO_ORIGIN_COOKIE,
                                                "http://localhost:3002")))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        redirectedUrl(
                                "/api/auth/sso/complete?next=%2Finfo%2Fprojects&origin=http%3A%2F%2Flocalhost%3A3002"));
    }

    @Test
    @DisplayName("POST /sso/agentProc - CS 모드 saveToken 복귀(POST)도 동일하게 처리")
    void agentProc_POST_complete로연결() throws Exception {
        mockMvc.perform(post("/sso/agentProc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/sso/complete"));
    }

    @Test
    @DisplayName("GET /api/auth/sso/complete - eno 직접 전달은 기본 차단")
    void complete_eno직접전달_차단() throws Exception {
        mockMvc.perform(
                        get("/api/auth/sso/complete")
                                .param("eno", "ITPAD001")
                                .param("origin", "http://localhost:3000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/login?error=sso"));

        verify(authService, never()).issueSsoTokens("ITPAD001");
    }

    @Test
    @DisplayName("complete: 허용되지 않은 origin과 외부 next는 기본 프론트 URL 루트로 이동한다")
    void complete_허용되지않은Origin과외부Next_기본프론트루트() throws Exception {
        stubVerified("K150024");
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(
                        ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "access-token")
                                .build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(
                        ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "refresh-token")
                                .build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "user").build());

        mockMvc.perform(
                        get("/api/auth/sso/complete")
                                .cookie(verifiedCookie())
                                .param("next", "https://evil.example")
                                .param("origin", "https://evil.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/"));
    }

    @Test
    @DisplayName("complete: 테스트 설정에서 직접 사번 전달이 허용되면 세션 없이 토큰을 발급한다")
    void complete_직접사번허용_토큰발급() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller =
                new SsoController(authService, cookieUtil, jwtUtil, ssoAgentClient, props);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(controller, "allowedOrigins", "http://localhost:3000");
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(
                        ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "access-token")
                                .build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(
                        ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "refresh-token")
                                .build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "user").build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete("K150024", "/dashboard", "http://localhost:3000", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/dashboard");
        // 토큰 3개(access/refresh/user) + SSO 상태 쿠키 3개 삭제(verified/next/origin)
        assertThat(response.getHeaders("Set-Cookie")).hasSize(6);
    }

    @Test
    @DisplayName("complete: next/origin이 없으면 기본 프론트 루트로 이동한다")
    void complete_nextOrigin없음_기본루트() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller =
                new SsoController(authService, cookieUtil, jwtUtil, ssoAgentClient, props);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(controller, "allowedOrigins", "http://localhost:3000");
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(
                        ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "access-token")
                                .build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(
                        ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "refresh-token")
                                .build());
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
        SsoController controller =
                new SsoController(authService, cookieUtil, jwtUtil, ssoAgentClient, props);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(
                controller, "allowedOrigins", "http://localhost:3000,http://localhost:3002");
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(
                        ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "access-token")
                                .build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(
                        ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "refresh-token")
                                .build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "user").build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        // 세션·파라미터엔 next/origin이 없고(ESSO 왕복에서 유실), SSO 시작 시 심은 쿠키에만 존재
        request.setCookies(
                new Cookie(
                        CookieUtil.SSO_NEXT_COOKIE,
                        URLEncoder.encode("/info/projects/123", StandardCharsets.UTF_8)),
                new Cookie(
                        CookieUtil.SSO_ORIGIN_COOKIE,
                        URLEncoder.encode("http://localhost:3002", StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete("K150024", null, null, request, response);

        // 파라미터가 비어도 쿠키에서 복원해 최초 요청 URL로 복귀해야 한다.
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3002/info/projects/123");
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

    @Test
    @DisplayName("business: 외부 프로토콜 상대 next는 세션과 쿠키에 저장하지 않는다")
    void business_프로토콜상대next_세션쿠키저장안함() throws Exception {
        SsoProperties props = new SsoProperties(true, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.business("//evil.example", "http://localhost:3002", request, response);

        assertThat(request.getSession(false)).isNull();
        verify(cookieUtil, never()).createSsoNextCookie(anyString());
    }

    @Test
    @DisplayName("business: 새 next가 안전하지 않으면 이전 복귀 상태를 제거하고 루트 복귀로 초기화한다")
    void business_안전하지않은새next_이전복귀상태제거() throws Exception {
        SsoProperties props = new SsoProperties(true, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(CookieUtil.SSO_NEXT_COOKIE, "%2Finfo%2Fprojects%2Fold"),
                new Cookie(CookieUtil.SSO_ORIGIN_COOKIE, "http%3A%2F%2Flocalhost%3A3002"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.business("//evil.example", null, request, response);

        // 모의 모드는 검증 쿠키 1개를 더 발급하므로 삭제 쿠키(next/origin)는 그중 2개다.
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .filteredOn(cookie -> cookie.contains("Max-Age=0"))
                .hasSize(2)
                .anyMatch(cookie -> cookie.startsWith(CookieUtil.SSO_NEXT_COOKIE + "="))
                .anyMatch(cookie -> cookie.startsWith(CookieUtil.SSO_ORIGIN_COOKIE + "="));
        verify(cookieUtil, never()).createSsoNextCookie(anyString());
        verify(cookieUtil, never()).createSsoOriginCookie(anyString());
    }

    @Test
    @DisplayName("complete: 프로토콜 상대 next는 허용 origin의 루트로 이동한다")
    void complete_프로토콜상대next_루트로이동() throws Exception {
        SsoController controller = directEnoController();
        stubSsoTokenIssue();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete(
                "K150024",
                "//evil.example",
                "http://localhost:3000",
                new MockHttpServletRequest(),
                response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
    }

    @Test
    @DisplayName("complete: 역슬래시 외부 경로 next는 허용 origin의 루트로 이동한다")
    void complete_역슬래시외부경로next_루트로이동() throws Exception {
        SsoController controller = directEnoController();
        stubSsoTokenIssue();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete(
                "K150024",
                "/\\evil.example",
                "http://localhost:3000",
                new MockHttpServletRequest(),
                response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
    }

    @Test
    @DisplayName("complete: 로그인 재진입 next는 허용 origin의 루트로 이동한다")
    void complete_로그인재진입next_루트로이동() throws Exception {
        SsoController controller = directEnoController();
        stubSsoTokenIssue();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete(
                "K150024",
                "/login?next=/admin",
                "http://localhost:3000",
                new MockHttpServletRequest(),
                response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
    }

    @Test
    @DisplayName("complete: 프론트 URL과 허용 origin이 없으면 외부 next 없이 백엔드 루트로 이동한다")
    void complete_프론트URL미설정_프로토콜상대next_백엔드루트이동() throws Exception {
        SsoController controller = directEnoController();
        ReflectionTestUtils.setField(controller, "frontendUrl", "");
        ReflectionTestUtils.setField(controller, "allowedOrigins", "");
        stubSsoTokenIssue();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete(
                "K150024", "//evil.example", null, new MockHttpServletRequest(), response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    @DisplayName("complete: 허용 origin 설정이 null이면 기본 프론트 URL로 이동한다")
    void complete_nullAllowedOrigins_기본프론트URL로이동() throws Exception {
        SsoController controller = directEnoController();
        ReflectionTestUtils.setField(controller, "allowedOrigins", null);
        stubSsoTokenIssue();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete(
                "K150024",
                "/info/projects",
                "http://untrusted.example",
                new MockHttpServletRequest(),
                response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/info/projects");
    }

    @Test
    @DisplayName("complete: 안전하지 않은 next 파라미터는 안전한 쿠키 next로 재폴백하지 않는다")
    void complete_안전하지않은파라미터next_안전한쿠키로재폴백안함() throws Exception {
        SsoController controller = directEnoController();
        stubSsoTokenIssue();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        CookieUtil.SSO_NEXT_COOKIE,
                        URLEncoder.encode("/safe-cookie-path", StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete("K150024", "//evil.example", null, request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
    }

    @Test
    @DisplayName("complete: 안전한 쿼리가 있으면 malformed 상태 쿠키를 읽지 않고 성공한다")
    void complete_안전한쿼리_malformed쿠키미사용_성공() throws Exception {
        SsoController controller = directEnoController();
        stubSsoTokenIssue();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(CookieUtil.SSO_NEXT_COOKIE, "%"),
                new Cookie(CookieUtil.SSO_ORIGIN_COOKIE, "%"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete(
                "K150024", "/info/projects", "http://localhost:3000", request, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/info/projects");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(value -> value.startsWith(CookieUtil.SSO_NEXT_COOKIE + "="))
                .anyMatch(value -> value.startsWith(CookieUtil.SSO_ORIGIN_COOKIE + "="));
    }

    @Test
    @DisplayName("complete: 쿼리가 없고 상태 쿠키가 malformed이면 비밀값 없이 루트로 복구한다")
    void complete_쿼리없음_malformed쿠키_로그미노출_루트복구() throws Exception {
        String cookieSecret = "%COOKIE-SECRET-RAW-9Q7";
        SsoController controller = directEnoController();
        stubSsoTokenIssue();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(CookieUtil.SSO_NEXT_COOKIE, cookieSecret),
                new Cookie(CookieUtil.SSO_ORIGIN_COOKIE, cookieSecret));
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(SsoController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        try {
            controller.complete("K150024", null, null, request, response);

            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
            assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                    .anyMatch(value -> value.startsWith(CookieUtil.SSO_NEXT_COOKIE + "="))
                    .anyMatch(value -> value.startsWith(CookieUtil.SSO_ORIGIN_COOKIE + "="));
            assertThat(formattedMessages(appender))
                    .noneMatch(message -> message.contains(cookieSecret));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    /** mock 모드 SsoController를 직접 구성합니다 (next/origin 세션 저장 + 인증서버 통신 없이 mock 사번 주입 검증용). */
    private SsoController newController(SsoProperties props) {
        SsoController controller =
                new SsoController(authService, cookieUtil, jwtUtil, ssoAgentClient, props);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(
                controller, "allowedOrigins", "http://localhost:3000,http://localhost:3002");
        ReflectionTestUtils.setField(controller, "allowDirectEno", false);
        return controller;
    }

    private SsoController directEnoController() {
        SsoController controller =
                newController(new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000));
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        return controller;
    }

    private void stubSsoTokenIssue() {
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "a").build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "r").build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "u").build());
    }

    @Test
    @DisplayName("business: 모의 모드면 인증서버 통신 없이 mock 사번의 검증 쿠키를 발급하고 loginProc로 이동")
    void business_모의모드_검증쿠키발급후loginProc() throws Exception {
        SsoProperties props = new SsoProperties(true, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.business("/info/projects", "http://localhost:3002", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/sso/loginProc");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> issuesSsoVerifiedCookie(cookie, "K140024"));
        assertThat(request.getSession(false)).isNull();
        verify(jwtUtil).generateSsoVerifiedToken("K140024");
        verify(ssoAgentClient, never()).isServerAlive();
    }

    @Test
    @DisplayName("business: 실연동 모드에서 인증서버가 살아있으면 ESSO 로그인 페이지로 agentId와 함께 이동")
    void business_실연동_서버정상_로그인페이지이동() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
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
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.isServerAlive()).willReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.business("/info/projects", "http://localhost:3002", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/login?error=sso");
    }

    @Test
    @DisplayName("checkauth: 토큰 검증 성공 시 resultData 사번의 검증 쿠키를 발급하고 loginProc로 이동")
    void checkauth_검증성공_검증쿠키발급후loginProc() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("secure-token", "sess-1", "127.0.0.1"))
                .willReturn(
                        new SsoAgentClient.TokenAuthResult("000000", "OK", "K150024", null, false));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.checkauth("000000", "secure-token", "sess-1", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/sso/loginProc");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> issuesSsoVerifiedCookie(cookie, "K150024"));
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName(
            "checkauth: CS 모드이면 saveToken.html로 POST 자동제출 폼을"
                    + " 렌더링한다(agentId/errCode/secureSessionId)")
    void checkauth_CS모드_saveToken_POST폼렌더링() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("secure-token", "sess-1", "127.0.0.1"))
                .willReturn(
                        new SsoAgentClient.TokenAuthResult("000000", "OK", "K150024", null, true));
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
        // CS 모드도 saveToken 복귀(agentProc) 뒤 complete가 읽을 검증 쿠키를 함께 발급한다.
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> issuesSsoVerifiedCookie(cookie, "K150024"));
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("checkauth: CS 모드 POST 폼은 secureSessionId의 HTML 특수문자를 이스케이프한다(XSS 방지)")
    void checkauth_CS모드_secureSessionId_이스케이프() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        String xss = "\"><script>alert(1)</script>";
        given(ssoAgentClient.authorize("secure-token", xss, "127.0.0.1"))
                .willReturn(
                        new SsoAgentClient.TokenAuthResult("000000", "OK", "K150024", null, true));
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
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.checkauth("000000", "", null, request, response);

        // /sso/business로 되돌리면 무한 루프가 되므로 프론트 수동 로그인으로 보낸다.
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
        verify(ssoAgentClient, never())
                .authorize(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("checkauth: 비정상 호출 시 origin 쿠키가 있으면 그 origin의 로그인으로 폴백한다")
    void checkauth_비정상호출_origin쿠키사용() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        CookieUtil.SSO_ORIGIN_COOKIE,
                        URLEncoder.encode("http://localhost:3002", StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.checkauth(null, null, null, request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/login?error=sso");
    }

    @Test
    @DisplayName("checkauth: 토큰 검증 실패 코드면 수동 로그인으로 폴백")
    void checkauth_검증실패_수동로그인폴백() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
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
    @DisplayName("checkauth: 토큰 검증 실패 로그에 프록시와 원격 IP 원문을 남기지 않는다")
    void checkauth_검증실패_IP원문로그미노출() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("secure-token", "sess-1", REMOTE_ADDR_SENTINEL))
                .willReturn(new SsoAgentClient.TokenAuthResult("310017", "권한없음", "", null, false));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(REMOTE_ADDR_SENTINEL);
        request.addHeader("X-Forwarded-For", FORWARDED_IP_SENTINEL);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(SsoController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        try {
            controller.checkauth("000000", "secure-token", "sess-1", request, response);

            assertThat(response.getRedirectedUrl())
                    .isEqualTo("http://localhost:3000/login?error=sso");
            assertThat(formattedMessages(appender))
                    .noneMatch(message -> message.contains(FORWARDED_IP_SENTINEL))
                    .noneMatch(message -> message.contains(REMOTE_ADDR_SENTINEL));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @MethodSource("unsafeResultCodes")
    @DisplayName("checkauth: 제어문자·과대·형식 문자열 resultCode를 한 줄 안전 표현으로 기록한다")
    void checkauth_비정상resultCode_로그주입차단(String unsafeResultCode) throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(SsoController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        try {
            controller.checkauth(unsafeResultCode, null, null, request, response);

            assertThat(response.getRedirectedUrl())
                    .isEqualTo("http://localhost:3000/login?error=sso");
            assertThat(formattedMessages(appender))
                    .singleElement()
                    .satisfies(
                            message ->
                                    assertThat(message)
                                            .contains("resultCode: <invalid>(len=")
                                            .doesNotContain(
                                                    RESULT_CODE_SENTINEL,
                                                    "\r",
                                                    "\n",
                                                    "\t",
                                                    "\u001b",
                                                    "\u0000",
                                                    "\u2028",
                                                    "\u2029"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("checkauth: 유효한 벤더 resultCode는 기존 진단 값을 유지한다")
    void checkauth_유효resultCode_로그진단유지() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(SsoController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        try {
            controller.checkauth("310017", null, null, request, response);

            assertThat(response.getRedirectedUrl())
                    .isEqualTo("http://localhost:3000/login?error=sso");
            assertThat(formattedMessages(appender))
                    .singleElement()
                    .satisfies(message -> assertThat(message).contains("resultCode: 310017"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("checkauth: 인증서버의 비정상 resultCode도 한 줄 안전 표현으로 기록한다")
    void checkauth_인증서버비정상resultCode_로그주입차단() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        String unsafeResultCode = RESULT_CODE_SENTINEL + "\r\nFORGED";
        given(ssoAgentClient.authorize("secure-token", "sess-1", "127.0.0.1"))
                .willReturn(
                        new SsoAgentClient.TokenAuthResult(
                                unsafeResultCode, "권한없음", "", null, false));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(SsoController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        try {
            controller.checkauth("000000", "secure-token", "sess-1", request, response);

            assertThat(response.getRedirectedUrl())
                    .isEqualTo("http://localhost:3000/login?error=sso");
            verify(jwtUtil, never()).generateSsoVerifiedToken(anyString());
            assertThat(formattedMessages(appender))
                    .singleElement()
                    .satisfies(
                            message ->
                                    assertThat(message)
                                            .contains("resultCode: <invalid>(len=")
                                            .doesNotContain(
                                                    RESULT_CODE_SENTINEL,
                                                    "\r",
                                                    "\n",
                                                    "\t",
                                                    "\u001b",
                                                    "\u0000",
                                                    "\u2028",
                                                    "\u2029"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("complete: next 쿼리의 토큰·세션 원문을 성공 로그에 남기지 않고 그대로 리다이렉트한다")
    void complete_next쿼리민감정보로그미노출_리다이렉트유지() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "a").build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "r").build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "u").build());
        String next =
                "/x?secureToken="
                        + TARGET_TOKEN_SENTINEL
                        + "&secureSessionId="
                        + TARGET_SESSION_SENTINEL;
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(SsoController.class);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        try {
            controller.complete("K150024", next, "http://localhost:3000", request, response);

            assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000" + next);
            assertThat(formattedMessages(appender))
                    .noneMatch(message -> message.contains(TARGET_TOKEN_SENTINEL))
                    .noneMatch(message -> message.contains(TARGET_SESSION_SENTINEL));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    @Test
    @DisplayName("complete: 토큰 발급 실패 시 로그인 오류 페이지로 이동한다")
    void complete_토큰발급실패_로그인오류() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        stubVerified("K150024");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(verifiedCookie());
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(authService.issueSsoTokens("K150024")).willThrow(new IllegalStateException("사용자 없음"));

        controller.complete(null, "/info/projects", "http://localhost:3002", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/login?error=sso");
    }

    @Test
    @DisplayName("complete: 검증 쿠키가 있어도 서명·만료·용도 검증에 실패하면 인증 실패로 처리한다")
    void complete_검증쿠키무효_로그인오류() throws Exception {
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieUtil.SSO_VERIFIED_COOKIE, "tampered-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.complete(null, "/info/projects", "http://localhost:3002", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/login?error=sso");
        verify(authService, never()).issueSsoTokens(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("ssoLogout: 모의 모드이면 프론트 로그인으로 이동한다")
    void ssoLogout_모의모드_프론트로그인() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        true,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoLogout(request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("ssoLogout: 실연동 모드이면 ESSO 로그아웃 페이지로 이동한다")
    void ssoLogout_실연동_통합로그아웃() throws Exception {
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoLogout(request, response);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://dintesso.kdb.co.kr:20443/logout.html");
    }

    @Test
    @DisplayName("GET /sso/logout은 405를 반환한다")
    void ssoLogout_GET_405() throws Exception {
        mockMvc.perform(get("/sso/logout").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    @DisplayName("POST /sso/logout은 서버 세션 없이 로그인 화면으로 이동한다")
    void ssoLogout_POST_로그인화면이동() throws Exception {
        mockMvc.perform(post("/sso/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/login"))
                .andExpect(request().sessionAttributeDoesNotExist("ssoVerifiedEno"));
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // business: 누락 분기 커버 (null/blank next·origin, origin 허용 여부)
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("business: next·origin이 null이면 세션·쿠키에 저장하지 않고 모의 모드 처리")
    void business_null_nextOrigin_세션쿠키저장안함() throws Exception {
        // Arrange — 모의 모드, next·origin 모두 null
        SsoProperties props = new SsoProperties(true, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        controller.business(null, null, request, response);

        // Assert — loginProc로 이동하되 쿠키 생성 메서드는 호출되지 않아야 한다
        assertThat(response.getRedirectedUrl()).isEqualTo("/sso/loginProc");
        verify(cookieUtil, never()).createSsoNextCookie(anyString());
        verify(cookieUtil, never()).createSsoOriginCookie(anyString());
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("business: next·origin이 공백이면 세션·쿠키에 저장하지 않는다")
    void business_blank_nextOrigin_세션쿠키저장안함() throws Exception {
        // Arrange — 모의 모드, next·origin 공백
        SsoProperties props = new SsoProperties(true, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        controller.business("   ", "  ", request, response);

        // Assert — 공백은 저장 대상에서 제외
        assertThat(response.getRedirectedUrl()).isEqualTo("/sso/loginProc");
        verify(cookieUtil, never()).createSsoNextCookie(anyString());
        verify(cookieUtil, never()).createSsoOriginCookie(anyString());
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("business: 실연동 모드에서 인증서버 실패 시 허용 목록의 origin을 사용해 폴백 리다이렉트한다")
    void business_실연동_서버다운_허용된origin으로폴백() throws Exception {
        // Arrange — 허용 목록에 있는 origin을 전달
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.isServerAlive()).willReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        controller.business(null, "http://localhost:3000", request, response);

        // Assert — 허용된 origin으로 폴백
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
    }

    @Test
    @DisplayName("business: 실연동 모드에서 허용되지 않은 origin이면 기본 frontend-url로 폴백한다")
    void business_실연동_서버다운_허용안된origin_기본프론트폴백() throws Exception {
        // Arrange — 허용 목록에 없는 origin 전달
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.isServerAlive()).willReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — origin은 허용 목록에 없는 값
        controller.business(null, "https://evil.example", request, response);

        // Assert — frontendUrl 기본값으로 폴백
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // checkauth: 누락 분기 커버 (resultCode 실패+유효토큰, 세션 origin, null secureSessionId)
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkauth: resultCode가 실패코드이지만 secureToken이 있으면 비정상 경로로 처리한다")
    void checkauth_실패resultCode_유효토큰_비정상경로() throws Exception {
        // Arrange — resultCode 실패 + secureToken 있음 → 비정상 호출 경로
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        CookieUtil.SSO_ORIGIN_COOKIE,
                        URLEncoder.encode("http://localhost:3002", StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — resultCode 실패 코드로 전달
        controller.checkauth("999999", "some-token", "sess-1", request, response);

        // Assert — authorize 호출 없이 수동 로그인으로 폴백
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/login?error=sso");
        verify(ssoAgentClient, never())
                .authorize(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("checkauth: 토큰 검증 실패 시 origin 쿠키가 있으면 그 origin으로 폴백한다")
    void checkauth_검증실패_origin쿠키사용() throws Exception {
        // Arrange — 토큰 검증 실패 + SSO 시작 시 심은 origin 쿠키
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("secure-token", "sess-1", "127.0.0.1"))
                .willReturn(new SsoAgentClient.TokenAuthResult("310012", "권한없음", "", null, false));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.setCookies(ssoStateCookie(CookieUtil.SSO_ORIGIN_COOKIE, "http://localhost:3002"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        controller.checkauth("000000", "secure-token", "sess-1", request, response);

        // Assert — 쿠키 origin으로 폴백하고 검증 쿠키는 발급하지 않는다
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3002/login?error=sso");
        verify(jwtUtil, never()).generateSsoVerifiedToken(anyString());
    }

    @Test
    @DisplayName("checkauth: 토큰 검증 실패 시 origin 쿠키가 없으면 기본 frontend-url로 폴백한다")
    void checkauth_검증실패_origin쿠키없음_기본프론트폴백() throws Exception {
        // Arrange — 토큰 검증 실패 + origin 쿠키 없음
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("secure-token", "sess-1", "127.0.0.1"))
                .willReturn(new SsoAgentClient.TokenAuthResult("310012", "권한없음", "", null, false));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        controller.checkauth("000000", "secure-token", "sess-1", request, response);

        // Assert — frontendUrl 기본값으로 폴백
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
    }

    @Test
    @DisplayName("checkauth: CS 모드이고 secureSessionId가 null이면 빈 문자열로 폼을 렌더링한다")
    void checkauth_CS모드_null_secureSessionId_빈값처리() throws Exception {
        // Arrange — CS 모드 + secureSessionId null
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("secure-token", null, "127.0.0.1"))
                .willReturn(
                        new SsoAgentClient.TokenAuthResult("000000", "OK", "K150024", null, true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        controller.checkauth("000000", "secure-token", null, request, response);

        // Assert — null secureSessionId가 빈 문자열로 처리되어 XSS 없이 폼 렌더링
        assertThat(response.getRedirectedUrl()).isNull();
        String html = response.getContentAsString();
        assertThat(html).contains("name=\"secureSessionId\" value=\"\"");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // complete: next 시작 문자 분기, 쿠키 폴백 vs 파라미터 우선
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("complete: next가 '/'로 시작하지 않으면 루트('/')로 이동한다(오픈 리다이렉트 방지)")
    void complete_외부next_루트로이동() throws Exception {
        // Arrange — next가 절대 URL
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "a").build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "r").build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "u").build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — next가 '/'로 시작하지 않는다
        controller.complete("K150024", "https://evil.example/steal", null, request, response);

        // Assert — 오픈 리다이렉트 방지: '/'로 대체
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
    }

    @Test
    @DisplayName("complete: next 파라미터가 있으면 쿠키 next보다 파라미터를 우선한다")
    void complete_파라미터next가쿠키보다우선() throws Exception {
        // Arrange — 파라미터와 쿠키 모두 있을 때
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "a").build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "r").build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "u").build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 쿠키에도 next가 있지만 파라미터가 우선
        request.setCookies(
                new Cookie(
                        CookieUtil.SSO_NEXT_COOKIE,
                        URLEncoder.encode("/cookie-path", StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — 파라미터 next를 명시적으로 전달
        controller.complete("K150024", "/param-path", null, request, response);

        // Assert — 파라미터 next가 쿠키보다 우선
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/param-path");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // resolveVerifiedEno: session == null, allowDirectEno 분기, 사용 후 세션 제거
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolveVerifiedEno: 검증 쿠키가 없고 allowDirectEno=false이면 예외 발생 → complete 오류 폴백")
    void resolveVerifiedEno_검증쿠키없고directEno불허_오류폴백() throws Exception {
        // Arrange — 검증 쿠키 없음, allowDirectEno=false(기본값)
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        controller.complete(null, null, "http://localhost:3000", request, response);

        // Assert — IllegalStateException 발생 → 오류 리다이렉트
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
    }

    @Test
    @DisplayName("resolveVerifiedEno: allowDirectEno=true이고 directEno가 blank이면 예외 발생 → 오류 폴백")
    void resolveVerifiedEno_directEno허용_blank_오류폴백() throws Exception {
        // Arrange — allowDirectEno=true, directEno는 공백
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — directEno로 공백 전달 (세션도 없음)
        controller.complete("   ", null, "http://localhost:3000", request, response);

        // Assert — 공백 eno는 유효하지 않으므로 오류 폴백
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
        verify(authService, never()).issueSsoTokens(anyString());
    }

    @Test
    @DisplayName("resolveVerifiedEno: 검증 쿠키의 사번은 직접 사번보다 우선하고 사용 후 쿠키를 삭제한다")
    void resolveVerifiedEno_검증쿠키우선_사용후삭제() throws Exception {
        // Arrange — allowDirectEno=true지만 검증 쿠키가 있으면 쿠키 사번이 이긴다
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        stubVerified("K150024");
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "a").build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "r").build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "u").build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(verifiedCookie());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — 직접 사번은 다른 값이지만 검증 쿠키 사번으로 발급돼야 한다
        controller.complete("ITPAD001", "/dashboard", null, request, response);

        // Assert — 쿠키 사번 사용, 검증 쿠키 삭제, 서버 세션 미생성
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/dashboard");
        verify(authService).issueSsoTokens("K150024");
        verify(authService, never()).issueSsoTokens("ITPAD001");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(SsoControllerTest::deletesSsoVerifiedCookie);
        assertThat(request.getSession(false)).isNull();
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // readCookie: cookies == null, 쿠키 이름 없음 분기
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("readCookie: getCookies()가 null을 반환하면 빈 문자열로 처리해 NPE 없이 폴백한다")
    void checkauth_쿠키배열null_NPE없이폴백() throws Exception {
        // Arrange — getCookies()가 null인 HttpServletRequest 목킹
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        jakarta.servlet.http.HttpServletRequest mockedReq =
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        given(mockedReq.getCookies()).willReturn(null);
        given(mockedReq.getRemoteAddr()).willReturn("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — 비정상 호출(resultCode null)이면 readCookie(null 배열) → "" → frontendUrl 폴백
        controller.checkauth(null, null, null, mockedReq, response);

        // Assert — NullPointerException 없이 기본 프론트로 폴백
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
    }

    @Test
    @DisplayName("readCookie: 요청에 다른 쿠키만 있고 SSO origin 쿠키가 없으면 기본 frontend-url로 폴백")
    void checkauth_비정상호출_origin쿠키없음_기본프론트폴백() throws Exception {
        // Arrange — SSO_ORIGIN_COOKIE가 아닌 다른 쿠키만 존재
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "3",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other-cookie", "some-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        controller.checkauth(null, null, null, request, response);

        // Assert — origin 쿠키 없으면 빈 문자열 → frontendUrl 기본값 사용
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=sso");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // firstNonBlank: primary null/blank 분기 (complete를 통해 간접 검증)
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("firstNonBlank: primary가 null이면 fallback을 반환한다 (complete 쿠키 복원 경로)")
    void firstNonBlank_primaryNull_fallback반환() throws Exception {
        // Arrange — next 파라미터 null이지만 쿠키에 있음
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("a")
                        .refreshToken("r")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("a"))
                .willReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "a").build());
        given(cookieUtil.createRefreshTokenCookie("r"))
                .willReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "r").build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "u").build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        CookieUtil.SSO_NEXT_COOKIE,
                        URLEncoder.encode("/fallback-path", StandardCharsets.UTF_8)),
                new Cookie(
                        CookieUtil.SSO_ORIGIN_COOKIE,
                        URLEncoder.encode("http://localhost:3000", StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — 파라미터 next·origin은 null
        controller.complete("K150024", null, null, request, response);

        // Assert — null primary → 쿠키 fallback 사용
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/fallback-path");
    }

    @Test
    @DisplayName("complete: 빈 next 파라미터는 안전한 쿠키 next로 재폴백하지 않는다")
    void complete_빈파라미터next_안전한쿠키로재폴백안함() throws Exception {
        // Arrange — 빈 next 파라미터와 안전한 next 쿠키가 공존
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        ReflectionTestUtils.setField(controller, "allowDirectEno", true);
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("K150024")
                        .accessToken("a")
                        .refreshToken("r")
                        .build();
        given(authService.issueSsoTokens("K150024")).willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("a"))
                .willReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "a").build());
        given(cookieUtil.createRefreshTokenCookie("r"))
                .willReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "r").build());
        given(cookieUtil.createUserInfoCookie(loginResponse))
                .willReturn(ResponseCookie.from("it-portal-user", "u").build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        CookieUtil.SSO_NEXT_COOKIE,
                        URLEncoder.encode("/blank-fallback", StandardCharsets.UTF_8)),
                new Cookie(
                        CookieUtil.SSO_ORIGIN_COOKIE,
                        URLEncoder.encode("http://localhost:3000", StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — next 파라미터가 명시적인 빈 문자열
        controller.complete("K150024", "", "  ", request, response);

        // Assert — 명시적 빈 파라미터는 쿠키로 재폴백하지 않고 루트로 이동
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // attr: null 입력 분기, HTML 특수문자 이스케이프 (writeAutoSubmitPostForm 경유)
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("attr: agentId에 HTML 특수문자가 있으면 이스케이프한다")
    void checkauth_CS모드_agentId_특수문자이스케이프() throws Exception {
        // Arrange — agentId에 '<'·'>'가 포함된 경우
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "<agent>",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("t", "s", "127.0.0.1"))
                .willReturn(
                        new SsoAgentClient.TokenAuthResult("000000", "OK", "K150024", null, true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        controller.checkauth("000000", "t", "s", request, response);

        // Assert — agentId의 '<'·'>'가 이스케이프됐는지 확인
        String html = response.getContentAsString();
        assertThat(html).contains("value=\"&lt;agent&gt;\"");
        assertThat(html).doesNotContain("value=\"<agent>\"");
    }

    @Test
    @DisplayName("attr: null secureSessionId는 빈 문자열로 처리한다(attr null 분기)")
    void checkauth_CS모드_null_secureSessionId_attr빈문자열() throws Exception {
        // Arrange — secureSessionId=null → writeAutoSubmitPostForm 내 attr(null) → "" 분기 실행
        SsoProperties props =
                new SsoProperties(
                        false,
                        "K140024",
                        "https://dintesso.kdb.co.kr:20443",
                        "https://dintesso.kdb.co.kr:20443",
                        "agentX",
                        "id",
                        5000,
                        5000);
        SsoController controller = newController(props);
        given(ssoAgentClient.authorize("t", null, "127.0.0.1"))
                .willReturn(
                        new SsoAgentClient.TokenAuthResult("000000", "OK", "K150024", null, true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — secureSessionId null
        controller.checkauth("000000", "t", null, request, response);

        // Assert — null이 "" (빈 문자열)로 처리됨
        String html = response.getContentAsString();
        assertThat(html).contains("name=\"secureSessionId\" value=\"\"");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // ssoLogout: browserBaseUrl이 blank인 실연동 모드 분기
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ssoLogout: 실연동 모드이지만 browserBaseUrl이 blank이면 프론트 로그인으로 이동한다")
    void ssoLogout_실연동_browserBaseUrl_blank_프론트로그인() throws Exception {
        // Arrange — mockEnabled=false이지만 browserBaseUrl 없음
        SsoProperties props = new SsoProperties(false, "K140024", "", "", "", "id", 5000, 5000);
        SsoController controller = newController(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        controller.ssoLogout(request, response);

        // Assert — browserBaseUrl blank → frontendUrl/login으로 이동
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login");
    }
}
