package com.kdb.it.domain.estimate.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.estimate.service.EstimateService;
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
    @DisplayName("GET /api/project/estimates → 200 + 목록 반환")
    void list_returns200() throws Exception {
        given(estimateService.list(any(), any(), any()))
                .willReturn(List.of(new EstimateDto.ListItem(
                        "REQ-2026-0001", 1, "100", "PRJ-1",
                        "테스트사업", "51", "10001", null)));

        mockMvc.perform(get("/api/project/estimates")
                        .param("status", "51")
                        .param("cncdRfrNo", "PRJ-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rqmBgReqDocNo").value("REQ-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("GET /api/project/estimates/{docNo} → 200 + 상세 반환")
    void get_returns200() throws Exception {
        given(estimateService.get("REQ-2026-0001"))
                .willReturn(new EstimateDto.Detail(
                        "REQ-2026-0001", 1, "100", "PRJ-1", "테스트사업",
                        "51", "요청", "10001", null, List.of()));

        mockMvc.perform(get("/api/project/estimates/REQ-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rqmBgReqDocNo").value("REQ-2026-0001"));
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
    @DisplayName("PUT /api/project/estimates/{docNo} → 200")
    void update_returns200() throws Exception {
        mockMvc.perform(put("/api/project/estimates/REQ-2026-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EstimateDto.UpdateRequest("수정"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("DELETE /api/project/estimates/{docNo} → 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/project/estimates/REQ-2026-0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("POST /api/project/estimates/{docNo}/status → 200")
    void changeStatus_returns200() throws Exception {
        mockMvc.perform(post("/api/project/estimates/REQ-2026-0001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EstimateDto.StatusRequest("55"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("PUT /api/project/estimates/{docNo}/lines → 200")
    void saveLines_returns200() throws Exception {
        mockMvc.perform(put("/api/project/estimates/REQ-2026-0001/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EstimateDto.LinesRequest(List.of(
                                        new EstimateDto.LineRequest("T001", "IOE001", new BigDecimal("1000"), "의견"))))))
                .andExpect(status().isOk());
    }
}
