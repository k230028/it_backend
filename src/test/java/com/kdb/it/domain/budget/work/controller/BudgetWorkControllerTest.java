package com.kdb.it.domain.budget.work.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.service.BudgetWorkService;

/**
 * BudgetWorkController @WebMvcTest
 *
 * <p>예산작업 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
@WebMvcTest(BudgetWorkController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class BudgetWorkControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BudgetWorkService budgetWorkService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/budget/work/ioe-categories - 비인증 → 401")
    void getIoeCategories_비인증_401() throws Exception {
        mockMvc.perform(get("/api/budget/work/ioe-categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/budget/work/ioe-categories - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getIoeCategories_인증_200() throws Exception {
        given(budgetWorkService.getIoeCategories(anyString())).willReturn(List.of());
        mockMvc.perform(get("/api/budget/work/ioe-categories").param("bgYy", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/budget/work/apply - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void apply_인증_200() throws Exception {
        given(budgetWorkService.applyRates(any())).willReturn(null);
        mockMvc.perform(post("/api/budget/work/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BudgetWorkDto.ApplyRequest("2026", List.of()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/budget/work/apply-items - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void applyItems_인증_200() throws Exception {
        given(budgetWorkService.applyItemRates(any())).willReturn(null);
        mockMvc.perform(post("/api/budget/work/apply-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BudgetWorkDto.ItemApplyRequest("2026", List.of()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/budget/work/summary - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getSummary_인증_200() throws Exception {
        given(budgetWorkService.getSummary(anyString())).willReturn(null);
        mockMvc.perform(get("/api/budget/work/summary").param("bgYy", "2026"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/budget/work/project-summary - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getProjectSummary_인증_200() throws Exception {
        given(budgetWorkService.getProjectSummary(anyString())).willReturn(null);
        mockMvc.perform(get("/api/budget/work/project-summary").param("bgYy", "2026"))
                .andExpect(status().isOk());
    }
}
