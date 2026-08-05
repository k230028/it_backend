package com.kdb.it.domain.council.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.EvaluationService;
import com.kdb.it.domain.council.service.PlanEvaluationService;
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
 * CouncilEvaluationController @WebMvcTest
 *
 * <p>HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest(CouncilEvaluationController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CouncilEvaluationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EvaluationService evaluationService;
    @MockitoBean private PlanEvaluationService planEvaluationService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // =========================================================================
    // M7: 평가의견
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/evaluation - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getAllEvaluations_인증_200() throws Exception {
        given(evaluationService.getAllEvaluations(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/evaluation")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/evaluation - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void saveEvaluation_인증_200() throws Exception {
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/evaluation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CouncilDto.EvaluationRequest(
                                                        List.of(
                                                                new CouncilDto.EvaluationItem(
                                                                        "CK01", 5, "적정"))))))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 계획협의회(dbrTc='02') — 심의 대상 + 사업별 적정/유보
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/plan-targets - 인증된 사용자 → 200 + 계획 요약")
    @WithMockUser(username = "10001")
    void getPlanTargets_인증_200() throws Exception {
        given(planEvaluationService.getPlanTargets(ASCT_ID))
                .willReturn(
                        new CouncilDto.PlanTargetsResponse(
                                "PLN-2026-0001", "2026", "10", List.of(), 0, false));

        mockMvc.perform(get("/api/council/" + ASCT_ID + "/plan-targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reqDocNo").value("PLN-2026-0001"))
                .andExpect(jsonPath("$.snapshotIncomplete").value(false));
    }

    @Test
    @DisplayName("GET /api/council/{asctId}/plan-evaluation - 인증된 사용자 → 200 + 평가·판정 배열")
    @WithMockUser(username = "10001")
    void getPlanEvaluations_인증_200() throws Exception {
        given(planEvaluationService.getAllEvaluations(ASCT_ID))
                .willReturn(new CouncilDto.PlanEvaluationSummaryResponse(List.of(), List.of()));

        mockMvc.perform(get("/api/council/" + ASCT_ID + "/plan-evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations").isArray())
                .andExpect(jsonPath("$.verdicts").isArray());
    }

    @Test
    @DisplayName("GET /api/council/{asctId}/plan-evaluation/my - 인증된 사용자 → 200 + 본인 평가 목록")
    @WithMockUser(username = "10001")
    void getMyPlanEvaluation_인증_200() throws Exception {
        given(planEvaluationService.getMyEvaluation(anyString(), any()))
                .willReturn(
                        List.of(
                                new CouncilDto.PlanEvaluationItemResponse(
                                        "10001", "홍길동", "ABUS-2026-0001", "Y", "적정 사유")));

        mockMvc.perform(get("/api/council/" + ASCT_ID + "/plan-evaluation/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pprtYn").value("Y"));
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/plan-evaluation - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void savePlanEvaluation_인증_200() throws Exception {
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/plan-evaluation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CouncilDto.PlanEvaluationRequest(
                                                        List.of(
                                                                new CouncilDto.PlanEvaluationItem(
                                                                        "ABUS-2026-0001",
                                                                        "Y",
                                                                        "적정 사유"))))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/plan-evaluation - items 비어있음 → 400 (@NotEmpty)")
    @WithMockUser(username = "10001")
    void savePlanEvaluation_빈항목_400() throws Exception {
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/plan-evaluation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CouncilDto.PlanEvaluationRequest(List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
            "GET /api/council/{asctId}/plan-evaluation/result-summary - 인증된 사용자 → 200 + 요약 HTML")
    @WithMockUser(username = "10001")
    void getPlanResultSummary_인증_200() throws Exception {
        given(planEvaluationService.buildResultSummary(ASCT_ID))
                .willReturn(
                        new CouncilDto.PlanResultSummaryResponse(
                                "<table></table>", List.of(), false));

        mockMvc.perform(get("/api/council/" + ASCT_ID + "/plan-evaluation/result-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryHtml").value("<table></table>"));
    }

    // =========================================================================
    // M7: 내 평가의견 조회
    // =========================================================================

    /**
     * getMyEvaluation: GET /api/council/{asctId}/evaluation/my
     * evaluationService.getMyEvaluation(asctId, userDetails) 위임 및 배열 응답 확인
     */
    @Test
    @DisplayName("GET /api/council/{asctId}/evaluation/my - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getMyEvaluation_인증_200() throws Exception {
        // Arrange
        var item = new CouncilDto.EvaluationItemResponse("10001", "홍길동", "CK01", "필요성", 5, "적정");
        given(evaluationService.getMyEvaluation(anyString(), any())).willReturn(List.of(item));

        // Act & Assert
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/evaluation/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].ckgItmC").value("CK01"));
    }
}
