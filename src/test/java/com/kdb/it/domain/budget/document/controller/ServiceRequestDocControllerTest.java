package com.kdb.it.domain.budget.document.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.kdb.it.domain.budget.document.dto.ServiceRequestDocDto;
import com.kdb.it.domain.budget.document.service.ServiceRequestDocService;

@WebMvcTest(ServiceRequestDocController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class ServiceRequestDocControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ServiceRequestDocService serviceRequestDocService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/documents - 비인증 → 401")
    void getDocuments_비인증_401() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/documents - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getDocuments_인증_200() throws Exception {
        given(serviceRequestDocService.getDocumentList()).willReturn(List.of());
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/documents/{docMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getDocument_인증_200() throws Exception {
        given(serviceRequestDocService.getDocument(anyString(), any()))
                .willReturn(new ServiceRequestDocDto.Response());
        mockMvc.perform(get("/api/documents/DOC-2026-0001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/documents/{docMngNo}/versions - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getVersionHistory_인증_200() throws Exception {
        given(serviceRequestDocService.getVersionHistory("DOC-2026-0001")).willReturn(List.of());
        mockMvc.perform(get("/api/documents/DOC-2026-0001/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/documents - 인증된 사용자 → 201 Created")
    @WithMockUser(username = "10001")
    void createDocument_인증_201() throws Exception {
        given(serviceRequestDocService.createDocument(any())).willReturn("DOC-2026-0001");
        var body = new ServiceRequestDocDto.CreateRequest();
        body.setReqTtl("요구사항 제목");

        mockMvc.perform(post("/api/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/documents - 필수 필드 누락 → 400")
    @WithMockUser(username = "10001")
    void createDocument_필수필드누락_400() throws Exception {
        var body = new ServiceRequestDocDto.CreateRequest();
        body.setReqTtl(null);

        mockMvc.perform(post("/api/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/documents/{docMngNo}/versions - 인증된 사용자 → 201 Created")
    @WithMockUser(username = "10001")
    void createNewVersion_인증_201() throws Exception {
        given(serviceRequestDocService.createNewVersion(eq("DOC-2026-0001"), any()))
                .willReturn(new BigDecimal("2.0"));
        mockMvc.perform(post("/api/documents/DOC-2026-0001/versions"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/documents/{docMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void updateDocument_인증_200() throws Exception {
        given(serviceRequestDocService.updateDocument(anyString(), any(), any())).willReturn("DOC-2026-0001");
        var body = new ServiceRequestDocDto.UpdateRequest();
        body.setReqTtl("요구사항 제목");

        mockMvc.perform(put("/api/documents/DOC-2026-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/documents/{docMngNo} - 필수 필드 누락 → 400")
    @WithMockUser(username = "10001")
    void updateDocument_필수필드누락_400() throws Exception {
        var body = new ServiceRequestDocDto.UpdateRequest();
        body.setReqTtl(null);

        mockMvc.perform(put("/api/documents/DOC-2026-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/documents/{docMngNo} - 인증된 사용자 → 204 No Content")
    @WithMockUser(username = "10001")
    void deleteDocument_인증_204() throws Exception {
        mockMvc.perform(delete("/api/documents/DOC-2026-0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/documents/dashboard - 인증된 사용자 → 200")
    void getDashboard_인증_200() throws Exception {
        CustomUserDetails user = new CustomUserDetails("10001", List.of("ITPAD001"), "IT001");
        given(serviceRequestDocService.getDashboard(anyString()))
                .willReturn(new ServiceRequestDocDto.DashboardResponse());
        mockMvc.perform(get("/api/documents/dashboard").param("bbrC", "IT001").with(user(user)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/documents/badge-count - 인증된 사용자 → 200")
    void getBadgeCount_인증_200() throws Exception {
        CustomUserDetails user = new CustomUserDetails("10001", List.of("ITPAD001"), "IT001");
        given(serviceRequestDocService.getBadgeCount(anyString()))
                .willReturn(new ServiceRequestDocDto.BadgeCountResponse());
        mockMvc.perform(get("/api/documents/badge-count").param("bbrC", "IT001").with(user(user)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/documents/dashboard - 비관리자는 본인 부서코드로 강제된다")
    void dashboard_nonAdminForcedToOwnBbrC() throws Exception {
        CustomUserDetails user = new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");
        given(serviceRequestDocService.getDashboard("18001"))
                .willReturn(new ServiceRequestDocDto.DashboardResponse());

        mockMvc.perform(get("/api/documents/dashboard").param("bbrC", "99999").with(user(user)))
                .andExpect(status().isOk());

        verify(serviceRequestDocService).getDashboard("18001");
    }

    @Test
    @DisplayName("GET /api/documents/dashboard - 관리자는 요청한 부서코드를 사용한다")
    void dashboard_adminUsesRequestedBbrC() throws Exception {
        CustomUserDetails user = new CustomUserDetails("E0099", List.of("ITPAD001"), "18001");
        given(serviceRequestDocService.getDashboard("99999"))
                .willReturn(new ServiceRequestDocDto.DashboardResponse());

        mockMvc.perform(get("/api/documents/dashboard").param("bbrC", "99999").with(user(user)))
                .andExpect(status().isOk());

        verify(serviceRequestDocService).getDashboard("99999");
    }

    @Test
    @DisplayName("GET /api/documents/badge-count - 비관리자는 본인 부서코드로 강제된다")
    void badgeCount_nonAdminForcedToOwnBbrC() throws Exception {
        CustomUserDetails user = new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");
        given(serviceRequestDocService.getBadgeCount("18001"))
                .willReturn(new ServiceRequestDocDto.BadgeCountResponse());

        mockMvc.perform(get("/api/documents/badge-count").param("bbrC", "99999").with(user(user)))
                .andExpect(status().isOk());

        verify(serviceRequestDocService).getBadgeCount("18001");
    }

    @Test
    @DisplayName("GET /api/documents/badge-count - 관리자는 요청한 부서코드를 사용한다")
    void badgeCount_adminUsesRequestedBbrC() throws Exception {
        CustomUserDetails user = new CustomUserDetails("E0099", List.of("ITPAD001"), "18001");
        given(serviceRequestDocService.getBadgeCount("99999"))
                .willReturn(new ServiceRequestDocDto.BadgeCountResponse());

        mockMvc.perform(get("/api/documents/badge-count").param("bbrC", "99999").with(user(user)))
                .andExpect(status().isOk());

        verify(serviceRequestDocService).getBadgeCount("99999");
    }
}
