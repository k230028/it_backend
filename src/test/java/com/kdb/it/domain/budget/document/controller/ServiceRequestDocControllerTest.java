package com.kdb.it.domain.budget.document.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.document.dto.ServiceRequestDocDto;
import com.kdb.it.domain.budget.document.service.ServiceRequestDocService;

/**
 * ServiceRequestDocController @WebMvcTest
 *
 * <p>서비스요청문서 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
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
        mockMvc.perform(post("/api/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ServiceRequestDocDto.CreateRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/documents/{docMngNo}/versions - 인증된 사용자 → 201 Created")
    @WithMockUser(username = "10001")
    void createNewVersion_인증_201() throws Exception {
        given(serviceRequestDocService.createNewVersion("DOC-2026-0001"))
                .willReturn(new BigDecimal("2.0"));
        mockMvc.perform(post("/api/documents/DOC-2026-0001/versions"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/documents/{docMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void updateDocument_인증_200() throws Exception {
        given(serviceRequestDocService.updateDocument(anyString(), any())).willReturn("DOC-2026-0001");
        mockMvc.perform(put("/api/documents/DOC-2026-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ServiceRequestDocDto.UpdateRequest())))
                .andExpect(status().isOk());
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
    @WithMockUser(username = "10001")
    void getDashboard_인증_200() throws Exception {
        given(serviceRequestDocService.getDashboard(anyString()))
                .willReturn(new ServiceRequestDocDto.DashboardResponse());
        mockMvc.perform(get("/api/documents/dashboard").param("bbrC", "IT001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/documents/badge-count - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getBadgeCount_인증_200() throws Exception {
        given(serviceRequestDocService.getBadgeCount(anyString()))
                .willReturn(new ServiceRequestDocDto.BadgeCountResponse());
        mockMvc.perform(get("/api/documents/badge-count").param("bbrC", "IT001"))
                .andExpect(status().isOk());
    }
}
