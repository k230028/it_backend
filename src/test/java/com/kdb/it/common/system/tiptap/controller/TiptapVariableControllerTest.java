package com.kdb.it.common.system.tiptap.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.MetadataResponse;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ResolveRequest;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ResolveResponse;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ResolvedValue;
import com.kdb.it.common.system.tiptap.service.TiptapVariableService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;

/**
 * TiptapVariableController @WebMvcTest
 *
 * <p>Tiptap 변수 카탈로그 조회/토큰 해석 API의 HTTP 응답 구조와 인증·검증 동작을 검증합니다.</p>
 *
 * Design Ref: §2.2 백엔드, §4.5 권한 필터링
 */
@WebMvcTest(TiptapVariableController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class TiptapVariableControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TiptapVariableService service;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser
    @DisplayName("GET /api/tiptap-variables/metadata - 인증 사용자에게 카탈로그 반환")
    void getMetadata_authenticated_returnsOk() throws Exception {
        given(service.getMetadata()).willReturn(new MetadataResponse(List.of()));

        mockMvc.perform(get("/api/tiptap-variables/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isArray());
    }

    @Test
    @DisplayName("GET /api/tiptap-variables/metadata - 미인증 사용자는 401")
    void getMetadata_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/tiptap-variables/metadata"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/tiptap-variables/resolve - 정상 요청은 200 + results 반환")
    void resolve_validRequest_returnsResults() throws Exception {
        given(service.resolve(any())).willReturn(new ResolveResponse(Map.of(
                "2026.itBudget.requestAmount", ResolvedValue.ok("900억원")
        )));

        var body = objectMapper.writeValueAsString(
                new ResolveRequest(List.of("2026.itBudget.requestAmount")));

        mockMvc.perform(post("/api/tiptap-variables/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.['2026.itBudget.requestAmount'].status").value("OK"))
                .andExpect(jsonPath("$.results.['2026.itBudget.requestAmount'].value").value("900억원"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/tiptap-variables/resolve - 토큰 201개면 400")
    void resolve_tooManyTokens_returnsBadRequest() throws Exception {
        List<String> tooMany = IntStream.range(0, 201)
                .mapToObj(i -> "2026.itBudget.requestAmount")
                .toList();
        var body = objectMapper.writeValueAsString(new ResolveRequest(tooMany));

        mockMvc.perform(post("/api/tiptap-variables/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
