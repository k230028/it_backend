package com.kdb.it.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.domain.log.listener.AuditFailureRecorder;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Actuator 엔드포인트 접근 제어 검증 (ERR-06 감사 실패 메트릭 보호).
 *
 * <p>실제 {@link SecurityConfig}·{@link JwtAuthenticationFilter}·{@link JwtUtil}과 Actuator 자동설정을 함께
 * 띄워 운영과 동일한 인가 경계를 검증합니다. Oracle·JPA 자동설정은 {@code test} 프로파일 ({@code
 * application-test.properties})에서 제외되므로 DB 없이 실행되며 {@code ./gradlew test} 게이트에 포함됩니다.
 *
 * <p>브라우저 인증 경로와 동일하게 {@code accessToken} httpOnly 쿠키에 담긴 유효 JWT로만 권한을 판단함을 확인합니다.
 *
 * <ul>
 *   <li>비인증 {@code /actuator/health} → 200 (공개)
 *   <li>비인증 {@code /actuator/metrics} → 401 (인증 필요)
 *   <li>일반 사용자 {@code /actuator/metrics} → 403 (관리자 전용)
 *   <li>관리자 {@code /actuator/metrics/audit.log.write.failure} → 200 (카운터 생성 후)
 * </ul>
 */
@SpringBootTest(classes = SecurityConfigTest.ActuatorSecurityTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    /** 감사 실패 카운터 이름 ({@link AuditFailureRecorder}와 동일). */
    private static final String AUDIT_FAILURE_METRIC = "audit.log.write.failure";

    @Autowired private MockMvc mockMvc;

    @Autowired private JwtUtil jwtUtil;

    @Autowired private AuditFailureRecorder auditFailureRecorder;

    @Test
    @DisplayName("비인증 /actuator/health 는 공개되어 200")
    void health_unauthenticated_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("기본값에서는 비인증 /v3/api-docs 가 공개되어 200")
    void openApiDocs_unauthenticated_returns200WhenEnabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("비인증 /actuator/metrics 는 인증이 필요해 401")
    void metrics_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자 /actuator/metrics 는 관리자 전용이라 403")
    void metrics_normalUser_returns403() throws Exception {
        mockMvc.perform(
                        get("/actuator/metrics")
                                .cookie(accessTokenCookie(List.of(CustomUserDetails.ATH_USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 감사 실패 카운터 생성 후 /actuator/metrics/{name} 를 200 으로 조회")
    void metrics_admin_returnsCounter200() throws Exception {
        // 메트릭이 존재해야 200 — 미존재 시 404. 관리자 조회 전에 카운터를 1회 증가시킨다.
        auditFailureRecorder.record(
                "Bprojm", "PRJ-1", "U", "afterCommit", new RuntimeException("테스트 감사 실패"));

        mockMvc.perform(
                        get("/actuator/metrics/" + AUDIT_FAILURE_METRIC)
                                .cookie(accessTokenCookie(List.of(CustomUserDetails.ATH_ADMIN))))
                .andExpect(status().isOk());
    }

    /**
     * 주어진 자격등급으로 유효한 {@code accessToken} JWT 쿠키를 생성합니다(운영 브라우저 인증 경로와 동일).
     *
     * @param athIds 자격등급 ID 목록(예: {@link CustomUserDetails#ATH_ADMIN})
     * @return {@code accessToken} 쿠키
     */
    private Cookie accessTokenCookie(List<String> athIds) {
        String token = jwtUtil.generateAccessToken("TESTER", athIds, "D001");
        return new Cookie(CookieUtil.ACCESS_TOKEN_COOKIE, token);
    }

    /**
     * Actuator·Security 만 최소로 띄우는 테스트 부트 설정.
     *
     * <p>{@code test} 프로파일이 DataSource/JPA 자동설정을 제외하므로 DB 없이 컨텍스트가 로드됩니다. 실제 {@link
     * SecurityConfig}·JWT 컴포넌트·{@link AuditFailureRecorder}만 등록하고, {@code MeterRegistry}와 Actuator
     * 엔드포인트는 자동설정으로 확보합니다.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        SecurityConfig.class,
        SwaggerConfig.class,
        JwtAuthenticationFilter.class,
        JwtUtil.class,
        AuditFailureRecorder.class
    })
    static class ActuatorSecurityTestApp {}
}
