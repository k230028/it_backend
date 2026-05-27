package com.kdb.it.infra.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.kdb.it.infra.ai.dto.GeminiDto;
import com.kdb.it.infra.ai.service.GeminiService;

/**
 * GeminiController @WebMvcTest
 *
 * <p>Gemini AI 프록시 컨트롤러의 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 *
 * <p>테스트 대상 엔드포인트:</p>
 * <ul>
 *   <li>POST /api/gemini/generate — AI 응답 생성</li>
 * </ul>
 */
@WebMvcTest(GeminiController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class GeminiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // -------------------------------------------------------------------------
    // 성공 케이스
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/gemini/generate - 인증 사용자 → 200 + AI 응답 텍스트 반환")
    @WithMockUser(roles = "ADMIN")
    void generate_인증사용자_200응답() throws Exception {
        // Arrange
        GeminiDto.Request request = GeminiDto.Request.builder()
                .prompt("요구사항 정의서 작성을 도와줘")
                .systemInstruction("당신은 IT 프로젝트 전문가입니다.")
                .build();
        GeminiDto.Response response = GeminiDto.Response.builder()
                .text("요구사항 정의서 초안입니다.")
                .model("gemini-2.0-flash")
                .attachedFileCount(0)
                .skippedFiles(List.of())
                .build();
        given(geminiService.generate(any(GeminiDto.Request.class))).willReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/gemini/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("요구사항 정의서 초안입니다."))
                .andExpect(jsonPath("$.model").value("gemini-2.0-flash"))
                .andExpect(jsonPath("$.attachedFileCount").value(0));
    }

    @Test
    @DisplayName("POST /api/gemini/generate - 첨부파일 포함 요청 → 200 + attachedFileCount 반영")
    @WithMockUser(roles = "ADMIN")
    void generate_첨부파일포함_attachedFileCount반영() throws Exception {
        // Arrange
        GeminiDto.Request request = GeminiDto.Request.builder()
                .prompt("이 파일들을 분석해줘")
                .flMpnIds(List.of("FL_00000001", "FL_00000002"))
                .build();
        GeminiDto.Response response = GeminiDto.Response.builder()
                .text("파일 분석 결과입니다.")
                .model("gemini-2.0-flash")
                .attachedFileCount(2)
                .skippedFiles(List.of())
                .build();
        given(geminiService.generate(any(GeminiDto.Request.class))).willReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/gemini/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachedFileCount").value(2))
                .andExpect(jsonPath("$.text").value("파일 분석 결과입니다."));
    }

    @Test
    @DisplayName("POST /api/gemini/generate - 일부 파일 건너뜀 → 200 + skippedFiles 목록 포함")
    @WithMockUser(roles = "ADMIN")
    void generate_파일건너뜀_skippedFiles반환() throws Exception {
        // Arrange
        GeminiDto.Request request = GeminiDto.Request.builder()
                .prompt("분석 요청")
                .flMpnIds(List.of("FL_00000001", "FL_INVALID"))
                .build();
        GeminiDto.Response response = GeminiDto.Response.builder()
                .text("부분 분석 결과입니다.")
                .model("gemini-2.0-flash")
                .attachedFileCount(1)
                .skippedFiles(List.of("FL_INVALID: 미지원 형식"))
                .build();
        given(geminiService.generate(any(GeminiDto.Request.class))).willReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/gemini/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skippedFiles[0]").value("FL_INVALID: 미지원 형식"))
                .andExpect(jsonPath("$.attachedFileCount").value(1));
    }

    // -------------------------------------------------------------------------
    // 실패 케이스 — 인증
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/gemini/generate - 비인증 → 401")
    void generate_비인증_401() throws Exception {
        // Arrange
        GeminiDto.Request request = GeminiDto.Request.builder()
                .prompt("접근 테스트")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/gemini/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // 엣지 케이스
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/gemini/generate - systemInstruction 없는 최소 요청 → 200 정상 처리")
    @WithMockUser(roles = "ADMIN")
    void generate_systemInstruction없음_정상처리() throws Exception {
        // Arrange: systemInstruction 필드를 생략한 최소 요청
        GeminiDto.Request request = GeminiDto.Request.builder()
                .prompt("간단한 질문입니다.")
                .build();
        GeminiDto.Response response = GeminiDto.Response.builder()
                .text("간단한 답변입니다.")
                .model("gemini-2.0-flash")
                .attachedFileCount(0)
                .skippedFiles(List.of())
                .build();
        given(geminiService.generate(any(GeminiDto.Request.class))).willReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/gemini/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("간단한 답변입니다."));
    }
}
