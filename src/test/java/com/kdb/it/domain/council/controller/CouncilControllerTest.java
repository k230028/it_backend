package com.kdb.it.domain.council.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CouncilApprovalService;
import com.kdb.it.domain.council.service.CouncilService;
import com.kdb.it.domain.council.service.CommitteeService;
import com.kdb.it.domain.council.service.EvaluationService;
import com.kdb.it.domain.council.service.FeasibilityService;
import com.kdb.it.domain.council.service.MainQnaService;
import com.kdb.it.domain.council.service.QnaService;
import com.kdb.it.domain.council.service.ResultService;
import com.kdb.it.domain.council.service.ScheduleService;

/**
 * CouncilController @WebMvcTest
 *
 * <p>정보화실무협의회 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
@WebMvcTest({ CouncilController.class, CouncilMainQnaController.class, CouncilQnaController.class })
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class CouncilControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CouncilService councilService;
    @MockitoBean
    private FeasibilityService feasibilityService;
    @MockitoBean
    private CouncilApprovalService councilApprovalService;
    @MockitoBean
    private CommitteeService committeeService;
    @MockitoBean
    private ScheduleService scheduleService;
    @MockitoBean
    private EvaluationService evaluationService;
    @MockitoBean
    private ResultService resultService;
    @MockitoBean
    private QnaService qnaService;
    @MockitoBean
    private MainQnaService mainQnaService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // =========================================================================
    // M3: 기본 CRUD
    // =========================================================================

    @Test
    @DisplayName("GET /api/council - 비인증 → 401")
    void getCouncilList_비인증_401() throws Exception {
        mockMvc.perform(get("/api/council"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/council - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getCouncilList_인증_200() throws Exception {
        given(councilService.getCouncilList(any())).willReturn(List.of());
        mockMvc.perform(get("/api/council"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/council - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void createCouncil_인증_200() throws Exception {
        given(councilService.createCouncil(any(), any())).willReturn(ASCT_ID);
        mockMvc.perform(post("/api/council")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/council/{asctId} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getCouncil_인증_200() throws Exception {
        given(councilService.getCouncil(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // M4: 타당성검토표
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/feasibility - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getFeasibility_인증_200() throws Exception {
        given(feasibilityService.getFeasibility(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/feasibility"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/feasibility - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void saveFeasibility_인증_200() throws Exception {
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/feasibility")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/feasibility - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void updateFeasibility_인증_200() throws Exception {
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/feasibility")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // M5: 전자결재
    // =========================================================================

    @Test
    @DisplayName("POST /api/council/{asctId}/approval - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void requestApproval_인증_200() throws Exception {
        given(councilApprovalService.requestApproval(anyString(), any(), any()))
                .willReturn(new CouncilDto.ApprovalResponse("APF_20260001"));
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/approval")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CouncilDto.ApprovalRequest("E20001", "결재요청합니다"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/approval - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void processApprovalCallback_인증_200() throws Exception {
        mockMvc.perform(patch("/api/council/" + ASCT_ID + "/approval")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CouncilDto.ApprovalCallbackRequest(true))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/start - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void startCouncil_인증_200() throws Exception {
        mockMvc.perform(patch("/api/council/" + ASCT_ID + "/start"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/complete - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void completeCouncil_인증_200() throws Exception {
        mockMvc.perform(patch("/api/council/" + ASCT_ID + "/complete"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/skip - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void skipCouncil_인증_200() throws Exception {
        mockMvc.perform(patch("/api/council/" + ASCT_ID + "/skip"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // M6: 평가위원
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/committee/default - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getDefaultCommittee_인증_200() throws Exception {
        given(committeeService.getDefaultCommittee(ASCT_ID)).willReturn(List.of());
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/committee/default"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/council/{asctId}/committee - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getCommittee_인증_200() throws Exception {
        given(committeeService.getCommittee(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/committee"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/committee - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void saveCommittee_인증_200() throws Exception {
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/committee")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/committee - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void updateCommittee_인증_200() throws Exception {
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/committee")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // M6: 일정
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/schedule - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getScheduleStatus_인증_200() throws Exception {
        given(scheduleService.getScheduleStatus(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/schedule"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/schedule - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void submitSchedule_인증_200() throws Exception {
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/schedule/confirm - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void confirmSchedule_인증_200() throws Exception {
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/schedule/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // M7: 평가의견
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/evaluation - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getAllEvaluations_인증_200() throws Exception {
        given(evaluationService.getAllEvaluations(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/evaluation"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/evaluation - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void saveEvaluation_인증_200() throws Exception {
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/evaluation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CouncilDto.EvaluationRequest(List.of()))))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // M7: 결과서
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/result - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getResult_인증_200() throws Exception {
        given(resultService.getResult(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/result"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/result - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void saveResult_인증_200() throws Exception {
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/result - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void updateResult_인증_200() throws Exception {
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/result/confirm - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void confirmResult_인증_200() throws Exception {
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/result/confirm"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/result/review - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void reviewResult_인증_200() throws Exception {
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/result/review"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/notify - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void notifyCouncil_인증_200() throws Exception {
        given(councilService.notifyCouncil(ASCT_ID)).willReturn(null);
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/notify"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // M6: 사전질의응답
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/qna - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getQnaList_인증_200() throws Exception {
        given(qnaService.getQnaList(ASCT_ID)).willReturn(List.of());
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/qna"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/qna - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void createQna_인증_200() throws Exception {
        given(qnaService.createQna(anyString(), any(), any())).willReturn("QTN-ASCT-2026-0001-01");
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/qna")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/qna/{qtnId} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void replyQna_인증_200() throws Exception {
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/qna/QTN-ASCT-2026-0001-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }
}
