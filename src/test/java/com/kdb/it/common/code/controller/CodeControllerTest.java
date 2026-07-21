package com.kdb.it.common.code.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.code.dto.CodeDto;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
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
 * CodeController @WebMvcTest
 *
 * <p>공통코드 조회/생성/수정/삭제 HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest(CodeController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CodeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CodeService codeService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/ccodem/{cdId} - 비인증 → 401")
    void getCode_비인증_401() throws Exception {
        mockMvc.perform(get("/api/ccodem/CODE001")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/ccodem/{cId} - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getCode_인증_200() throws Exception {
        given(codeService.getCcodemsByCId(anyString(), any())).willReturn(List.of());
        mockMvc.perform(get("/api/ccodem/CODE001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/ccodem/{cId} - 코드ID로 목록 조회 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getCodesByCId_인증_200() throws Exception {
        given(codeService.getCcodemsByCId(anyString(), any())).willReturn(List.of());
        mockMvc.perform(get("/api/ccodem/BUDGET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/ccodem/budget-period - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getBudgetPeriod_인증_200() throws Exception {
        given(codeService.getBudgetPeriod())
                .willReturn(CodeDto.BudgetPeriodResponse.builder().build());
        mockMvc.perform(get("/api/ccodem/budget-period")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/ccodem - 인증된 사용자 → 201 Created")
    @WithMockUser(username = "10001")
    void createCode_인증_201() throws Exception {
        given(codeService.createCcodem(any())).willReturn("NEW_CODE");
        mockMvc.perform(
                        post("/api/ccodem")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CodeDto.CreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    @DisplayName("POST /api/ccodem - 중복 코드 → 400")
    @WithMockUser(username = "10001")
    void createCode_중복_400() throws Exception {
        doThrow(new IllegalArgumentException("중복 코드")).when(codeService).createCcodem(any());
        mockMvc.perform(
                        post("/api/ccodem")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CodeDto.CreateRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/ccodem/{cId}/{cdva} - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void updateCode_인증_200() throws Exception {
        mockMvc.perform(
                        put("/api/ccodem/CODE001/001")
                                .param("sttDt", "20260101")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CodeDto.UpdateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/ccodem/{cId}/{cdva} - 인증된 사용자 → 204 No Content")
    @WithMockUser(username = "10001")
    void deleteCode_인증_204() throws Exception {
        mockMvc.perform(delete("/api/ccodem/CODE001/001").param("sttDt", "20260101"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/ccodem/{cId}/{cdva} - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void getCcodem_인증_200() throws Exception {
        // Arrange
        given(codeService.getCcodem(anyString(), anyString(), any()))
                .willReturn(CodeDto.Response.builder().build());

        // Act & Assert
        mockMvc.perform(get("/api/ccodem/PRJ_TP/001")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/ccodem/{cId}/{cdva} - 비인증 → 401")
    void getCcodem_비인증_401() throws Exception {
        mockMvc.perform(get("/api/ccodem/PRJ_TP/001")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/ccodem/{cId}/{cdva} - targetDate 파라미터 포함 → 200 OK")
    @WithMockUser(username = "10001")
    void getCcodem_withTargetDate_200() throws Exception {
        // Arrange
        given(codeService.getCcodem(anyString(), anyString(), any()))
                .willReturn(CodeDto.Response.builder().build());

        // Act & Assert
        mockMvc.perform(get("/api/ccodem/PRJ_TP/001").param("targetDate", "2026-01-01"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/ccodem/type/{cTp} - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getCcodemsByCTp_인증_200() throws Exception {
        // Arrange
        given(codeService.getCcodemsByCTp(anyString(), any())).willReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/ccodem/type/IOE_LEAFE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/ccodem/type/{cTp} - 비인증 → 401")
    void getCcodemsByCTp_비인증_401() throws Exception {
        mockMvc.perform(get("/api/ccodem/type/IOE_LEAFE")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/ccodem/type/{cTp} - targetDate 파라미터 포함 → 200 OK")
    @WithMockUser(username = "10001")
    void getCcodemsByCTp_withTargetDate_200() throws Exception {
        // Arrange
        given(codeService.getCcodemsByCTp(anyString(), any())).willReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/ccodem/type/IOE_XPN").param("targetDate", "2026-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
