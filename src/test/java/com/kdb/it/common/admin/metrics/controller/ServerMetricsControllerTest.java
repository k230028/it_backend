package com.kdb.it.common.admin.metrics.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import com.kdb.it.common.admin.metrics.service.ServerMetricsService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 인증 경계와 응답 구조를 검증한다. ROLE_ADMIN 강제는 실제 SecurityConfig의 {@code /api/admin/**} 규칙이 담당한다. */
@WebMvcTest(ServerMetricsController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class ServerMetricsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ServerMetricsService service;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/admin/dashboard/server-metrics - 비인증 → 401")
    void getServerMetrics_비인증_401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/server-metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/admin/dashboard/server-metrics - 관리자 → 200 + 인스턴스 목록")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void getServerMetrics_관리자_200() throws Exception {
        Instant at = Instant.parse("2026-09-05T03:00:00Z");
        ServerMetricsDto.Sample sample =
                new ServerMetricsDto.Sample(
                        at, 25.7, 5.0, 8, null, 16L, 12L, 10L, 3L, 500L, 125L, 42, 90L, 3, 7, 0,
                        10);
        given(service.aggregate())
                .willReturn(
                        new ServerMetricsDto.Response(
                                at,
                                10,
                                60,
                                List.of(
                                        new ServerMetricsDto.InstanceMetrics(
                                                "SVR1",
                                                true,
                                                sample,
                                                List.of(sample.toPoint()),
                                                null),
                                        new ServerMetricsDto.InstanceMetrics(
                                                "SVR2", false, null, List.of(), "timeout"))));

        mockMvc.perform(get("/api/admin/dashboard/server-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleIntervalSec").value(10))
                .andExpect(jsonPath("$.historyMinutes").value(60))
                .andExpect(jsonPath("$.instances[0].instanceId").value("SVR1"))
                .andExpect(jsonPath("$.instances[0].self").value(true))
                .andExpect(jsonPath("$.instances[0].latest.systemCpuPct").value(25.7))
                .andExpect(jsonPath("$.instances[0].latest.load1m").doesNotExist())
                .andExpect(jsonPath("$.instances[0].history[0].memUsedPct").value(75.0))
                .andExpect(jsonPath("$.instances[1].peerError").value("timeout"))
                .andExpect(jsonPath("$.instances[1].latest").doesNotExist());
    }
}
