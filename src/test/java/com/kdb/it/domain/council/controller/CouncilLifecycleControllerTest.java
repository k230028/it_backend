package com.kdb.it.domain.council.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CouncilApprovalService;
import com.kdb.it.domain.council.service.CouncilService;
import com.kdb.it.domain.council.service.CouncilSkipService;
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
 * CouncilLifecycleController @WebMvcTest
 *
 * <p>HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest(CouncilLifecycleController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CouncilLifecycleControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CouncilService councilService;
    @MockitoBean private CouncilApprovalService councilApprovalService;
    @MockitoBean private CouncilSkipService councilSkipService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // =========================================================================
    // M5: 전자결재
    // =========================================================================

    @Test
    @DisplayName("POST /api/council/{asctId}/approval - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void requestApproval_인증_200() throws Exception {
        given(councilApprovalService.requestApproval(anyString(), any(), any()))
                .willReturn(new CouncilDto.ApprovalResponse("APF_20260001"));
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/approval")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CouncilDto.ApprovalRequest(
                                                        "E20001", "결재요청합니다"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/approval - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void processApprovalCallback_인증_200() throws Exception {
        mockMvc.perform(
                        patch("/api/council/" + ASCT_ID + "/approval")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CouncilDto.ApprovalCallbackRequest(true))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/start - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void startCouncil_인증_200() throws Exception {
        mockMvc.perform(patch("/api/council/" + ASCT_ID + "/start")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/complete - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void completeCouncil_인증_200() throws Exception {
        mockMvc.perform(patch("/api/council/" + ASCT_ID + "/complete")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/skip - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void skipCouncil_인증_200() throws Exception {
        mockMvc.perform(patch("/api/council/" + ASCT_ID + "/skip")).andExpect(status().isOk());
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
        var body = new CouncilDto.SkipRequestCreate("보안강화 사유", "FL-2026-00000001");

        // Act & Assert
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/skip-request")
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
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/skip-request/decision")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    /**
     * getSkipRequests: GET /api/council/skip-requests councilSkipService.getActiveSkipRequests() 위임
     * 및 배열 응답 확인
     */
    @Test
    @DisplayName("GET /api/council/skip-requests - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getSkipRequests_인증_200() throws Exception {
        // Arrange
        var response =
                new CouncilDto.SkipRequestResponse(
                        ASCT_ID,
                        "보안강화",
                        "FL-2026-0001",
                        "10001",
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null);
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
        var response =
                new CouncilDto.SkipRequestResponse(
                        ASCT_ID,
                        "기타 사유",
                        "FL-2026-0002",
                        "10002",
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null);
        given(councilSkipService.getSkipRequest(ASCT_ID)).willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/skip-request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asctId").value(ASCT_ID))
                .andExpect(jsonPath("$.rsn").value("기타 사유"));
    }
}
