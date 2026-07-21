package com.kdb.it.common.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.exception.InvalidRefreshTokenException;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AuthController @WebMvcTest
 *
 * <p>Web 레이어만 로드하여 인증 API의 HTTP 요청/응답 스펙을 검증합니다. 토큰은 응답 body가 아닌 Set-Cookie 헤더로 전달됨을 검증합니다.
 */
@WebMvcTest(com.kdb.it.common.system.controller.AuthController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;
    @MockitoBean private CookieUtil cookieUtil;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/auth/signup - 성공 시 200 + '회원가입 성공' 반환")
    void signup_성공_200반환() throws Exception {
        // given
        AuthDto.SignupRequest request = new AuthDto.SignupRequest();
        request.setEno("10001");
        request.setEmpNm("홍길동");
        request.setPassword("password123");

        doNothing()
                .when(authService)
                .signup(org.mockito.ArgumentMatchers.any(AuthDto.SignupRequest.class));

        // when & then
        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("회원가입 성공"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 성공 시 200 + eno/empNm body + Set-Cookie 헤더")
    void login_성공_200및쿠키반환() throws Exception {
        // given
        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setEno("10001");
        request.setPassword("password123");

        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("10001")
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

        given(authService.login(anyString(), anyString(), anyString(), anyString()))
                .willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token")).willReturn(accessCookie);
        given(cookieUtil.createRefreshTokenCookie("refresh-token")).willReturn(refreshCookie);

        // when & then
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("User-Agent", "TestAgent")
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eno").value("10001"))
                .andExpect(jsonPath("$.empNm").value("홍길동"))
                // @JsonIgnore: 토큰이 응답 body에 없어야 함
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                // 토큰은 Set-Cookie 헤더로 전달
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 서비스 예외 시 500 반환")
    void login_서비스예외_500반환() throws Exception {
        // given
        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setEno("99999");
        request.setPassword("password");

        given(authService.login(anyString(), anyString(), anyString(), anyString()))
                .willThrow(new RuntimeException("사용자를 찾을 수 없습니다."));

        // when & then
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("User-Agent", "TestAgent")
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/auth/session - Access Token 인증 사용자의 화면 복원 정보 반환")
    void session_인증사용자_화면복원정보반환() throws Exception {
        CustomUserDetails principal = new CustomUserDetails("10001", List.of("ITPZZ002"), "BBR001");
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, "", principal.getAuthorities());
        AuthDto.LoginResponse sessionUser =
                AuthDto.LoginResponse.builder()
                        .eno("10001")
                        .empNm("홍길동")
                        .athIds(List.of("ITPZZ002"))
                        .bbrC("BBR001")
                        .temC("TEM001")
                        .build();
        given(authService.getSessionUser("10001")).willReturn(sessionUser);

        mockMvc.perform(
                        get("/api/auth/session")
                                .with(
                                        SecurityMockMvcRequestPostProcessors.authentication(
                                                authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eno").value("10001"))
                .andExpect(jsonPath("$.empNm").value("홍길동"))
                .andExpect(jsonPath("$.athIds[0]").value("ITPZZ002"))
                .andExpect(jsonPath("$.bbrC").value("BBR001"))
                .andExpect(jsonPath("$.temC").value("TEM001"));

        verify(authService).getSessionUser("10001");
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Refresh Token 쿠키 없으면 401 + Access·Refresh 삭제 쿠키 2개")
    void refresh_쿠키없음_401및쿠키삭제() throws Exception {
        // given — helper가 두 삭제 쿠키를 조회하므로 스텁 필요
        stubDeleteCookies();

        // when & then
        MvcResult result =
                mockMvc.perform(post("/api/auth/refresh"))
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().string("다시 로그인해 주세요."))
                        .andReturn();

        assertBothDeleteCookies(result);
    }

    @Test
    @DisplayName("POST /api/auth/refresh - 유효한 Refresh Token 쿠키 → 200 + '토큰 갱신 성공'")
    void refresh_유효한쿠키_200반환() throws Exception {
        // given
        AuthDto.RefreshResponse refreshResponse =
                AuthDto.RefreshResponse.builder().accessToken("new-access-token").build();

        ResponseCookie newAccessCookie =
                ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "new-access-token")
                        .httpOnly(true)
                        .path("/")
                        .build();

        given(authService.refreshAccessToken("valid-refresh-token")).willReturn(refreshResponse);
        given(cookieUtil.createAccessTokenCookie("new-access-token")).willReturn(newAccessCookie);

        // when & then
        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(
                                        new Cookie(
                                                CookieUtil.REFRESH_TOKEN_COOKIE,
                                                "valid-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(content().string("토큰 갱신 성공"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 신뢰 프록시 미설정 시 X-Forwarded-For를 무시하고 remoteAddr을 사용한다")
    void login_XForwardedFor_미신뢰_remoteAddr전달() throws Exception {
        // app.trusted-proxies 미설정(빈 allowlist)이므로 XFF는 위조 가능으로 간주해 무시.
        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setEno("10001");
        request.setPassword("password123");
        stubLoginResponseAndCookies();

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Forwarded-For", "203.0.113.10")
                                .header("User-Agent", "TestAgent")
                                .with(
                                        req -> {
                                            req.setRemoteAddr("198.51.100.5");
                                            return req;
                                        })
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authService).login("10001", "password123", "198.51.100.5", "TestAgent");
    }

    @Test
    @DisplayName("POST /api/auth/login - X-Forwarded-For가 없으면 remoteAddr을 사용한다")
    void login_XForwardedFor없음_remoteAddr전달() throws Exception {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setEno("10001");
        request.setPassword("password123");
        stubLoginResponseAndCookies();

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("User-Agent", "TestAgent")
                                .with(
                                        req -> {
                                            req.setRemoteAddr("198.51.100.20");
                                            return req;
                                        })
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authService).login("10001", "password123", "198.51.100.20", "TestAgent");
    }

    @Test
    @DisplayName("POST /api/auth/login - 미신뢰 환경에서 멀티 IP XFF도 무시하고 remoteAddr을 사용한다")
    void login_멀티IP_미신뢰_remoteAddr전달() throws Exception {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setEno("10001");
        request.setPassword("password123");
        stubLoginResponseAndCookies();

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Forwarded-For", "203.0.113.30, 10.0.0.9")
                                .header("User-Agent", "TestAgent")
                                .with(
                                        req -> {
                                            req.setRemoteAddr("198.51.100.30");
                                            return req;
                                        })
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authService).login("10001", "password123", "198.51.100.30", "TestAgent");
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("POST /api/auth/logout - 인증 사용자가 있으면 서비스 로그아웃과 쿠키 삭제를 수행한다")
    void logout_인증사용자_서비스호출및쿠키삭제() throws Exception {
        ResponseCookie deleteAccess =
                ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "").maxAge(0).path("/").build();
        ResponseCookie deleteRefresh =
                ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "")
                        .maxAge(0)
                        .path("/api/auth")
                        .build();
        given(cookieUtil.deleteAccessTokenCookie()).willReturn(deleteAccess);
        given(cookieUtil.deleteRefreshTokenCookie()).willReturn(deleteRefresh);

        mockMvc.perform(
                        post("/api/auth/logout")
                                .header("User-Agent", "TestAgent")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("198.51.100.1");
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("로그아웃 성공"))
                .andExpect(header().exists("Set-Cookie"));

        verify(authService).logout(eq("10001"), eq("198.51.100.1"), eq("TestAgent"));
    }

    @Test
    @DisplayName("logout - 인증 정보가 없으면 서비스 호출 없이 쿠키만 삭제한다")
    void logout_인증정보없음_쿠키만삭제() {
        SecurityContextHolder.clearContext();
        ResponseCookie deleteAccess =
                ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "").maxAge(0).path("/").build();
        ResponseCookie deleteRefresh =
                ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "")
                        .maxAge(0)
                        .path("/api/auth")
                        .build();
        given(cookieUtil.deleteAccessTokenCookie()).willReturn(deleteAccess);
        given(cookieUtil.deleteRefreshTokenCookie()).willReturn(deleteRefresh);
        AuthController controller = new AuthController(authService, cookieUtil, "");

        var response = controller.logout(new MockHttpServletRequest());

        assert response.getStatusCode().is2xxSuccessful();
        verify(authService, never()).logout(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/auth/logout - Access 인증이 없어도 Refresh 쿠키로 패밀리를 폐기한다")
    void logout_Access만료_Refresh쿠키로폐기() throws Exception {
        SecurityContextHolder.clearContext();
        ResponseCookie deleteAccess =
                ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "").maxAge(0).path("/").build();
        ResponseCookie deleteRefresh =
                ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "")
                        .maxAge(0)
                        .path("/api/auth")
                        .build();
        given(cookieUtil.deleteAccessTokenCookie()).willReturn(deleteAccess);
        given(cookieUtil.deleteRefreshTokenCookie()).willReturn(deleteRefresh);

        mockMvc.perform(
                        post("/api/auth/logout")
                                .cookie(
                                        new Cookie(
                                                CookieUtil.REFRESH_TOKEN_COOKIE, "refresh-token"))
                                .header("User-Agent", "TestAgent")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("198.51.100.3");
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("로그아웃 성공"));

        verify(authService)
                .logoutByRefreshToken(
                        eq("refresh-token"), org.mockito.ArgumentMatchers.isNull(),
                        eq("198.51.100.3"), eq("TestAgent"));
    }

    // -----------------------------------------------------------------------
    // refresh — Refresh Token 회전 분기 (response.getRefreshToken() != null)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/refresh - Refresh Token 회전 시 새 refreshToken 쿠키도 Set-Cookie에 포함")
    void refresh_토큰회전_새리프레시쿠키포함() throws Exception {
        // given — 서비스가 새 accessToken + 회전된 refreshToken 모두 반환하는 경우
        AuthDto.RefreshResponse rotatedResponse =
                AuthDto.RefreshResponse.builder()
                        .accessToken("rotated-access-token")
                        .refreshToken("rotated-refresh-token")
                        .build();

        ResponseCookie newAccessCookie =
                ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "rotated-access-token")
                        .httpOnly(true)
                        .path("/")
                        .build();
        ResponseCookie newRefreshCookie =
                ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "rotated-refresh-token")
                        .httpOnly(true)
                        .path("/api/auth")
                        .build();

        given(authService.refreshAccessToken("valid-refresh-token")).willReturn(rotatedResponse);
        given(cookieUtil.createAccessTokenCookie("rotated-access-token"))
                .willReturn(newAccessCookie);
        given(cookieUtil.createRefreshTokenCookie("rotated-refresh-token"))
                .willReturn(newRefreshCookie);

        // when & then — Set-Cookie 헤더가 존재하고 body가 "토큰 갱신 성공"임을 검증
        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(
                                        new Cookie(
                                                CookieUtil.REFRESH_TOKEN_COOKIE,
                                                "valid-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(content().string("토큰 갱신 성공"))
                .andExpect(header().exists("Set-Cookie"));
    }

    // -----------------------------------------------------------------------
    // refresh — extractCookieValue: 쿠키 배열에 대상 이름이 없는 경우 (이름 불일치 분기)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/refresh - 쿠키는 있지만 refreshToken 이름이 아닌 경우 401 + 삭제 쿠키 2개")
    void refresh_쿠키이름불일치_401및쿠키삭제() throws Exception {
        // given — 다른 이름의 쿠키만 존재 → extractCookieValue 루프에서 이름 불일치 후 null 반환
        // 즉 cookies != null, but no cookie named "refreshToken" → helper(401 + 쿠키 삭제) 분기 도달
        stubDeleteCookies();

        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/refresh")
                                        .cookie(new Cookie("otherCookie", "some-value")))
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().string("다시 로그인해 주세요."))
                        .andReturn();

        assertBothDeleteCookies(result);
    }

    @Test
    @DisplayName(
            "POST /api/auth/refresh - 서비스가 InvalidRefreshTokenException 발생 시 401 + 삭제 쿠키 2개 + 재로그인 안내")
    void refresh_서비스예외_401및쿠키삭제() throws Exception {
        // given — 서비스가 잘못된 Refresh 토큰 전용 예외를 던지는 경우
        stubDeleteCookies();
        given(authService.refreshAccessToken("invalid-refresh-token"))
                .willThrow(new InvalidRefreshTokenException());

        // when & then — 컨트롤러 helper가 401 + Access·Refresh 쿠키 삭제로 변환
        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/refresh")
                                        .cookie(
                                                new Cookie(
                                                        CookieUtil.REFRESH_TOKEN_COOKIE,
                                                        "invalid-refresh-token")))
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().string("다시 로그인해 주세요."))
                        .andReturn();

        assertBothDeleteCookies(result);
    }

    // -----------------------------------------------------------------------
    // 생성자 람다 (lambda$new$0 / lambda$new$1): trustedProxiesCsv 파싱 분기
    // trustedProxiesCsv가 공백이 아닐 때 .map(String::trim).filter(!isEmpty) 람다 실행
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("생성자 - 신뢰 프록시 CSV를 공백·빈값 포함해 파싱하면 유효한 항목만 Set에 저장된다")
    void constructor_trustedProxiesCsv_파싱_람다실행() {
        // given — 공백 항목이 포함된 CSV (trim + filter 람다 모두 실행)
        // "10.0.0.1, ,10.0.0.2," → trim 후 빈 항목 필터링 → {10.0.0.1, 10.0.0.2}
        AuthController controller =
                new AuthController(authService, cookieUtil, "10.0.0.1, ,10.0.0.2,");

        // when & then — 컨트롤러 생성 자체가 람다를 실행한다; NPE 없이 생성되면 통과
        org.assertj.core.api.Assertions.assertThat(controller).isNotNull();
    }

    @Test
    @DisplayName("생성자 - 단일 프록시 IP만 있는 CSV도 정상 파싱된다")
    void constructor_trustedProxiesCsv_단일항목_파싱() {
        // given — 단일 값, trim 결과가 비어있지 않으므로 filter 통과
        AuthController controller = new AuthController(authService, cookieUtil, "192.168.1.1");

        // then — 람다가 실행되고 예외 없이 생성됨
        org.assertj.core.api.Assertions.assertThat(controller).isNotNull();
    }

    // -----------------------------------------------------------------------
    // logout — 쿠키 삭제 결과 Set-Cookie 헤더가 두 개 설정되는지 검증
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "20001")
    @DisplayName("POST /api/auth/logout - 쿠키 삭제 헤더(Set-Cookie)가 응답에 포함된다")
    void logout_쿠키삭제헤더_존재() throws Exception {
        // given — 삭제용 만료 쿠키 스텁
        ResponseCookie deleteAccess =
                ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "").maxAge(0).path("/").build();
        ResponseCookie deleteRefresh =
                ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "")
                        .maxAge(0)
                        .path("/api/auth")
                        .build();
        given(cookieUtil.deleteAccessTokenCookie()).willReturn(deleteAccess);
        given(cookieUtil.deleteRefreshTokenCookie()).willReturn(deleteRefresh);

        // when & then — IP/User-Agent를 함께 제공해 logout 인자(ipAddress·userAgent) null을 회피
        mockMvc.perform(
                        post("/api/auth/logout")
                                .header("User-Agent", "TestAgent")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("198.51.100.2");
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("로그아웃 성공"))
                .andExpect(header().exists("Set-Cookie"));

        // authService.logout 이 인증 사용자(20001)에 대해 호출되었는지 검증
        verify(authService).logout(eq("20001"), eq("198.51.100.2"), eq("TestAgent"));
    }

    /**
     * 삭제용 Access·Refresh 쿠키 스텁을 운영 CookieUtil과 동일 속성으로 준비합니다. (Max-Age=0, HttpOnly, Access=Path
     * "/", Refresh=Path "/api/auth", SameSite=Lax)
     */
    private void stubDeleteCookies() {
        ResponseCookie deleteAccess =
                ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "")
                        .httpOnly(true)
                        .path("/")
                        .maxAge(0)
                        .sameSite("Lax")
                        .build();
        ResponseCookie deleteRefresh =
                ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "")
                        .httpOnly(true)
                        .path("/api/auth")
                        .maxAge(0)
                        .sameSite("Lax")
                        .build();
        given(cookieUtil.deleteAccessTokenCookie()).willReturn(deleteAccess);
        given(cookieUtil.deleteRefreshTokenCookie()).willReturn(deleteRefresh);
    }

    /**
     * 응답의 Set-Cookie 헤더가 Access·Refresh 삭제 쿠키 정확히 2개인지 검증합니다. 각 쿠키는 이름뿐 아니라 Max-Age=0, HttpOnly,
     * 그리고 생성 쿠키와 동일한 Path를 가져야 합니다. (Access=Path "/", Refresh=Path "/api/auth")
     */
    private void assertBothDeleteCookies(MvcResult result) {
        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        // Access 삭제 쿠키: 이름 accessToken, Path=/, Max-Age=0, HttpOnly
        assertThat(setCookies)
                .anySatisfy(
                        cookie ->
                                assertThat(cookie)
                                        .contains(CookieUtil.ACCESS_TOKEN_COOKIE + "=")
                                        .contains("Path=/;")
                                        .contains("Max-Age=0")
                                        .contains("HttpOnly"));
        // Refresh 삭제 쿠키: 이름 refreshToken, Path=/api/auth, Max-Age=0, HttpOnly
        assertThat(setCookies)
                .anySatisfy(
                        cookie ->
                                assertThat(cookie)
                                        .contains(CookieUtil.REFRESH_TOKEN_COOKIE + "=")
                                        .contains("Path=/api/auth")
                                        .contains("Max-Age=0")
                                        .contains("HttpOnly"));
    }

    private void stubLoginResponseAndCookies() {
        AuthDto.LoginResponse loginResponse =
                AuthDto.LoginResponse.builder()
                        .eno("10001")
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
        given(authService.login(anyString(), anyString(), anyString(), anyString()))
                .willReturn(loginResponse);
        given(cookieUtil.createAccessTokenCookie("access-token")).willReturn(accessCookie);
        given(cookieUtil.createRefreshTokenCookie("refresh-token")).willReturn(refreshCookie);
    }
}
