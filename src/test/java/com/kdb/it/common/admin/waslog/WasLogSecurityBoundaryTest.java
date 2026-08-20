package com.kdb.it.common.admin.waslog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.admin.waslog.controller.WasLogController;
import com.kdb.it.common.admin.waslog.controller.WasLogInternalController;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.service.LevelOverrideService;
import com.kdb.it.common.admin.waslog.service.WasLogAuditLogger;
import com.kdb.it.common.admin.waslog.service.WasLogService;
import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.SecurityConfig;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** WAS 로그 API의 인증·인가 경계 검증. 실제 {@link SecurityConfig}를 그대로 적용한다. */
@WebMvcTest({WasLogController.class, WasLogInternalController.class})
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    CookieUtil.class,
    JacksonConfig.class
})
@EnableConfigurationProperties(WasLogProperties.class)
@TestPropertySource(properties = "app.was-log.internal-secret=s3cret")
class WasLogSecurityBoundaryTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private WasLogService service;
    @MockitoBean private WasLogAuditLogger auditLogger;

    // WasLogController 생성자의 Clock 의존성을 채운다. 이 클래스의 테스트는 download()를 호출하지
    // 않으므로 별도 스텁 없이 빈 목으로 충분하다.
    @MockitoBean private Clock clock;

    // WasLogInternalController가 Task 5부터 LevelOverrideService를 생성자로 주입받는다.
    // @WebMvcTest 슬라이스는 @Service 빈을 자동 스캔하지 않으므로 목으로 채워야 컨텍스트가 뜬다.
    @MockitoBean private LevelOverrideService levelOverrideService;

    private static final String BODY =
            """
            {"afterSeq":0,"limit":200,"levels":[],"logger":null,"keyword":null}
            """;

    @Test
    @DisplayName("미인증 요청은 401")
    void 미인증_401() throws Exception {
        mockMvc.perform(get("/api/admin/was-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자 요청은 403")
    @WithMockUser(roles = "USER")
    void 일반사용자_403() throws Exception {
        mockMvc.perform(get("/api/admin/was-logs")).andExpect(status().isForbidden());
    }

    // 아래 두 건은 SecurityConfig의 "/internal/was-logs/**" permitAll 4줄이 실제로 물리는지
    // 증명한다 — 이 4줄을 지워도 WasLogInternalControllerTest(TestSecurityConfig 기반)는
    // 전부 초록이므로, 실제 SecurityConfig를 올리는 이 클래스에만 검증을 둔다.
    @Test
    @DisplayName("내부 경로는 인증 없이도 도달한다 — permitAll이 없으면 이 요청은 401로 막힌다")
    void 내부경로_비인증_토큰일치_200() throws Exception {
        given(service.localSnapshot(any()))
                .willReturn(
                        new WasLogDto.Snapshot(
                                "SVR1", "e1", List.of(), 0L, false, List.of(), null));

        mockMvc.perform(
                        post("/internal/was-logs/snapshot")
                                .header("X-Internal-Token", "s3cret")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("SVR1"));
    }

    @Test
    @DisplayName("내부 경로에서 토큰이 틀리면 컨트롤러가 401을 준다 — 시큐리티 계층에서 막히지 않았다는 증거")
    void 내부경로_비인증_토큰불일치_401() throws Exception {
        var result =
                mockMvc.perform(
                                post("/internal/was-logs/snapshot")
                                        .header("X-Internal-Token", "wrong")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(BODY))
                        .andExpect(status().isUnauthorized())
                        .andReturn();

        // SecurityConfig의 인증 실패 핸들러는 response.sendError(401, ...)로 에러 메시지를 싣는다.
        // 이 401이 그 경로에서 왔다면 getErrorMessage()가 채워지지만, 실제로는 컨트롤러가
        // ResponseEntity.status(401).build()로 직접 만든 응답이라 에러 메시지가 비어 있다 —
        // permitAll이 걷혀 시큐리티가 먼저 막았다면 이 값이 채워져 이 단언이 깨진다.
        assertThat(result.getResponse().getErrorMessage()).isNull();
    }
}
