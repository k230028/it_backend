package com.kdb.it.domain.budget.document.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.document.service.ReviewCommentService;

/**
 * ReviewCommentController @WebMvcTest
 *
 * <p>검토의견 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
@WebMvcTest(ReviewCommentController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class ReviewCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewCommentService reviewCommentService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private static final String DOC_ID = "DOC-2026-0001";

    @Test
    @DisplayName("GET /api/documents/{docMngNo}/review-comments - 비인증 → 401")
    void getComments_비인증_401() throws Exception {
        mockMvc.perform(get("/api/documents/" + DOC_ID + "/review-comments")
                .param("docVrs", "1.0"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/documents/{docMngNo}/review-comments - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getComments_인증_200() throws Exception {
        given(reviewCommentService.getComments(anyString(), any())).willReturn(List.of());
        mockMvc.perform(get("/api/documents/" + DOC_ID + "/review-comments")
                .param("docVrs", "1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/documents/{docMngNo}/review-comments - 인증된 사용자 → 201 Created")
    @WithMockUser(username = "10001")
    void addComment_인증_201() throws Exception {
        given(reviewCommentService.addComment(anyString(), any()))
                .willReturn(null);
        mockMvc.perform(post("/api/documents/" + DOC_ID + "/review-comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"docVrs\":1.0,\"rplOpnnTc\":\"G\",\"ivgOpnnCone\":\"테스트 의견\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PATCH /api/documents/{docMngNo}/review-comments/{ivgSno}/resolve - 인증된 사용자 → 204 No Content")
    @WithMockUser(username = "10001")
    void resolveComment_인증_204() throws Exception {
        mockMvc.perform(patch("/api/documents/" + DOC_ID + "/review-comments/1/resolve"))
                .andExpect(status().isNoContent());
    }
}
