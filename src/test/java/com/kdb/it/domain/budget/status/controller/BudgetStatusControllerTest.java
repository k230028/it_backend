package com.kdb.it.domain.budget.status.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.status.service.BudgetStatusService;

/**
 * BudgetStatusController @WebMvcTest
 *
 * <p>예산현황 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
@WebMvcTest(BudgetStatusController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class BudgetStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BudgetStatusService budgetStatusService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/budget/status/projects - 비인증 → 401")
    void getProjects_비인증_401() throws Exception {
        mockMvc.perform(get("/api/budget/status/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/budget/status/projects - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getProjects_인증_200() throws Exception {
        given(budgetStatusService.getProjectStatus(anyString())).willReturn(List.of());
        mockMvc.perform(get("/api/budget/status/projects").param("bgYy", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/budget/status/costs - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getCosts_인증_200() throws Exception {
        given(budgetStatusService.getCostStatus(anyString())).willReturn(List.of());
        mockMvc.perform(get("/api/budget/status/costs").param("bgYy", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/budget/status/ordinary - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getOrdinary_인증_200() throws Exception {
        given(budgetStatusService.getOrdinaryStatus(anyString())).willReturn(List.of());
        mockMvc.perform(get("/api/budget/status/ordinary").param("bgYy", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
