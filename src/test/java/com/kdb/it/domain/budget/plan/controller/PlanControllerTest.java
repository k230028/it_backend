package com.kdb.it.domain.budget.plan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.service.PlanService;

/**
 * PlanController @WebMvcTest
 *
 * <p>정보기술부문 계획 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
@WebMvcTest(PlanController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlanService planService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/plans - 비인증 → 401")
    void getPlans_비인증_401() throws Exception {
        mockMvc.perform(get("/api/plans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/plans - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getPlans_인증_200() throws Exception {
        given(planService.getPlans()).willReturn(List.of());
        mockMvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/plans/{plnMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getPlan_인증_200() throws Exception {
        given(planService.getPlan("PLN-2026-0001")).willReturn(new PlanDto.DetailResponse());
        mockMvc.perform(get("/api/plans/PLN-2026-0001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/plans - 인증된 사용자 → 201 Created")
    @WithMockUser(username = "10001")
    void createPlan_인증_201() throws Exception {
        mockMvc.perform(post("/api/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PlanDto.CreateRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("DELETE /api/plans/{plnMngNo} - 인증된 사용자 → 204 No Content")
    @WithMockUser(username = "10001")
    void deletePlan_인증_204() throws Exception {
        mockMvc.perform(delete("/api/plans/PLN-2026-0001"))
                .andExpect(status().isNoContent());
    }
}
