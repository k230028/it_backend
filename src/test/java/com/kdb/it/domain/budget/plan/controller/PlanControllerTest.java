package com.kdb.it.domain.budget.plan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.service.PlanService;
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

/**
 * PlanController @WebMvcTest
 *
 * <p>정보기술부문 계획 HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest(PlanController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class PlanControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PlanService planService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/plans - 비인증 → 401")
    void getPlans_비인증_401() throws Exception {
        mockMvc.perform(get("/api/plans")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/plans - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getPlans_인증_200() throws Exception {
        given(planService.getPlans()).willReturn(List.of());
        mockMvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/plans/{plnMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getPlan_인증_200() throws Exception {
        given(planService.getPlan("PLN-2026-0001")).willReturn(new PlanDto.DetailResponse());
        mockMvc.perform(get("/api/plans/PLN-2026-0001")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/plans - 인증된 사용자 → 201 Created")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createPlan_인증_201() throws Exception {
        given(
                        planService.createPlan(
                                org.mockito.ArgumentMatchers.any(PlanDto.CreateRequest.class),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn("PLN-2026-0405");

        mockMvc.perform(
                        post("/api/plans")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new PlanDto.CreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/plans/PLN-2026-0405"))
                .andExpect(content().string("PLN-2026-0405"));
    }

    @Test
    @DisplayName("DELETE /api/plans/{plnMngNo} - 인증된 사용자 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void deletePlan_인증_204() throws Exception {
        mockMvc.perform(delete("/api/plans/PLN-2026-0001")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /api/plans/{plnMngNo} - 관리자 → 204, 서비스에 관리번호와 본문 전달")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updatePlanText_관리자_204() throws Exception {
        mockMvc.perform(
                        patch("/api/plans/PLN-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"prjDvmCone\":\"IT프로젝트 내용\",\"itBgCone\":\"IT예산 내용\"}"))
                .andExpect(status().isNoContent());

        verify(planService)
                .updatePlanText(
                        eq("PLN-2026-0001"),
                        argThat(
                                request ->
                                        "IT프로젝트 내용".equals(request.getPrjDvmCone())
                                                && "IT예산 내용".equals(request.getItBgCone())));
    }

    @Test
    @DisplayName("PATCH /api/plans/{plnMngNo} - 비인증 → 401, 서비스 미호출")
    void updatePlanText_비인증_401() throws Exception {
        mockMvc.perform(
                        patch("/api/plans/PLN-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"prjDvmCone\":\"IT프로젝트 내용\"}"))
                .andExpect(status().isUnauthorized());

        verify(planService, never()).updatePlanText(anyString(), any());
    }
}
