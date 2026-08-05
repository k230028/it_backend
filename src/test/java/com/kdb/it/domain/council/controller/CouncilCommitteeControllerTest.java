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
import com.kdb.it.domain.council.service.CommitteeService;
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
 * CouncilCommitteeController @WebMvcTest
 *
 * <p>HTTP 응답 구조와 인증 동작을 검증합니다.
 */
@WebMvcTest(CouncilCommitteeController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CouncilCommitteeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CommitteeService committeeService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    /** 핵심 필드 제약을 충족하는 평가위원 선정 요청 (members 비어있지 않음) */
    private static CouncilDto.CommitteeRequest validCommitteeRequest() {
        return new CouncilDto.CommitteeRequest(
                "INFO_SYS", List.of(new CouncilDto.CommitteeMemberRequest("10002", "MAND")));
    }

    // =========================================================================
    // M6: 평가위원
    // =========================================================================

    @Test
    @DisplayName("GET /api/council/{asctId}/committee/default - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getDefaultCommittee_인증_200() throws Exception {
        given(committeeService.getDefaultCommittee(ASCT_ID)).willReturn(List.of());
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/committee/default"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/council/{asctId}/committee - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getCommittee_인증_200() throws Exception {
        given(committeeService.getCommittee(ASCT_ID)).willReturn(null);
        mockMvc.perform(get("/api/council/" + ASCT_ID + "/committee")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/council/{asctId}/committee - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void saveCommittee_인증_200() throws Exception {
        mockMvc.perform(
                        post("/api/council/" + ASCT_ID + "/committee")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validCommitteeRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/council/{asctId}/committee - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void updateCommittee_인증_200() throws Exception {
        mockMvc.perform(
                        put("/api/council/" + ASCT_ID + "/committee")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validCommitteeRequest())))
                .andExpect(status().isOk());
    }
}
