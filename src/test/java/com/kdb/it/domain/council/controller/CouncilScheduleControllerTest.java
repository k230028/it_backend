package com.kdb.it.domain.council.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CouncilService;
import com.kdb.it.domain.council.service.ScheduleService;
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
 * CouncilScheduleController @WebMvcTest
 *
 * <p>HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest(CouncilScheduleController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CouncilScheduleControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ScheduleService scheduleService;
    @MockitoBean private CouncilService councilService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // =========================================================================
    // M6: 일정
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/schedule - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getScheduleStatus_인증_200() throws Exception {
        given(scheduleService.getScheduleStatus(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/schedule")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/schedule - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void submitSchedule_인증_200() throws Exception {
        var request =
                new CouncilDto.ScheduleRequest(
                        List.of(new CouncilDto.ScheduleItem("20260701", "10:00", "Y")), "N");
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/schedule")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/schedule/confirm - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void confirmSchedule_인증_200() throws Exception {
        var request =
                new CouncilDto.ScheduleConfirmRequest(
                        java.time.LocalDate.of(2026, 7, 1), "10:00", "회의실 A");
        mockMvc.perform(
                        put("/api/council/" + ASCT_ID + "/schedule/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // M6: 내 일정 조회 (평가위원 본인)
    // =========================================================================

    /**
     * getMySchedule: GET /api/council/{asctId}/schedule/my scheduleService.getMySchedule(asctId,
     * eno) 위임 및 배열 응답 확인
     */
    @Test
    @DisplayName("GET /api/council/{asctId}/schedule/my - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getMySchedule_인증_200() throws Exception {
        // Arrange
        var slot = new CouncilDto.ScheduleSlotResponse("20260701", "10:00", "Y");
        given(scheduleService.getMySchedule(anyString(), anyString())).willReturn(List.of(slot));

        // Act & Assert — @AuthenticationPrincipal CustomUserDetails.getEno() 사용을 위해 실제 principal 주입
        mockMvc.perform(
                        get("/api/council/" + ASCT_ID + "/schedule/my")
                                .with(
                                        user(
                                                new CustomUserDetails(
                                                        "10001",
                                                        List.of(CustomUserDetails.ATH_USER),
                                                        "D001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].dsdDt").value("20260701"));
    }

    // =========================================================================
    // PRD_c_20260620 #1: 서면개최 확정
    // =========================================================================

    /**
     * confirmWrittenMeeting: PUT /api/council/{asctId}/schedule/confirm-written
     * scheduleService.confirmWrittenMeeting(asctId) 위임 확인
     */
    @Test
    @DisplayName("PUT /api/council/{asctId}/schedule/confirm-written - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void confirmWrittenMeeting_인증_200() throws Exception {
        // Arrange: void 서비스 — 스텁 불필요

        // Act & Assert
        mockMvc.perform(put("/api/council/" + ASCT_ID + "/schedule/confirm-written"))
                .andExpect(status().isOk());
    }
}
