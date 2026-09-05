package com.kdb.it.common.admin.metrics.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import com.kdb.it.common.admin.metrics.service.ServerMetricsService;
import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.TestSecurityConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WasLogInternalControllerTest와 같은 이유로 TestSecurityConfig·@WithMockUser는 필터 체인 통과용이고, 인증 판정은 컨트롤러의
 * X-Internal-Token 검사가 담당한다. @ConditionalOnExpression이 Environment를 직접 읽으므로 @TestPropertySource로
 * 비밀값을 싣는다.
 */
@WebMvcTest(ServerMetricsInternalController.class)
@Import({TestSecurityConfig.class, ServerMetricsInternalControllerTest.Config.class})
@TestPropertySource(properties = "app.was-log.internal-secret=s3cret")
@WithMockUser
class ServerMetricsInternalControllerTest {

    @TestConfiguration
    static class Config {
        @Bean
        WasLogProperties wasLogProperties() {
            return new WasLogProperties(2000, Map.of(), "s3cret", 1000, 3000);
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ServerMetricsService service;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /internal/server-metrics/snapshot - 토큰 없음·불일치 → 401")
    void snapshot_토큰불일치_401() throws Exception {
        mockMvc.perform(get("/internal/server-metrics/snapshot"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        get("/internal/server-metrics/snapshot")
                                .header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /internal/server-metrics/snapshot - 토큰 일치 → 200 + 로컬 지표")
    void snapshot_토큰일치_200() throws Exception {
        given(service.local())
                .willReturn(
                        new ServerMetricsDto.InstanceMetrics("SVR2", true, null, List.of(), null));

        mockMvc.perform(
                        get("/internal/server-metrics/snapshot")
                                .header("X-Internal-Token", "s3cret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("SVR2"))
                .andExpect(jsonPath("$.self").value(true));
    }
}
