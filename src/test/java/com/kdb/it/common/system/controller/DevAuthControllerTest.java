package com.kdb.it.common.system.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DevAuthController Web 계층 테스트
 *
 * <p>개발용 사용자 목록 필터링과 사용자 전환 시 쿠키 발급 계약을 검증합니다.</p>
 */
@WebMvcTest(DevAuthController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
@TestPropertySource(properties = "app.dev.user-switch.enabled=true")
class DevAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CookieUtil cookieUtil;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/auth/dev/users: 삭제되지 않은 사용자만 목록으로 반환한다")
    void listUsers_활성사용자만반환() throws Exception {
        CuserI active = CuserI.builder().eno("10001").usrNm("홍길동").bbrC("101").delYn("N").build();
        CuserI deleted = CuserI.builder().eno("99999").usrNm("삭제사용자").delYn("Y").build();
        given(userRepository.findAllByOrderByUsrNmAsc()).willReturn(List.of(active, deleted));

        mockMvc.perform(get("/api/auth/dev/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eno").value("10001"))
                .andExpect(jsonPath("$[0].usrNm").value("홍길동"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("POST /api/auth/dev/switch-user: 토큰 쿠키 세 개와 사용자 응답을 반환한다")
    void switchUser_정상요청_쿠키발급() throws Exception {
        DevAuthController.SwitchRequest request = new DevAuthController.SwitchRequest();
        request.setEno("10001");
        AuthDto.LoginResponse response = AuthDto.LoginResponse.builder()
                .eno("10001")
                .empNm("홍길동")
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();
        given(authService.issueDevSwitchTokens("10001")).willReturn(response);
        given(cookieUtil.createAccessTokenCookie("access-token"))
                .willReturn(ResponseCookie.from("access_token", "access-token").path("/").build());
        given(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .willReturn(ResponseCookie.from("refresh_token", "refresh-token").path("/").build());
        given(cookieUtil.createUserInfoCookie(response))
                .willReturn(ResponseCookie.from("it-portal-user", "user-info").path("/").build());

        mockMvc.perform(post("/api/auth/dev/switch-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie",
                        "access_token=access-token; Path=/",
                        "refresh_token=refresh-token; Path=/",
                        "it-portal-user=user-info; Path=/"))
                .andExpect(jsonPath("$.eno").value("10001"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/dev/switch-user: 사번이 비어있으면 요청을 거부한다")
    void switchUser_사번없음_400() throws Exception {
        mockMvc.perform(post("/api/auth/dev/switch-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eno\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).issueDevSwitchTokens(anyString());
    }
}
