package com.kdb.it.domain.council.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.kdb.it.domain.council.service.MainQnaService;
import com.kdb.it.domain.council.service.QnaService;
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
 * CouncilQnaController, CouncilMainQnaController @WebMvcTest
 *
 * <p>HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest({CouncilQnaController.class, CouncilMainQnaController.class})
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CouncilQnaControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private QnaService qnaService;
    @MockitoBean private MainQnaService mainQnaService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // =========================================================================
    // M3-1: 본회의 Q&A
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/main-qna - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getMainQnaList_인증_200() throws Exception {
        given(mainQnaService.getMainQnaList(ASCT_ID))
                .willReturn(
                        List.of(
                                new CouncilDto.QnaResponse(
                                        "MQT-1", "10001", "홍길동", "질의", null, null, null, "N")));

        mockMvc.perform(get("/api/council/" + ASCT_ID + "/main-qna"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].qtnId").value("MQT-1"))
                .andExpect(jsonPath("$[0].qtnCone").value("질의"));
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/main-qna - ADMIN → 생성 ID 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createMainQna_관리자_200() throws Exception {
        given(mainQnaService.createMainQna(anyString(), any(), any())).willReturn("MQT-1");

        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/main-qna")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CouncilDto.QnaCreateRequest("질의"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("MQT-1"));
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/main-qna/{qtnId} - ADMIN → 200")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updateMainQna_관리자_200() throws Exception {
        mockMvc.perform(
                        patch("/api/council/" + ASCT_ID + "/main-qna/MQT-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CouncilDto.QnaUpdateRequest("수정"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/main-qna/{qtnId} - ADMIN → 200")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void replyMainQna_관리자_200() throws Exception {
        mockMvc.perform(
                        put("/api/council/" + ASCT_ID + "/main-qna/MQT-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CouncilDto.QnaReplyRequest("답변"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/council/{asctId}/main-qna/{qtnId} - ADMIN → 204")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void deleteMainQna_관리자_204() throws Exception {
        mockMvc.perform(delete("/api/council/" + ASCT_ID + "/main-qna/MQT-1"))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // M6: 사전질의응답
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/qna - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getQnaList_인증_200() throws Exception {
        given(qnaService.getQnaList(ASCT_ID)).willReturn(List.of());
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/qna")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/qna - 인증된 사용자 → 201 Created")
    @WithMockUser(username = "10001")
    void createQna_인증_201() throws Exception {
        given(qnaService.createQna(anyString(), any(), any())).willReturn("QTN-ASCT-2026-0001-01");
        // QnaCreateRequest.qtnCone은 @NotBlank — 유효한 본문 전송
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/qna")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"qtnCone\":\"사전 질의 내용\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/qna - 질의내용 누락 → 400")
    @WithMockUser(username = "10001")
    void createQna_본문누락_400() throws Exception {
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/qna")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/qna/{qtnId} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void replyQna_인증_200() throws Exception {
        // QnaReplyRequest.repCone은 @NotBlank — 유효한 본문 전송
        mockMvc.perform(
                        put("/api/council/" + ASCT_ID + "/qna/QTN-ASCT-2026-0001-01")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"repCone\":\"답변 내용\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/qna/{qtnId} - 답변내용 누락 → 400")
    @WithMockUser(username = "10001")
    void replyQna_본문누락_400() throws Exception {
        mockMvc.perform(
                        put("/api/council/" + ASCT_ID + "/qna/QTN-ASCT-2026-0001-01")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/qna/{qtnId} - 인증된 사용자 → 200, 서비스에 경로·본문 전달")
    @WithMockUser(username = "10001")
    void updateQna_인증_200() throws Exception {
        // QnaUpdateRequest.qtnCone은 @NotBlank — 유효한 본문 전송
        mockMvc.perform(
                        patch("/api/council/" + ASCT_ID + "/qna/QTN-ASCT-2026-0001-01")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"qtnCone\":\"수정된 질의 내용\"}"))
                .andExpect(status().isOk());

        verify(qnaService)
                .updateQna(
                        eq(ASCT_ID),
                        eq("QTN-ASCT-2026-0001-01"),
                        argThat(request -> "수정된 질의 내용".equals(request.qtnCone())),
                        any());
    }

    @Test
    @DisplayName("PATCH /api/council/{asctId}/qna/{qtnId} - 질의내용 누락 → 400, 서비스 미호출")
    @WithMockUser(username = "10001")
    void updateQna_본문누락_400() throws Exception {
        mockMvc.perform(
                        patch("/api/council/" + ASCT_ID + "/qna/QTN-ASCT-2026-0001-01")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"qtnCone\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verify(qnaService, never()).updateQna(anyString(), anyString(), any(), any());
    }
}
