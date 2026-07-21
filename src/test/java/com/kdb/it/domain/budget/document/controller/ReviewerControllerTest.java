package com.kdb.it.domain.budget.document.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.document.dto.ReviewerDto;
import com.kdb.it.domain.budget.document.service.ReviewerService;

/**
 * ReviewerController @WebMvcTest
 *
 * <p>사전협의 검토자 목록 API의 인증 상태와 응답 구조를 검증합니다.</p>
 */
@WebMvcTest(ReviewerController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class ReviewerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewerService reviewerService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private static final String DOC_ID = "DOC-2026-0001";

    @Test
    @DisplayName("GET /api/reviews/reviewers - 비인증 → 401")
    void getReviewers_비인증_401() throws Exception {
        mockMvc.perform(get("/api/reviews/reviewers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/reviews/reviewers - 인증된 사용자 → 200 + 검토자 배열")
    @WithMockUser(username = "10001")
    void getReviewers_인증_200() throws Exception {
        ReviewerDto.Response reviewer = new ReviewerDto.Response("E001", "홍길동", "PMO팀");
        given(reviewerService.getReviewers()).willReturn(List.of(reviewer));

        mockMvc.perform(get("/api/reviews/reviewers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].eno").value("E001"))
                .andExpect(jsonPath("$[0].empNm").value("홍길동"))
                .andExpect(jsonPath("$[0].teamName").value("PMO팀"));

        verify(reviewerService).getReviewers();
    }

    @Test
    @DisplayName("GET /api/reviews/reviewers - 검토자 없음 → 200 + 빈 배열")
    @WithMockUser(username = "10001")
    void getReviewers_검토자없음_빈배열() throws Exception {
        given(reviewerService.getReviewers()).willReturn(List.of());

        mockMvc.perform(get("/api/reviews/reviewers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(reviewerService).getReviewers();
    }

    @Test
    @DisplayName("GET /api/reviews/{docMngNo}/reviewers - 인증된 사용자 → 200 + 호환 응답")
    @WithMockUser(username = "10001")
    void getReviewers_구경로호환_200() throws Exception {
        ReviewerDto.Response reviewer = new ReviewerDto.Response("E001", "홍길동", "PMO팀");
        given(reviewerService.getReviewers()).willReturn(List.of(reviewer));

        mockMvc.perform(get("/api/reviews/" + DOC_ID + "/reviewers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].eno").value("E001"))
                .andExpect(jsonPath("$[0].empNm").value("홍길동"))
                .andExpect(jsonPath("$[0].teamName").value("PMO팀"));

        verify(reviewerService).getReviewers();
    }
}
