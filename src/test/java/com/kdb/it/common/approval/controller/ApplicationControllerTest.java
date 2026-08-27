package com.kdb.it.common.approval.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.service.ApplicationService;
import com.kdb.it.common.approval.service.ApprovalLineManagementService;
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

import java.util.List;

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
    @MockitoBean private PendingApproverService pendingApproverService;
    @MockitoBean private ApprovalLineManagementService approvalLineManagementService;
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
    @DisplayName("GET /api/applications/pending-count - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getPendingCount_인증_200() throws Exception {
        given(applicationService.getPendingCount(null))
                .willReturn(ApplicationDto.PendingCountResponse.builder().build());
        mockMvc.perform(get("/api/applications/pending-count")).andExpect(status().isOk());
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
}
