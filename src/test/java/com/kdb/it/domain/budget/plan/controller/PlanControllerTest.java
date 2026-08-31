package com.kdb.it.domain.budget.plan.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.kdb.it.domain.budget.plan.service.PlanVersionService;
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
    @MockitoBean private PlanVersionService planVersionService;
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
    @WithMockUser(username = "10001")
    void getPlan_인증_200() throws Exception {
        given(planService.getPlan(org.mockito.ArgumentMatchers.eq("PLN-2026-0001"), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PlanDto.DetailResponse());
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
    @DisplayName("POST /api/plans/{plnMngNo}/reapplications - 결재완료 최종본의 초안을 생성한다")
    @WithMockUser(username = "10001")
    void createPlanReapplication_인증_201() throws Exception {
        given(
                        planVersionService.createReapplication(
                                org.mockito.ArgumentMatchers.eq("PLN-2026-0001"),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(new PlanVersionService.PlanVersion("PLN-2026-0001", 2, "N"));

        mockMvc.perform(post("/api/plans/PLN-2026-0001/reapplications"))
                .andExpect(status().isCreated())
                .andExpect(
                        header()
                                .string(
                                        "Location",
                                        "http://localhost/api/plans/PLN-2026-0001/versions/2"))
                .andExpect(jsonPath("$.reqDocNo").value("PLN-2026-0001"))
                .andExpect(jsonPath("$.sno").value(2))
                .andExpect(jsonPath("$.lstYn").value("N"));
    }

    @Test
    @DisplayName("GET /api/plans/{plnMngNo}/history - 작성부서 이력을 반환한다")
    @WithMockUser(username = "10001")
    void getPlanHistory_인증_200() throws Exception {
        given(
                        planVersionService.findHistory(
                                org.mockito.ArgumentMatchers.eq("PLN-2026-0001"),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        List.of(
                                PlanDto.VersionResponse.builder()
                                        .reqDocNo("PLN-2026-0001")
                                        .sno(1)
                                        .lstYn("Y")
                                        .approvalStatus("02")
                                        .build()));

        mockMvc.perform(get("/api/plans/PLN-2026-0001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sno").value(1))
                .andExpect(jsonPath("$[0].approvalStatus").value("02"));
    }

    @Test
    @DisplayName("GET /api/plans/{plnMngNo}/versions/{sno} - 명시 순번 상세를 반환한다")
    @WithMockUser(username = "10001")
    void getPlanVersion_인증_200() throws Exception {
        given(
                        planService.getPlanVersion(
                                org.mockito.ArgumentMatchers.eq("PLN-2026-0001"),
                                org.mockito.ArgumentMatchers.eq(2),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        PlanDto.DetailResponse.builder()
                                .reqDocNo("PLN-2026-0001")
                                .sno(2)
                                .lstYn("N")
                                .build());

        mockMvc.perform(get("/api/plans/PLN-2026-0001/versions/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sno").value(2))
                .andExpect(jsonPath("$.lstYn").value("N"));
    }
}
