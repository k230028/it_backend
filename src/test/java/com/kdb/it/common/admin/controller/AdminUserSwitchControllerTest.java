package com.kdb.it.common.admin.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserSwitchController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    AdminUserSwitchControllerTest.MethodSecurityConfig.class
})
class AdminUserSwitchControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private AuthService authService;
    @MockitoBean private CookieUtil cookieUtil;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("사용자 전환 API는 비인증 요청을 거부한다")
    void switchUsers_비인증_401() throws Exception {
        mockMvc.perform(get("/api/admin/switch-users")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("사용자 전환 API는 일반 사용자 요청을 거부한다")
    void switchUsers_일반사용자_403() throws Exception {
        mockMvc.perform(get("/api/admin/switch-users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자에게 삭제되지 않은 사용자 목록을 반환한다")
    void switchUsers_관리자_활성사용자반환() throws Exception {
        CuserI active = CuserI.builder().eno("10001").usrNm("홍길동").delYn("N").build();
        CuserI deleted = CuserI.builder().eno("99999").usrNm("삭제자").delYn("Y").build();
        given(userRepository.findAllByOrderByUsrNmAsc()).willReturn(List.of(active, deleted));

        mockMvc.perform(get("/api/admin/switch-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eno").value("10001"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자가 선택한 직원의 인증 쿠키를 발급한다")
    void switchUser_관리자_쿠키발급() throws Exception {
        AuthDto.LoginResponse response =
                AuthDto.LoginResponse.builder()
                        .eno("10001")
                        .empNm("홍길동")
                        .accessToken("access")
                        .refreshToken("refresh")
                        .build();
        given(authService.issueUserSwitchTokens("10001")).willReturn(response);
        given(cookieUtil.createAccessTokenCookie("access"))
                .willReturn(ResponseCookie.from("access_token", "access").path("/").build());
        given(cookieUtil.createRefreshTokenCookie("refresh"))
                .willReturn(ResponseCookie.from("refresh_token", "refresh").path("/").build());
        given(cookieUtil.createUserInfoCookie(response))
                .willReturn(ResponseCookie.from("it-portal-user", "user").path("/").build());

        mockMvc.perform(
                        post("/api/admin/switch-user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("eno", "10001"))))
                .andExpect(status().isOk())
                .andExpect(
                        header().stringValues(
                                        "Set-Cookie",
                                        "access_token=access; Path=/",
                                        "refresh_token=refresh; Path=/",
                                        "it-portal-user=user; Path=/"))
                .andExpect(jsonPath("$.eno").value("10001"));
    }
}
