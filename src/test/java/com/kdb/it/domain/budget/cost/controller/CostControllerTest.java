package com.kdb.it.domain.budget.cost.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.service.CostService;

/**
 * CostController @WebMvcTest
 *
 * <p>전산관리비 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
@WebMvcTest(CostController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class CostControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CostService costService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/cost - 비인증 → 401")
    void getCostList_비인증_401() throws Exception {
        mockMvc.perform(get("/api/cost"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/cost - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getCostList_인증_200() throws Exception {
        given(costService.searchCostList(any())).willReturn(List.of());
        mockMvc.perform(get("/api/cost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/cost/{itMngcNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getCost_인증_200() throws Exception {
        given(costService.getCost("COST_2026_0001")).willReturn(new CostDto.Response());
        mockMvc.perform(get("/api/cost/COST_2026_0001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/cost - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void createCost_인증_200() throws Exception {
        given(costService.createCost(any())).willReturn("COST_2026_0001");
        mockMvc.perform(post("/api/cost")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CostDto.CreateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/cost/{itMngcNo} - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void updateCost_인증_200() throws Exception {
        given(costService.updateCost(anyString(), any())).willReturn("COST_2026_0001");
        mockMvc.perform(put("/api/cost/COST_2026_0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CostDto.UpdateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/cost/{itMngcNo} - 인증된 사용자 → 204 No Content")
    @WithMockUser(username = "10001")
    void deleteCost_인증_204() throws Exception {
        mockMvc.perform(delete("/api/cost/COST_2026_0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/cost/bulk-get - 인증된 사용자 → 200 + items/failedIds 반환")
    @WithMockUser(username = "10001")
    void getCostsByIds_인증_200() throws Exception {
        given(costService.getCostsByIds(any()))
                .willReturn(new CostDto.BulkResponse(List.of(), List.of()));
        mockMvc.perform(post("/api/cost/bulk-get")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CostDto.BulkGetRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.failedIds").isArray());
    }
}
