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
import com.kdb.it.domain.budget.document.dto.GuideDocDto;
import com.kdb.it.domain.budget.document.service.GuideDocService;

/**
 * GuideDocController @WebMvcTest
 *
 * <p>안내문서 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
@WebMvcTest(GuideDocController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class GuideDocControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GuideDocService guideDocService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/guide-documents - 비인증 → 401")
    void getDocuments_비인증_401() throws Exception {
        mockMvc.perform(get("/api/guide-documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/guide-documents - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getDocuments_인증_200() throws Exception {
        given(guideDocService.getDocumentList()).willReturn(List.of());
        mockMvc.perform(get("/api/guide-documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/guide-documents/{docMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getDocument_인증_200() throws Exception {
        given(guideDocService.getDocument("DOC-2026-0001")).willReturn(new GuideDocDto.Response());
        mockMvc.perform(get("/api/guide-documents/DOC-2026-0001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/guide-documents - 인증된 사용자 → 201 Created")
    @WithMockUser(username = "10001")
    void createDocument_인증_201() throws Exception {
        given(guideDocService.createDocument(any())).willReturn("DOC-2026-0001");
        var body = new GuideDocDto.CreateRequest();
        body.setDocTtlCone("가이드 문서");

        mockMvc.perform(post("/api/guide-documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/guide-documents - 필수 필드 누락 → 400")
    @WithMockUser(username = "10001")
    void createDocument_필수필드누락_400() throws Exception {
        var body = new GuideDocDto.CreateRequest();
        body.setDocTtlCone(null);

        mockMvc.perform(post("/api/guide-documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/guide-documents/{docMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void updateDocument_인증_200() throws Exception {
        given(guideDocService.updateDocument(anyString(), any())).willReturn("DOC-2026-0001");
        mockMvc.perform(put("/api/guide-documents/DOC-2026-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GuideDocDto.UpdateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/guide-documents/{docMngNo} - 인증된 사용자 → 204 No Content")
    @WithMockUser(username = "10001")
    void deleteDocument_인증_204() throws Exception {
        mockMvc.perform(delete("/api/guide-documents/DOC-2026-0001"))
                .andExpect(status().isNoContent());
    }
}
