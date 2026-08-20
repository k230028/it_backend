package com.kdb.it.common.admin.waslog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.service.WasLogService;
import com.kdb.it.common.system.security.JwtUtil;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// TestSecurityConfig를 함께 로드해 CSRF를 끈다 — 실제 SecurityConfig는 /internal/was-logs/**를
// permitAll·CSRF 비활성으로 처리하지만, @WebMvcTest 슬라이스는 SecurityConfig를 로드하지 않고
// 기본 Security 자동설정(CSRF 활성)이 대신 적용되므로 POST가 403으로 막힌다. 인증 자체는
// 컨트롤러가 X-Internal-Token으로 직접 판정하므로 @WithMockUser는 필터 체인 통과용일 뿐이다.
//
// @ConditionalOnExpression은 Environment 프로퍼티 app.was-log.internal-secret을 직접 읽는다.
// Config의 WasLogProperties 빈을 수동 생성해도 Environment 값 자체는 채워지지 않아 조건이
// false로 평가되어 컨트롤러 빈이 아예 등록되지 않는다(모든 요청이 404). @TestPropertySource로
// 같은 값을 Environment에도 실어 프로덕션과 동일하게 조건이 참이 되게 한다.
@WebMvcTest(WasLogInternalController.class)
@Import({TestSecurityConfig.class, WasLogInternalControllerTest.Config.class})
@TestPropertySource(properties = "app.was-log.internal-secret=s3cret")
@WithMockUser
class WasLogInternalControllerTest {

    @TestConfiguration
    static class Config {
        @Bean
        WasLogProperties wasLogProperties() {
            return new WasLogProperties(2000, Map.of(), "s3cret", 1000, 3000);
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private WasLogService service;

    // WasLogController와 마찬가지로 @WebMvcTest 슬라이스가 시큐리티 필터 체인을 함께 로드하며
    // JwtAuthenticationFilter 생성자 의존성을 채우기 위해 필요하다. 이 컨트롤러는 X-Internal-Token으로
    // 직접 인증하므로 실제 JWT 검증 로직은 이 테스트에서 쓰이지 않는다.
    @MockitoBean private JwtUtil jwtUtil;

    private static final String BODY =
            """
            {"afterSeq":0,"limit":200,"levels":[],"logger":null,"keyword":null}
            """;

    @Test
    @DisplayName("올바른 토큰이면 로컬 스냅샷을 반환한다")
    void snapshot_토큰일치() throws Exception {
        given(service.localSnapshot(any()))
                .willReturn(
                        new WasLogDto.Snapshot(
                                "SVR2", "e2", List.of(), 0L, false, List.of(), null));

        mockMvc.perform(
                        post("/internal/was-logs/snapshot")
                                .header("X-Internal-Token", "s3cret")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("SVR2"));
    }

    @Test
    @DisplayName("토큰이 다르면 401이고 본문이 없다")
    void snapshot_토큰불일치_401() throws Exception {
        mockMvc.perform(
                        post("/internal/was-logs/snapshot")
                                .header("X-Internal-Token", "wrong")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰 헤더가 없으면 401")
    void snapshot_토큰없음_401() throws Exception {
        mockMvc.perform(
                        post("/internal/was-logs/snapshot")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
