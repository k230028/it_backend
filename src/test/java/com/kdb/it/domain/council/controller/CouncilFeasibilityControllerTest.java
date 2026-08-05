package com.kdb.it.domain.council.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.FeasibilityService;
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
 * CouncilFeasibilityController @WebMvcTest
 *
 * <p>HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest(CouncilFeasibilityController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CouncilFeasibilityControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private FeasibilityService feasibilityService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    /** 핵심 필드 제약을 충족하는 타당성검토표 요청 (kpnTc 필수) */
    private static CouncilDto.FeasibilityRequest validFeasibilityRequest() {
        return new CouncilDto.FeasibilityRequest(
                null, null, null, null, null, null, null, null, null, "10", null, null, null);
    }

    // =========================================================================
    // M4: 타당성검토표
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/feasibility - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getFeasibility_인증_200() throws Exception {
        given(feasibilityService.getFeasibility(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/feasibility")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/feasibility - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void saveFeasibility_인증_200() throws Exception {
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/feasibility")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(validFeasibilityRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/feasibility - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void updateFeasibility_인증_200() throws Exception {
        mockMvc.perform(
                        put("/api/council/" + ASCT_ID + "/feasibility")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(validFeasibilityRequest())))
                .andExpect(status().isOk());
    }
}
