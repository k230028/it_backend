package com.kdb.it.common.approval.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.dto.ApprovalHomeInboxDto;
import com.kdb.it.common.approval.service.ApplicationService;
import com.kdb.it.common.approval.service.ApprovalHomeInboxService;
import com.kdb.it.common.approval.service.ApprovalLineManagementService;
import com.kdb.it.common.approval.service.ApprovalLineSuggestionService;
import com.kdb.it.common.approval.service.PendingApproverService;
import com.kdb.it.common.mfa.security.MfaGuardConfiguration;
import com.kdb.it.common.mfa.service.MfaService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ApplicationController @WebMvcTest
 *
 * <p>전자결재 신청 HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest(ApplicationController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    MfaGuardConfiguration.class,
    CookieUtil.class
})
class ApplicationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ApplicationService applicationService;
    @MockitoBean private ApprovalHomeInboxService approvalHomeInboxService;
    @MockitoBean private PendingApproverService pendingApproverService;
    @MockitoBean private ApprovalLineManagementService approvalLineManagementService;
    @MockitoBean private ApprovalLineSuggestionService approvalLineSuggestionService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private MfaService mfaService;

    private static final CustomUserDetails USER =
            new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "D001");

    private static final Cookie MFA_PROOF = new Cookie("mfa-proof", "valid-proof");

    @Test
    @DisplayName("GET /api/applications - 비인증 → 401")
    void getApplications_비인증_401() throws Exception {
        mockMvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/applications - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getApplications_인증_200() throws Exception {
        given(applicationService.getApplications()).willReturn(List.of());
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/applications/pending - 비인증 → 401")
    void getPendingApplications_비인증_401() throws Exception {
        mockMvc.perform(get("/api/applications/pending")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/applications/pending - 인증 주체 사번으로 조회한다")
    @WithMockUser(username = "10001")
    void getPendingApplications_인증_200() throws Exception {
        given(applicationService.getPendingApplications("10001")).willReturn(List.of());
        mockMvc.perform(get("/api/applications/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/applications/home-inbox - 인증 주체의 결재함·기안함만 조회한다")
    @WithMockUser(username = "10001")
    void getHomeInbox_인증주체_200() throws Exception {
        given(approvalHomeInboxService.getHomeInbox("10001"))
                .willReturn(
                        new ApprovalHomeInboxDto.Response(
                                List.of(
                                        new ApprovalHomeInboxDto.Item(
                                                "APF-001",
                                                "결재 대기 문서",
                                                "김기안",
                                                java.time.LocalDate.of(2026, 9, 1),
                                                "1",
                                                "결재중",
                                                true)),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()));

        mockMvc.perform(get("/api/applications/home-inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalPending[0].apfMngNo").value("APF-001"))
                .andExpect(jsonPath("$.approvalPending[0].actionable").value(true));
        verify(approvalHomeInboxService).getHomeInbox("10001");
    }

    @Test
    @DisplayName("GET /api/applications/home-inbox - 비인증 사용자는 조회할 수 없다")
    void getHomeInbox_비인증_401() throws Exception {
        mockMvc.perform(get("/api/applications/home-inbox")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/applications/pending-count - 인증 주체와 결재상태를 서비스에 전달한다")
    void getPendingCount_인증_200() throws Exception {
        given(applicationService.getPendingCount("2027", "1", USER))
                .willReturn(ApplicationDto.PendingCountResponse.builder().build());
        mockMvc.perform(
                        get("/api/applications/pending-count")
                                .with(user(USER))
                                .param("bgYy", "2027")
                                .param("apfSts", "1"))
                .andExpect(status().isOk());
        verify(applicationService).getPendingCount("2027", "1", USER);
    }

    @Test
    @DisplayName("GET /api/applications/{apfMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getApplication_인증_200() throws Exception {
        given(applicationService.getApplication("APF_20260001"))
                .willReturn(ApplicationDto.Response.builder().build());
        mockMvc.perform(get("/api/applications/APF_20260001")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/applications/bulk-get - 인증된 사용자 → 200 + items/failedIds 반환")
    @WithMockUser(username = "10001")
    void bulkGet_인증_200() throws Exception {
        given(applicationService.getApplicationsByIds(any()))
                .willReturn(new ApplicationDto.BulkResponse(List.of(), List.of()));
        mockMvc.perform(
                        post("/api/applications/bulk-get")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ApplicationDto.BulkGetRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.failedIds").isArray());
    }

    @Test
    @DisplayName("POST /api/applications/{apfMngNo}/approve - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void approve_인증_200() throws Exception {
        mockMvc.perform(
                        post("/api/applications/APF_20260001/approve")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ApplicationDto.ApproveRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/applications/bulk-approve - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void bulkApprove_인증_200() throws Exception {
        given(applicationService.bulkApprove(any()))
                .willReturn(ApplicationDto.BulkApproveResponse.builder().build());
        mockMvc.perform(
                        post("/api/applications/bulk-approve")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ApplicationDto.BulkApproveRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/applications/dashboard - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getDashboard_인증_200() throws Exception {
        given(applicationService.getDashboard(anyString(), anyString()))
                .willReturn(new ApplicationDto.DashboardResponse());
        mockMvc.perform(
                        get("/api/applications/dashboard")
                                .param("bbrC", "IT001")
                                .param("eno", "E10001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/applications/approval-badge - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getApprovalBadge_인증_200() throws Exception {
        given(applicationService.getApprovalBadgeCount(anyString(), anyString()))
                .willReturn(new ApplicationDto.ApprovalBadgeCountResponse());
        mockMvc.perform(
                        get("/api/applications/approval-badge")
                                .param("bbrC", "IT001")
                                .param("eno", "E10001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/applications/{apfMngNo}/apfDtlCone - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getApfDtlCone_인증_200() throws Exception {
        // 준비
        given(applicationService.getApfDtlCone("APF_202600000001"))
                .willReturn(ApplicationDto.ApfDtlConeResponse.builder().build());

        // 실행 및 검증
        mockMvc.perform(get("/api/applications/APF_202600000001/apfDtlCone"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/applications/{apfMngNo}/apfDtlCone - 비인증 → 401")
    void getApfDtlCone_비인증_401() throws Exception {
        mockMvc.perform(get("/api/applications/APF_202600000001/apfDtlCone"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "10001")
    void corruptDetailReturnsServerErrorWithoutRawParserContent() throws Exception {
        given(applicationService.getApfDtlCone("APF_202600000001"))
                .willAnswer(
                        i ->
                                com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture
                                        .reader()
                                        .read("{\"private\":SECRET_BODY}"));
        var response =
                mockMvc.perform(get("/api/applications/APF_202600000001/apfDtlCone"))
                        .andExpect(status().isInternalServerError())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(response).doesNotContain("SECRET_BODY", "JsonParseException", "apfDtlCone");
    }

    @Test
    @DisplayName("POST /api/applications - 신규 신청서 생성 → 201 Created + Location 헤더")
    @WithMockUser(username = "10001")
    void submit_인증_201() throws Exception {
        // 준비
        given(applicationService.submit(any())).willReturn("APF_202600000001");

        // 실행 및 검증
        mockMvc.perform(
                        post("/api/applications")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ApplicationDto.CreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(
                        header().string(
                                        "Set-Cookie",
                                        org.hamcrest.Matchers.containsString("Max-Age=0")));
        verify(mfaService).consumeApprovalProof(USER, "valid-proof");
    }

    @Test
    @DisplayName("POST /api/applications - 비인증 → 401")
    void submit_비인증_401() throws Exception {
        mockMvc.perform(
                        post("/api/applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/applications/{apfMngNo}/recall - 일반 사용자 회수 → 204 + 관리자 아님 전달")
    @WithMockUser(username = "10001", roles = "USER")
    void recall_일반사용자_204() throws Exception {
        // 준비
        ArgumentCaptor<ApplicationDto.RecallRequest> requestCaptor =
                ArgumentCaptor.forClass(ApplicationDto.RecallRequest.class);

        // 실행
        mockMvc.perform(
                        post("/api/applications/APF_202600000001/recall")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recallOpnn\":\"결재선 오기재\"}"))
                .andExpect(status().isNoContent());

        // 검증
        verify(applicationService)
                .recall(eq("APF_202600000001"), requestCaptor.capture(), eq("10001"), eq(false));
        assertThat(requestCaptor.getValue().getRecallOpnn()).isEqualTo("결재선 오기재");
    }

    @Test
    @DisplayName("POST /api/applications/{apfMngNo}/recall - 관리자 회수 → 204 + 관리자 권한 전달")
    @WithMockUser(
            username = "90001",
            roles = {"USER", "ADMIN"})
    void recall_관리자_204() throws Exception {
        // 준비
        String requestBody = "{\"recallOpnn\":\"관리자 직권 회수\"}";

        // 실행
        CustomUserDetails admin =
                new CustomUserDetails("90001", List.of(CustomUserDetails.ATH_ADMIN), "D001");
        mockMvc.perform(
                        post("/api/applications/APF_202600000002/recall")
                                .with(user(admin))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isNoContent());

        // 검증
        verify(applicationService).recall(eq("APF_202600000002"), any(), eq("90001"), eq(true));
    }

    @Test
    @DisplayName("POST /api/applications/{apfMngNo}/recall - 회수 사유 공백 → 400")
    @WithMockUser(username = "10001", roles = "USER")
    void recall_회수사유공백_400() throws Exception {
        // 준비
        String requestBody = "{\"recallOpnn\":\"   \"}";

        // 실행
        mockMvc.perform(
                        post("/api/applications/APF_202600000001/recall")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isBadRequest());

        // 검증
        verify(applicationService, never()).recall(anyString(), any(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("POST recall - 본문 검증 실패는 proof를 소비하지 않고 수정 후 동일 proof로 성공한다")
    void recall_본문검증실패_동일Proof재시도성공() throws Exception {
        mockMvc.perform(
                        post("/api/applications/APF_202600000001/recall")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recallOpnn\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        post("/api/applications/APF_202600000001/recall")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recallOpnn\":\"결재 의견 수정\"}"))
                .andExpect(status().isNoContent());

        verify(mfaService, times(1)).consumeApprovalProof(USER, "valid-proof");
        verify(applicationService).recall(eq("APF_202600000001"), any(), eq("10001"), eq(false));
    }

    @Test
    @DisplayName("POST /api/applications/{apfMngNo}/recall - 비인증 → 401")
    void recall_비인증_401() throws Exception {
        // 준비
        String requestBody = "{\"recallOpnn\":\"결재선 오기재\"}";

        // 실행
        mockMvc.perform(
                        post("/api/applications/APF_202600000001/recall")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isUnauthorized());

        // 검증
        verify(applicationService, never()).recall(anyString(), any(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("POST /api/applications - MFA proof 없음 → 401 + 서비스 미호출")
    void submit_MFA증표없음_서비스미호출() throws Exception {
        mockMvc.perform(
                        post("/api/applications")
                                .with(user(USER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ApplicationDto.CreateRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED"))
                .andExpect(
                        header().string(
                                        "Set-Cookie",
                                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(applicationService, never()).submit(any());
    }

    @Test
    @DisplayName("PATCH /api/applications/{apfMngNo}/approvers/order - 미결재 순서 변경 → 204")
    @WithMockUser(username = "10001", roles = "USER")
    void reorderApprovers_인증_204() throws Exception {
        mockMvc.perform(
                        patch("/api/applications/APF_202600000001/approvers/order")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"orderedDcdSqns\":[3,2]}"))
                .andExpect(status().isNoContent());

        verify(approvalLineManagementService)
                .reorderPendingApprovers(
                        eq("APF_202600000001"), eq(List.of(3, 2)), eq("10001"), eq(false));
    }

    // ───────────────────────────────────────────────────────
    // PATCH /{apfMngNo}/approvers/{dcdSqn} — 미결재 결재자 변경
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/applications/{apfMngNo}/approvers/{dcdSqn} - 일반 사용자 변경 → 204")
    @WithMockUser(username = "10001", roles = "USER")
    void changePendingApprover_일반사용자_204() throws Exception {
        // 실행
        mockMvc.perform(
                        patch("/api/applications/APF_202600000001/approvers/2")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"newApproverEno\":\"E777\"}"))
                .andExpect(status().isNoContent());

        // 검증: 관리자 아님(false)으로 전달
        verify(pendingApproverService)
                .changePendingApprover(
                        eq("APF_202600000001"), eq(2), eq("E777"), eq("10001"), eq(false));
    }

    @Test
    @DisplayName("PATCH /api/applications/{apfMngNo}/approvers/{dcdSqn} - 관리자 변경 → 204 + 관리자 권한 전달")
    @WithMockUser(
            username = "90001",
            roles = {"USER", "ADMIN"})
    void changePendingApprover_관리자_204() throws Exception {
        // 준비
        CustomUserDetails admin =
                new CustomUserDetails("90001", List.of(CustomUserDetails.ATH_ADMIN), "D001");

        // 실행
        mockMvc.perform(
                        patch("/api/applications/APF_202600000001/approvers/3")
                                .with(user(admin))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"newApproverEno\":\"E888\"}"))
                .andExpect(status().isNoContent());

        // 검증: 관리자(true)로 전달
        verify(pendingApproverService)
                .changePendingApprover(
                        eq("APF_202600000001"), eq(3), eq("E888"), eq("90001"), eq(true));
    }

    @Test
    @DisplayName("PATCH /api/applications/{apfMngNo}/approvers/{dcdSqn} - 사번 공백 → 400 + 서비스 미호출")
    @WithMockUser(username = "10001", roles = "USER")
    void changePendingApprover_사번공백_400() throws Exception {
        // 실행
        mockMvc.perform(
                        patch("/api/applications/APF_202600000001/approvers/2")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"newApproverEno\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // 검증
        verify(pendingApproverService, never())
                .changePendingApprover(
                        anyString(), anyInt(), anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("PATCH /api/applications/{apfMngNo}/approvers/{dcdSqn} - 비인증 → 401")
    void changePendingApprover_비인증_401() throws Exception {
        mockMvc.perform(
                        patch("/api/applications/APF_202600000001/approvers/2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"newApproverEno\":\"E777\"}"))
                .andExpect(status().isUnauthorized());

        verify(pendingApproverService, never())
                .changePendingApprover(
                        anyString(), anyInt(), anyString(), anyString(), anyBoolean());
    }

    // ───────────────────────────────────────────────────────
    // PUT /{apfMngNo}/approvers — 미결재 결재선 일괄 변경
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/applications/{apfMngNo}/approvers - MFA 결재선 일괄 변경을 서비스에 전달한다")
    @WithMockUser(username = "10001", roles = "USER")
    void replacePendingApprovers_일반사용자_204() throws Exception {
        mockMvc.perform(
                        put("/api/applications/APF_202600000001/approvers")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"approverEnos\":[\"20001\",\"20002\"]}"))
                .andExpect(status().isNoContent());

        verify(approvalLineManagementService)
                .replacePendingApprovers(
                        eq("APF_202600000001"),
                        eq(List.of("20001", "20002")),
                        eq("10001"),
                        eq(false));
    }

    @Test
    @DisplayName("PUT /api/applications/{apfMngNo}/approvers - 빈 결재선은 400이고 서비스를 호출하지 않는다")
    @WithMockUser(username = "10001", roles = "USER")
    void replacePendingApprovers_빈목록_400() throws Exception {
        mockMvc.perform(
                        put("/api/applications/APF_202600000001/approvers")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"approverEnos\":[]}"))
                .andExpect(status().isBadRequest());

        verify(approvalLineManagementService, never())
                .replacePendingApprovers(anyString(), any(), anyString(), anyBoolean());
    }

    // ───────────────────────────────────────────────────────
    // POST /{apfMngNo}/approvers — 추가 결재자 등록
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/applications/{apfMngNo}/approvers - 결재선 참여자 추가 → 204")
    @WithMockUser(username = "10001", roles = "USER")
    void addApprover_일반사용자_204() throws Exception {
        // 실행
        mockMvc.perform(
                        post("/api/applications/APF_202600000001/approvers")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"approverEno\":\"E555\"}"))
                .andExpect(status().isNoContent());

        // 검증
        verify(approvalLineManagementService)
                .addApprover(eq("APF_202600000001"), eq("E555"), eq("10001"), eq(false));
    }

    @Test
    @DisplayName("POST /api/applications/{apfMngNo}/approvers - 권한 없는 사용자 → 403")
    @WithMockUser(username = "10001", roles = "USER")
    void addApprover_권한거부_403() throws Exception {
        // 준비: 서비스가 접근 거부 예외를 던지도록 설정
        willThrow(new org.springframework.security.access.AccessDeniedException("결재선 변경 권한이 없습니다."))
                .given(approvalLineManagementService)
                .addApprover(anyString(), anyString(), anyString(), anyBoolean());

        // 실행 및 검증
        mockMvc.perform(
                        post("/api/applications/APF_202600000001/approvers")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"approverEno\":\"E555\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/applications/{apfMngNo}/approvers - 사번 공백 → 400 + 서비스 미호출")
    @WithMockUser(username = "10001", roles = "USER")
    void addApprover_사번공백_400() throws Exception {
        // 실행
        mockMvc.perform(
                        post("/api/applications/APF_202600000001/approvers")
                                .with(user(USER))
                                .cookie(MFA_PROOF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"approverEno\":\"\"}"))
                .andExpect(status().isBadRequest());

        // 검증
        verify(approvalLineManagementService, never())
                .addApprover(anyString(), anyString(), anyString(), anyBoolean());
    }

    // ───────────────────────────────────────────────────────
    // DELETE /{apfMngNo}/approvers/{dcdSqn} — 추가 결재자 삭제
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/applications/{apfMngNo}/approvers/{dcdSqn} - 결재선 참여자 삭제 → 204")
    @WithMockUser(username = "10001", roles = "USER")
    void deleteApprover_일반사용자_204() throws Exception {
        // 실행
        mockMvc.perform(
                        delete("/api/applications/APF_202600000001/approvers/3")
                                .with(user(USER))
                                .cookie(MFA_PROOF))
                .andExpect(status().isNoContent());

        // 검증
        verify(approvalLineManagementService)
                .deleteApprover(eq("APF_202600000001"), eq(3), eq("10001"), eq(false));
    }

    @Test
    @DisplayName(
            "DELETE /api/applications/{apfMngNo}/approvers/{dcdSqn} - 관리자 삭제 → 204 + 관리자 권한 전달")
    @WithMockUser(
            username = "90001",
            roles = {"USER", "ADMIN"})
    void deleteApprover_관리자_204() throws Exception {
        // 준비
        CustomUserDetails admin =
                new CustomUserDetails("90001", List.of(CustomUserDetails.ATH_ADMIN), "D001");

        // 실행
        mockMvc.perform(
                        delete("/api/applications/APF_202600000002/approvers/4")
                                .with(user(admin))
                                .cookie(MFA_PROOF))
                .andExpect(status().isNoContent());

        // 검증
        verify(approvalLineManagementService)
                .deleteApprover(eq("APF_202600000002"), eq(4), eq("90001"), eq(true));
    }

    @Test
    @DisplayName("DELETE /api/applications/{apfMngNo}/approvers/{dcdSqn} - 승인 완료 결재자 삭제 시도 → 400")
    @WithMockUser(username = "10001", roles = "USER")
    void deleteApprover_승인완료삭제시도_400() throws Exception {
        // 준비: 비즈니스 규칙 위반 예외 → 400 매핑 검증
        willThrow(new IllegalStateException("미결재 상태인 결재자만 삭제할 수 있습니다."))
                .given(approvalLineManagementService)
                .deleteApprover(anyString(), anyInt(), anyString(), anyBoolean());

        // 실행 및 검증
        mockMvc.perform(
                        delete("/api/applications/APF_202600000001/approvers/1")
                                .with(user(USER))
                                .cookie(MFA_PROOF))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/applications/{apfMngNo}/approvers/{dcdSqn} - 비인증 → 401")
    void deleteApprover_비인증_401() throws Exception {
        mockMvc.perform(delete("/api/applications/APF_202600000001/approvers/3"))
                .andExpect(status().isUnauthorized());

        verify(approvalLineManagementService, never())
                .deleteApprover(anyString(), anyInt(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("GET /api/applications/approval-line/suggestion - 비인증 → 401")
    void suggestApprovalLine_비인증_401() throws Exception {
        mockMvc.perform(get("/api/applications/approval-line/suggestion"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/applications/approval-line/suggestion - 인증 주체 사번으로 제안한다")
    @WithMockUser(username = "10001")
    void suggestApprovalLine_인증_200() throws Exception {
        given(approvalLineSuggestionService.suggest("10001"))
                .willReturn(ApplicationDto.ApprovalLineSuggestion.foreign());
        mockMvc.perform(get("/api/applications/approval-line/suggestion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foreignBranch").value(true));
    }
}
