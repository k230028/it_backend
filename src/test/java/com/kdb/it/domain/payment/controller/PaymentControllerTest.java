package com.kdb.it.domain.payment.controller;

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
import com.kdb.it.domain.payment.dto.PaymentDto;
import com.kdb.it.domain.payment.service.PaymentService;
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
 * PaymentController @WebMvcTest
 *
 * <p>대금지급 HTTP 응답 구조와 인증 동작을 검증합니다.
 *
 * <p>인증 설정: TestSecurityConfig(CSRF 비활성화, 비인증 401)를 임포트하고 @WithMockUser로 인증을 시뮬레이션합니다.
 * DeliberationControllerTest와 동일한 패턴.
 */
@WebMvcTest(PaymentController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PaymentService paymentService;

    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/project/payments - 비인증 → 401")
    void create_비인증_401() throws Exception {
        mockMvc.perform(
                        post("/api/project/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new PaymentDto.CreateRequest(
                                                        "100",
                                                        "PRJ-1",
                                                        "의뢰",
                                                        "계약A",
                                                        new BigDecimal("1000")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("GET /api/project/payments → 200 + 목록 반환")
    void list_returns200() throws Exception {
        given(paymentService.list(any(), any(), any(), any()))
                .willReturn(
                        List.of(
                                new PaymentDto.ListItem(
                                        "PAY-2026-0001",
                                        1,
                                        "100",
                                        "PRJ-1",
                                        "81",
                                        "계약A",
                                        new BigDecimal("1000"),
                                        "10001",
                                        null)));

        mockMvc.perform(
                        get("/api/project/payments")
                                .param("status", "81")
                                .param("prnTc", "100")
                                .param("cncdRfrNo", "PRJ-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docMngNo").value("PAY-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("GET /api/project/payments/{docNo} → 200 + 상세 반환")
    void get_returns200() throws Exception {
        given(paymentService.get("PAY-2026-0001"))
                .willReturn(
                        new PaymentDto.Detail(
                                "PAY-2026-0001",
                                1,
                                "100",
                                "PRJ-1",
                                "테스트사업",
                                "81",
                                "의뢰",
                                "계약A",
                                new BigDecimal("1000"),
                                "10001",
                                null,
                                List.of()));

        mockMvc.perform(get("/api/project/payments/PAY-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docMngNo").value("PAY-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("POST /api/project/payments → 201 + 문서번호 반환")
    void create_returns201() throws Exception {
        given(paymentService.create(any(PaymentDto.CreateRequest.class), any()))
                .willReturn("PAY-2026-0001");

        mockMvc.perform(
                        post("/api/project/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new PaymentDto.CreateRequest(
                                                        "100",
                                                        "PRJ-1",
                                                        "의뢰",
                                                        "계약A",
                                                        new BigDecimal("1000")))))
                .andExpect(status().isCreated())
                .andExpect(content().string("PAY-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("PUT /api/project/payments/{docNo} → 200")
    void update_returns200() throws Exception {
        mockMvc.perform(
                        put("/api/project/payments/PAY-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new PaymentDto.UpdateRequest(
                                                        "수정", "계약B", new BigDecimal("2000")))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("DELETE /api/project/payments/{docNo} → 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/project/payments/PAY-2026-0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("POST /api/project/payments/{docNo}/status → 200")
    void changeStatus_returns200() throws Exception {
        mockMvc.perform(
                        post("/api/project/payments/PAY-2026-0001/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new PaymentDto.StatusRequest("85"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("PUT /api/project/payments/{docNo}/payments → 200")
    void savePayments_returns200() throws Exception {
        mockMvc.perform(
                        put("/api/project/payments/PAY-2026-0001/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new PaymentDto.LinesRequest(
                                                        List.of(
                                                                new PaymentDto.LineRequest(
                                                                        1,
                                                                        new BigDecimal("1000"),
                                                                        "20260601",
                                                                        null,
                                                                        "의견"))))))
                .andExpect(status().isOk());
    }
}
