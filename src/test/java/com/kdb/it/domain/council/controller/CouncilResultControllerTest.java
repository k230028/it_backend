package com.kdb.it.domain.council.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.kdb.it.domain.council.service.ResultService;
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
 * CouncilResultController @WebMvcTest
 *
 * <p>HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest(CouncilResultController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CouncilResultControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ResultService resultService;
    @MockitoBean private CouncilService councilService;
    @MockitoBean private CouncilApprovalService councilApprovalService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // =========================================================================
    // M7: 결과서
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/result - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getResult_인증_200() throws Exception {
        given(resultService.getResult(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/result")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/result - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void saveResult_인증_200() throws Exception {
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/result")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/result - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void updateResult_인증_200() throws Exception {
        mockMvc.perform(
                        put("/api/council/" + ASCT_ID + "/result")
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
        mockMvc.perform(post("/api/council/" + ASCT_ID + "/notify")).andExpect(status().isOk());
    }

    // =========================================================================
    // M7: 결과서 검토 동기화 및 본인 확인 여부
    // =========================================================================

    /**
     * syncReviewStatus: POST /api/council/{asctId}/result/review/sync
     * resultService.syncReviewStatus(asctId) 위임 및 Boolean 응답 확인
     *
     * <p>이 슬라이스는 {@code @EnableMethodSecurity}를 활성화하지 않아 {@code @PreAuthorize}가 적용되지 않으므로,
     * ADMIN/비-ADMIN 권한 경계 검증은 다루지 않습니다(해당 경계는 {@code CouncilControllerSecurityTest}가 검증). 이 테스트는
     * ADMIN 신원으로 서비스 위임과 Boolean 응답 매핑만 확인합니다.
     */
    @Test
    @DisplayName("POST /api/council/{asctId}/result/review/sync - ADMIN → 200 + Boolean 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
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
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/result/approval")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apfMngNo").value("APF_202600000099"));
    }
}
