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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.document.dto.GuideDocDto;
import com.kdb.it.domain.budget.document.service.GuideDocService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GuideDocController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    GuideDocControllerTest.MethodSecurityConfig.class
})
class GuideDocControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private GuideDocService guideDocService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/guide-documents - 비인증 → 401")
    void getDocuments_비인증_401() throws Exception {
        mockMvc.perform(get("/api/guide-documents")).andExpect(status().isUnauthorized());
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
    @DisplayName("GET /api/guide-documents - 목록 응답 JSON에 본문(nacTxtInf) 필드가 없다")
    @WithMockUser(username = "10001")
    void getDocuments_목록응답_본문필드없음() throws Exception {
        given(guideDocService.getDocumentList())
                .willReturn(
                        List.of(
                                new GuideDocDto.ListResponse(
                                        "GDOC-2026-0001",
                                        "가이드문서",
                                        "N",
                                        null,
                                        "10001",
                                        null,
                                        "10001")));

        mockMvc.perform(get("/api/guide-documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docMngNo").value("GDOC-2026-0001"))
                .andExpect(jsonPath("$[0].nacTxtInf").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/guide-documents/{docMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getDocument_인증_200() throws Exception {
        given(guideDocService.getDocument("GDOC-2026-0001")).willReturn(new GuideDocDto.Response());
        mockMvc.perform(get("/api/guide-documents/GDOC-2026-0001")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/guide-documents - 관리자 → 201 Created")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createDocument_관리자_201() throws Exception {
        given(guideDocService.createDocument(any())).willReturn("GDOC-2026-0001");
        var body = new GuideDocDto.CreateRequest();
        body.setDocTtlCone("가이드 문서");

        mockMvc.perform(
                        post("/api/guide-documents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/guide-documents - 일반 사용자 → 403 Forbidden")
    @WithMockUser(username = "10001")
    void createDocument_일반사용자_403() throws Exception {
        var body = new GuideDocDto.CreateRequest();
        body.setDocTtlCone("가이드 문서");

        mockMvc.perform(
                        post("/api/guide-documents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/guide-documents - 비GDOC 문서관리번호 직접 지정 → 400 Bad Request")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createDocument_비GDOC번호직접지정_400() throws Exception {
        given(guideDocService.createDocument(any()))
                .willThrow(new IllegalArgumentException("문서관리번호는 GDOC-로 시작해야 합니다"));
        var body = new GuideDocDto.CreateRequest();
        body.setDocMngNo("FDOC-2026-0001");
        body.setDocTtlCone("입력 길라잡이");

        mockMvc.perform(
                        post("/api/guide-documents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/guide-documents - 필수 필드 누락 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createDocument_필수필드누락_400() throws Exception {
        var body = new GuideDocDto.CreateRequest();
        body.setDocTtlCone(null);

        mockMvc.perform(
                        post("/api/guide-documents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/guide-documents/{docMngNo} - 관리자 → 200")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updateDocument_관리자_200() throws Exception {
        given(guideDocService.updateDocument(anyString(), any())).willReturn("GDOC-2026-0001");
        var body = new GuideDocDto.UpdateRequest();
        body.setDocTtlCone("수정 문서");

        mockMvc.perform(
                        put("/api/guide-documents/GDOC-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/guide-documents/{docMngNo} - 일반 사용자 → 403 Forbidden")
    @WithMockUser(username = "10001")
    void updateDocument_일반사용자_403() throws Exception {
        var body = new GuideDocDto.UpdateRequest();
        body.setDocTtlCone("수정 문서");

        mockMvc.perform(
                        put("/api/guide-documents/GDOC-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/guide-documents/{docMngNo} - 필수 필드 누락 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updateDocument_필수필드누락_400() throws Exception {
        var body = new GuideDocDto.UpdateRequest();
        body.setDocTtlCone(null);

        mockMvc.perform(
                        put("/api/guide-documents/GDOC-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/guide-documents/{docMngNo} - 관리자 → 204 No Content")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void deleteDocument_관리자_204() throws Exception {
        mockMvc.perform(delete("/api/guide-documents/GDOC-2026-0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/guide-documents/{docMngNo} - 일반 사용자 → 403 Forbidden")
    @WithMockUser(username = "10001")
    void deleteDocument_일반사용자_403() throws Exception {
        mockMvc.perform(delete("/api/guide-documents/GDOC-2026-0001"))
                .andExpect(status().isForbidden());
    }
}
