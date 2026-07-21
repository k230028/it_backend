package com.kdb.it.common.system.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.dto.LoginHistoryDto;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.common.system.service.LoginHistoryService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LoginHistoryController 단위 테스트
 *
 * <p>SecurityContext에서 인증 사용자 사번을 읽어 본인 로그인 이력만 조회하는지 검증합니다.
 */
@WebMvcTest(LoginHistoryController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class LoginHistoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LoginHistoryService loginHistoryService;

    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/login-history - 인증된 사용자의 로그인 이력을 반환한다")
    @WithMockUser(username = "10001")
    void getMyLoginHistory_인증사용자_이력반환() throws Exception {
        LoginHistoryDto.Response history =
                LoginHistoryDto.Response.builder()
                        .id(1L)
                        .eno("10001")
                        .itPtlLgnTc("1")
                        .ipAddress("127.0.0.1")
                        .agtVrsCone("JUnit")
                        .loginTime(LocalDateTime.of(2026, 5, 6, 9, 0))
                        .build();
        given(loginHistoryService.getLoginHistory("10001")).willReturn(List.of(history));

        mockMvc.perform(get("/api/login-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eno").value("10001"))
                .andExpect(jsonPath("$[0].itPtlLgnTc").value("1"))
                .andExpect(jsonPath("$[0].ipAddress").value("127.0.0.1"));
    }

    @Test
    @DisplayName("GET /api/login-history - 비인증 요청은 401을 반환한다")
    void getMyLoginHistory_비인증_401반환() throws Exception {
        mockMvc.perform(get("/api/login-history")).andExpect(status().isUnauthorized());
    }
}
