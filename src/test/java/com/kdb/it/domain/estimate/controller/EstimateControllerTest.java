package com.kdb.it.domain.estimate.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.estimate.service.EstimateService;
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
 * EstimateController @WebMvcTest
 *
 * <p>소요예산 산정 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 *
 * <p>인증 설정: TestSecurityConfig(CSRF 비활성화, 비인증 401)를 임포트하고
 * @WithMockUser로 인증을 시뮬레이션합니다. CostControllerTest와 동일한 패턴.</p>
 */
@WebMvcTest(EstimateController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class EstimateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EstimateService estimateService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/project/estimates - 비인증 → 401")
    void create_비인증_401() throws Exception {
        mockMvc.perform(post("/api/project/estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EstimateDto.CreateRequest("PRJ-2026-0001", "요청"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("POST /api/project/estimates → 201 + 문서번호 반환")
    void create_returns201() throws Exception {
        given(estimateService.create(any(EstimateDto.CreateRequest.class), any()))
                .willReturn("REQ-2026-0001");

        mockMvc.perform(post("/api/project/estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EstimateDto.CreateRequest("PRJ-2026-0001", "요청"))))
                .andExpect(status().isCreated())
                .andExpect(content().string("REQ-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("POST /api/project/estimates/{docNo}/status → 200")
    void changeStatus_returns200() throws Exception {
        mockMvc.perform(post("/api/project/estimates/REQ-2026-0001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EstimateDto.StatusRequest("42"))))
                .andExpect(status().isOk());
    }
}
