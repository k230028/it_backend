package com.kdb.it.domain.council.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CouncilApprovalService;
import com.kdb.it.domain.council.service.CouncilService;
import com.kdb.it.domain.council.service.CouncilSkipService;
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
    private CouncilSkipService councilSkipService;
    @MockitoBean
    private QnaService qnaService;
    @MockitoBean
    private MainQnaService mainQnaService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    /** 핵심 필드 제약을 충족하는 타당성검토표 요청 (kpnTc 필수) */
    private static CouncilDto.FeasibilityRequest validFeasibilityRequest() {
        return new CouncilDto.FeasibilityRequest(
                null, null, null, null, null, null, null, null, null,
                "TEMP", null, null);
    }

    /** 핵심 필드 제약을 충족하는 평가위원 선정 요청 (members 비어있지 않음) */
    private static CouncilDto.CommitteeRequest validCommitteeRequest() {
        return new CouncilDto.CommitteeRequest(
                "INFO_SYS",
                List.of(new CouncilDto.CommitteeMemberRequest("10002", "MAND")));
    }

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
                .content(objectMapper.writeValueAsString(
                        new CouncilDto.CreateRequest("PRJ-2026-0001", 1, "INFO_SYS"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council - prjMngNo 누락 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createCouncil_prjMngNo누락_400() throws Exception {
        var body = new CouncilDto.CreateRequest(null, null, null);
        mockMvc.perform(post("/api/council")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
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
    // M3-1: 본회의 Q&A
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/main-qna - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getMainQnaList_인증_200() throws Exception {
        given(mainQnaService.getMainQnaList(ASCT_ID)).willReturn(List.of(
                new CouncilDto.QnaResponse("MQT-1", "10001", "홍길동", "질의", null, null, null, "N")));

        mockMvc.perform(get("/api/council/" + ASCT_ID + "/main-qna"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].qtnId").value("MQT-1"))
                .andExpect(jsonPath("$[0].qtnCone").value("질의"));
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/main-qna - ADMIN → 생성 ID 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createMainQna_관리자_200() throws Exception {
        given(mainQnaService.createMainQna(anyString(), any(), any())).willReturn("MQT-1");

        mockMvc.perform(post("/api/council/" + ASCT_ID + "/main-qna")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouncilDto.QnaCreateRequest("질의"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("MQT-1"));
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/main-qna/{qtnId} - ADMIN → 200")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updateMainQna_관리자_200() throws Exception {
        mockMvc.perform(patch("/api/council/" + ASCT_ID + "/main-qna/MQT-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouncilDto.QnaUpdateRequest("수정"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/main-qna/{qtnId} - ADMIN → 200")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void replyMainQna_관리자_200() throws Exception {
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/main-qna/MQT-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouncilDto.QnaReplyRequest("답변"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/council/{asctId}/main-qna/{qtnId} - ADMIN → 204")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void deleteMainQna_관리자_204() throws Exception {
        mockMvc.perform(delete("/api/council/" + ASCT_ID + "/main-qna/MQT-1"))
                .andExpect(status().isNoContent());
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
                .content(objectMapper.writeValueAsString(validFeasibilityRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/feasibility - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void updateFeasibility_인증_200() throws Exception {
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/feasibility")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validFeasibilityRequest())))
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
                .content(objectMapper.writeValueAsString(validCommitteeRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/committee - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void updateCommittee_인증_200() throws Exception {
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/committee")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCommitteeRequest())))
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
        var request = new CouncilDto.ScheduleRequest(
                List.of(new CouncilDto.ScheduleItem("20260701", "10:00", "Y")), "N");
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/schedule/confirm - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void confirmSchedule_인증_200() throws Exception {
        var request = new CouncilDto.ScheduleConfirmRequest(
                java.time.LocalDate.of(2026, 7, 1), "10:00", "회의실 A");
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/schedule/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
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
                .content(objectMapper.writeValueAsString(new CouncilDto.EvaluationRequest(
                        List.of(new CouncilDto.EvaluationItem("CK01", 5, "적정"))))))
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
    // PRD_c_20260620 #2: 개최준비 시작
    // =========================================================================

    /**
     * startPreparation: PATCH /api/council/{asctId}/start-preparation
     * councilService.startPreparation(asctId) 위임 확인
     */
    @Test
    @DisplayName("PATCH /api/council/{asctId}/start-preparation - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void startPreparation_인증_200() throws Exception {
        // Arrange: 서비스 스텁은 void — 별도 설정 불필요

        // Act & Assert
        mockMvc.perform(patch("/api/council/" + ASCT_ID + "/start-preparation"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // PRD_c_20260620 #3: 타당성검토 생략 판정 요청
    // =========================================================================

    /**
     * createSkipRequest: POST /api/council/{asctId}/skip-request
     * councilSkipService.createSkipRequest(asctId, request, userDetails) 위임 확인
     */
    @Test
    @DisplayName("POST /api/council/{asctId}/skip-request - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void createSkipRequest_인증_200() throws Exception {
        // Arrange
        var body = new CouncilDto.SkipRequestCreate("01", "보안강화 사유", "FL-2026-00000001");

        // Act & Assert
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/skip-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    /**
     * decideSkipRequest: POST /api/council/{asctId}/skip-request/decision
     * councilSkipService.submitDecision(asctId, request, userDetails) 위임 확인
     */
    @Test
    @DisplayName("POST /api/council/{asctId}/skip-request/decision - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void decideSkipRequest_인증_200() throws Exception {
        // Arrange
        var body = new CouncilDto.SkipDecisionRequest("Y", "생략 가능", List.of("E10001", "E10002"));

        // Act & Assert
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/skip-request/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    /**
     * getSkipRequests: GET /api/council/skip-requests
     * councilSkipService.getActiveSkipRequests() 위임 및 배열 응답 확인
     */
    @Test
    @DisplayName("GET /api/council/skip-requests - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getSkipRequests_인증_200() throws Exception {
        // Arrange
        var response = new CouncilDto.SkipRequestResponse(
                ASCT_ID, "01", "보안강화", "FL-2026-0001",
                "10001", null, false, null, null, null, null, null);
        given(councilSkipService.getActiveSkipRequests()).willReturn(List.of(response));

        // Act & Assert
        mockMvc.perform(get("/api/council/skip-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].asctId").value(ASCT_ID));
    }

    /**
     * getSkipRequest: GET /api/council/{asctId}/skip-request
     * councilSkipService.getSkipRequest(asctId) 위임 및 단건 응답 확인
     */
    @Test
    @DisplayName("GET /api/council/{asctId}/skip-request - 인증된 사용자 → 200 + 단건 반환")
    @WithMockUser(username = "10001")
    void getSkipRequest_인증_200() throws Exception {
        // Arrange
        var response = new CouncilDto.SkipRequestResponse(
                ASCT_ID, "02", "기타 사유", "FL-2026-0002",
                "10002", null, false, null, null, null, null, null);
        given(councilSkipService.getSkipRequest(ASCT_ID)).willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/skip-request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asctId").value(ASCT_ID))
                .andExpect(jsonPath("$.rsnTc").value("02"));
    }

    // =========================================================================
    // M6: 내 일정 조회 (평가위원 본인)
    // =========================================================================

    /**
     * getMySchedule: GET /api/council/{asctId}/schedule/my
     * scheduleService.getMySchedule(asctId, eno) 위임 및 배열 응답 확인
     */
    @Test
    @DisplayName("GET /api/council/{asctId}/schedule/my - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getMySchedule_인증_200() throws Exception {
        // Arrange
        var slot = new CouncilDto.ScheduleSlotResponse("20260701", "10:00", "Y");
        given(scheduleService.getMySchedule(anyString(), anyString())).willReturn(List.of(slot));

        // Act & Assert — @AuthenticationPrincipal CustomUserDetails.getEno() 사용을 위해 실제 principal 주입
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/schedule/my")
                        .with(user(new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "D001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].dsdDt").value("20260701"));
    }

    // =========================================================================
    // PRD_c_20260620 #1: 서면개최 확정
    // =========================================================================

    /**
     * confirmWrittenMeeting: PUT /api/council/{asctId}/schedule/confirm-written
     * scheduleService.confirmWrittenMeeting(asctId) 위임 확인
     */
    @Test
    @DisplayName("PUT /api/council/{asctId}/schedule/confirm-written - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void confirmWrittenMeeting_인증_200() throws Exception {
        // Arrange: void 서비스 — 스텁 불필요

        // Act & Assert
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/schedule/confirm-written"))
                .andExpect(status().isOk());
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
        var item = new CouncilDto.EvaluationItemResponse(
                "10001", "홍길동", "CK01", "필요성", 5, "적정");
        given(evaluationService.getMyEvaluation(anyString(), any())).willReturn(List.of(item));

        // Act & Assert
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/evaluation/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].ckgItmC").value("CK01"));
    }

    // =========================================================================
    // M7: 결과서 검토 동기화 및 본인 확인 여부
    // =========================================================================

    /**
     * syncReviewStatus: POST /api/council/{asctId}/result/review/sync
     * resultService.syncReviewStatus(asctId) 위임 및 Boolean 응답 확인
     */
    @Test
    @DisplayName("POST /api/council/{asctId}/result/review/sync - 인증된 사용자 → 200 + Boolean 반환")
    @WithMockUser(username = "10001")
    void syncReviewStatus_인증_200() throws Exception {
        // Arrange
        given(resultService.syncReviewStatus(ASCT_ID)).willReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/result/review/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    /**
     * getMyResultReview: GET /api/council/{asctId}/result/review/my
     * resultService.getMyReviewStatus(asctId, userDetails) 위임 및 Boolean 응답 확인
     */
    @Test
    @DisplayName("GET /api/council/{asctId}/result/review/my - 인증된 사용자 → 200 + Boolean 반환")
    @WithMockUser(username = "10001")
    void getMyResultReview_인증_200() throws Exception {
        // Arrange
        given(resultService.getMyReviewStatus(anyString(), any())).willReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/result/review/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));
    }

    // =========================================================================
    // M7: 개최결과서 결재 요청
    // =========================================================================

    /**
     * requestResultApproval: POST /api/council/{asctId}/result/approval
     * councilApprovalService.requestResultApproval(asctId, request, userDetails) 위임 확인
     */
    @Test
    @DisplayName("POST /api/council/{asctId}/result/approval - 인증된 사용자 → 200 + 신청관리번호 반환")
    @WithMockUser(username = "10001")
    void requestResultApproval_인증_200() throws Exception {
        // Arrange
        var approval = new CouncilDto.ApprovalResponse("APF_202600000099");
        given(councilApprovalService.requestResultApproval(anyString(), any(), any()))
                .willReturn(approval);
        var body = new CouncilDto.ResultApprovalRequest("E10001", "E10002", "결재요청합니다");

        // Act & Assert
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/result/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apfMngNo").value("APF_202600000099"));
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
