package com.kdb.it.domain.council.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CouncilService;
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
 * CouncilController @WebMvcTest
 *
 * <p>정보화실무협의회 HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest(CouncilController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CouncilControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CouncilService councilService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // =========================================================================
    // M3: 기본 CRUD
    // =========================================================================

    @Test
    @DisplayName("GET /api/council - 비인증 → 401")
    void getCouncilList_비인증_401() throws Exception {
        mockMvc.perform(get("/api/council")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/council - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getCouncilList_인증_200() throws Exception {
        given(councilService.getCouncilList(any())).willReturn(List.of());
        mockMvc.perform(get("/api/council"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/council - 인증된 사용자 → 201 Created + Location")
    @WithMockUser(username = "10001")
    void createCouncil_인증_201() throws Exception {
        given(councilService.createCouncil(any(), any())).willReturn(ASCT_ID);
        mockMvc.perform(
                        post("/api/council")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CouncilDto.CreateRequest(
                                                        "PRJ-2026-0001", 1, "INFO_SYS", null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/council/" + ASCT_ID));
    }

    @Test
    @DisplayName("POST /api/council - prjMngNo 누락 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createCouncil_prjMngNo누락_400() throws Exception {
        var body = new CouncilDto.CreateRequest(null, null, null, null);
        mockMvc.perform(
                        post("/api/council")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/council/{asctId} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getCouncil_인증_200() throws Exception {
        given(councilService.getCouncil(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID)).andExpect(status().isOk());
    }
}
