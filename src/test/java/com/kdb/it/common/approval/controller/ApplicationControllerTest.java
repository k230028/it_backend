package com.kdb.it.common.approval.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.service.ApplicationService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;

/**
 * ApplicationController @WebMvcTest
 *
 * <p>전자결재 신청 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
@WebMvcTest(ApplicationController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApplicationService applicationService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/applications - 비인증 → 401")
    void getApplications_비인증_401() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized());
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
        given(applicationService.getPendingCount(null)).willReturn(ApplicationDto.PendingCountResponse.builder().build());
        mockMvc.perform(get("/api/applications/pending-count"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/applications/{apfMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getApplication_인증_200() throws Exception {
        given(applicationService.getApplication("APF_20260001")).willReturn(ApplicationDto.Response.builder().build());
        mockMvc.perform(get("/api/applications/APF_20260001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/applications/bulk-get - 인증된 사용자 → 200 + items/failedIds 반환")
    @WithMockUser(username = "10001")
    void bulkGet_인증_200() throws Exception {
        given(applicationService.getApplicationsByIds(any()))
                .willReturn(new ApplicationDto.BulkResponse(List.of(), List.of()));
        mockMvc.perform(post("/api/applications/bulk-get")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ApplicationDto.BulkGetRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.failedIds").isArray());
    }

    @Test
    @DisplayName("POST /api/applications/{apfMngNo}/approve - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void approve_인증_200() throws Exception {
        mockMvc.perform(post("/api/applications/APF_20260001/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ApplicationDto.ApproveRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/applications/bulk-approve - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void bulkApprove_인증_200() throws Exception {
        given(applicationService.bulkApprove(any())).willReturn(ApplicationDto.BulkApproveResponse.builder().build());
        mockMvc.perform(post("/api/applications/bulk-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ApplicationDto.BulkApproveRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/applications/dashboard - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getDashboard_인증_200() throws Exception {
        given(applicationService.getDashboard(anyString(), anyString()))
                .willReturn(new ApplicationDto.DashboardResponse());
        mockMvc.perform(get("/api/applications/dashboard")
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
        mockMvc.perform(get("/api/applications/approval-badge")
                .param("bbrC", "IT001")
                .param("eno", "E10001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/applications/{apfMngNo}/apfDtlCone - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getApfDtlCone_인증_200() throws Exception {
        // Arrange
        given(applicationService.getApfDtlCone("APF_202600000001"))
                .willReturn(ApplicationDto.ApfDtlConeResponse.builder().build());

        // Act & Assert
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
        // Arrange
        given(applicationService.submit(any())).willReturn("APF_202600000001");

        // Act & Assert
        mockMvc.perform(post("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ApplicationDto.CreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    @DisplayName("POST /api/applications - 비인증 → 401")
    void submit_비인증_401() throws Exception {
        mockMvc.perform(post("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
