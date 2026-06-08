package com.kdb.it.domain.contract.controller;

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
import com.kdb.it.domain.contract.dto.ContractDto;
import com.kdb.it.domain.contract.service.ContractService;
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
 * ContractController @WebMvcTest
 *
 * <p>입찰/계약 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 *
 * <p>인증 설정: TestSecurityConfig(CSRF 비활성화, 비인증 401)를 임포트하고
 * @WithMockUser로 인증을 시뮬레이션합니다. DeliberationControllerTest와 동일한 패턴.</p>
 */
@WebMvcTest(ContractController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class ContractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContractService contractService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/project/contracts - 비인증 → 401")
    void create_비인증_401() throws Exception {
        mockMvc.perform(post("/api/project/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ContractDto.CreateRequest("100", "PRJ-1", "의뢰"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("GET /api/project/contracts → 200 + 목록 반환")
    void list_returns200() throws Exception {
        given(contractService.list(any(), any(), any(), any()))
                .willReturn(List.of(new ContractDto.ListItem(
                        "CTR-2026-0001", 1, "100", "PRJ-1", "61",
                        "계약A", new BigDecimal("1000"), "10001", null)));

        mockMvc.perform(get("/api/project/contracts")
                        .param("status", "61")
                        .param("prnTc", "100")
                        .param("cncdRfrNo", "PRJ-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docMngNo").value("CTR-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("GET /api/project/contracts/{docNo} → 200 + 상세 반환")
    void get_returns200() throws Exception {
        given(contractService.get("CTR-2026-0001"))
                .willReturn(new ContractDto.Detail(
                        "CTR-2026-0001", 1, "100", "PRJ-1", "테스트사업",
                        "61", "의뢰", "01", "수의계약", "계약A",
                        new BigDecimal("1000"), "공급사", "20260601", "10001", null));

        mockMvc.perform(get("/api/project/contracts/CTR-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docMngNo").value("CTR-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("POST /api/project/contracts → 201 + 문서번호 반환")
    void create_returns201() throws Exception {
        given(contractService.create(any(ContractDto.CreateRequest.class), any()))
                .willReturn("CTR-2026-0001");

        mockMvc.perform(post("/api/project/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ContractDto.CreateRequest("100", "PRJ-1", "의뢰"))))
                .andExpect(status().isCreated())
                .andExpect(content().string("CTR-2026-0001"));
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("PUT /api/project/contracts/{docNo} → 200")
    void update_returns200() throws Exception {
        mockMvc.perform(put("/api/project/contracts/CTR-2026-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ContractDto.UpdateRequest("수정"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("DELETE /api/project/contracts/{docNo} → 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/project/contracts/CTR-2026-0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("POST /api/project/contracts/{docNo}/status → 200")
    void changeStatus_returns200() throws Exception {
        mockMvc.perform(post("/api/project/contracts/CTR-2026-0001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ContractDto.StatusRequest("62"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10001")
    @DisplayName("PUT /api/project/contracts/{docNo}/contract → 200")
    void saveContract_returns200() throws Exception {
        mockMvc.perform(put("/api/project/contracts/CTR-2026-0001/contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ContractDto.WorkRequest(
                                        "01", "사유", "계약A", new BigDecimal("1000"), "공급사", "20260601"))))
                .andExpect(status().isOk());
    }
}
