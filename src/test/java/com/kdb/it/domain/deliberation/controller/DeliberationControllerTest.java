package com.kdb.it.domain.deliberation.controller;

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
import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import com.kdb.it.domain.deliberation.service.DeliberationService;
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
 * DeliberationController @WebMvcTest
 *
 * <p>과업심의위원회 HTTP 응답 구조와 인증 동작을 검증합니다.
 *
 * <p>인증 설정: TestSecurityConfig(CSRF 비활성화, 비인증 401)를 임포트하고 @WithMockUser로 인증을 시뮬레이션합니다.
 * EstimateControllerTest와 동일한 패턴.
 */
@WebMvcTest(DeliberationController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class DeliberationControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private DeliberationService deliberationService;

    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/project/deliberations - 비인증 → 401")
    void create_비인증_401() throws Exception {
        mockMvc.perform(
                        post("/api/project/deliberations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DeliberationDto.CreateRequest(
                                                        "100", "PRJ-1", "요청"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("GET /api/project/deliberations → 200 + 목록 반환")
    void list_returns200() throws Exception {
        given(deliberationService.list(any(), any(), any(), any()))
                .willReturn(
                        List.of(
                                new DeliberationDto.ListItem(
                                        "DLB-2026-0001",
                                        1,
                                        "100",
                                        "PRJ-1",
                                        "61",
                                        "01",
                                        "10001",
                                        null)));

        mockMvc.perform(
                        get("/api/project/deliberations")
                                .param("status", "61")
                                .param("prnTc", "100")
                                .param("cncdRfrNo", "PRJ-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docMngNo").value("DLB-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("GET /api/project/deliberations/{docNo} → 200 + 상세 반환")
    void get_returns200() throws Exception {
        given(deliberationService.get("DLB-2026-0001"))
                .willReturn(
                        new DeliberationDto.Detail(
                                "DLB-2026-0001",
                                1,
                                "100",
                                "PRJ-1",
                                "테스트사업",
                                "61",
                                "요청",
                                "01",
                                "01",
                                "20260601",
                                "1",
                                "N",
                                null,
                                "의견",
                                "전결사유",
                                "10001",
                                null));

        mockMvc.perform(get("/api/project/deliberations/DLB-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docMngNo").value("DLB-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("POST /api/project/deliberations → 201 + 문서번호 반환")
    void create_returns201() throws Exception {
        given(deliberationService.create(any(DeliberationDto.CreateRequest.class), any()))
                .willReturn("DLB-2026-0001");

        mockMvc.perform(
                        post("/api/project/deliberations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DeliberationDto.CreateRequest(
                                                        "100", "PRJ-1", "요청"))))
                .andExpect(status().isCreated())
                .andExpect(content().string("DLB-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("PUT /api/project/deliberations/{docNo} → 200")
    void update_returns200() throws Exception {
        mockMvc.perform(
                        put("/api/project/deliberations/DLB-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DeliberationDto.UpdateRequest("수정"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("DELETE /api/project/deliberations/{docNo} → 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/project/deliberations/DLB-2026-0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("POST /api/project/deliberations/{docNo}/status → 200")
    void changeStatus_returns200() throws Exception {
        mockMvc.perform(
                        post("/api/project/deliberations/DLB-2026-0001/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DeliberationDto.StatusRequest("65"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("PUT /api/project/deliberations/{docNo}/result → 200")
    void saveResult_returns200() throws Exception {
        mockMvc.perform(
                        put("/api/project/deliberations/DLB-2026-0001/result")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DeliberationDto.ResultRequest(
                                                        "01",
                                                        "01",
                                                        "20260601",
                                                        "1",
                                                        "N",
                                                        null,
                                                        "의견",
                                                        "전결사유"))))
                .andExpect(status().isOk());
    }
}
