package com.kdb.it.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.security.SimpleRequestCsrfFilter;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.domain.log.listener.AuditFailureRecorder;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

    @Autowired private SecurityProbeController securityProbeController;

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

    @Test
    @DisplayName("관리자 스트리밍 응답은 ASYNC 재디스패치에서도 200으로 완료된다")
    void adminStream_admin_returns200AfterAsyncDispatch() throws Exception {
        MvcResult started =
                mockMvc.perform(
                                get("/api/admin/security-probe/stream")
                                        .cookie(
                                                accessTokenCookie(
                                                        List.of(CustomUserDetails.ATH_ADMIN))))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        // MockHttpServletResponse는 실제 컨테이너 응답과 달리 헤더 Map이 thread-safe하지 않다.
        // 최초 보안 필터 체인이 헤더를 다 쓴 뒤 스트림 작업을 풀어 비동기 인가 결과만 검증한다.
        securityProbeController.releaseStream();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string("stream-ok"));
    }

    @Test
    @DisplayName("일반 사용자는 관리자 스트리밍 응답을 시작하기 전에 403으로 차단된다")
    void adminStream_normalUser_returns403BeforeAsyncDispatch() throws Exception {
        mockMvc.perform(
                        get("/api/admin/security-probe/stream")
                                .cookie(accessTokenCookie(List.of(CustomUserDetails.ATH_USER))))
                .andExpect(status().isForbidden())
                .andExpect(request().asyncNotStarted());
    }

    @Test
    @DisplayName("악성 Origin의 게시물 조회수 POST는 CORS 필터에서 403으로 차단한다")
    void postView_maliciousOrigin_returns403() throws Exception {
        mockMvc.perform(
                        post("/api/boards/BLB-1/posts/NAC-1/views")
                                .header(HttpHeaders.ORIGIN, "https://evil.example")
                                .cookie(accessTokenCookie(List.of(CustomUserDetails.ATH_USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("허용 Origin의 게시물 조회수 POST는 성공 응답과 CORS 헤더를 함께 반환한다")
    void postView_allowedOrigin_returns204WithCorsHeader() throws Exception {
        mockMvc.perform(
                        post("/api/boards/BLB-1/posts/NAC-1/views")
                                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                                .cookie(accessTokenCookie(List.of(CustomUserDetails.ATH_USER))))
                .andExpect(status().isNoContent())
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "http://localhost:3000"))
                .andExpect(content().string(""));
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
        SimpleRequestCsrfFilter.class,
        JwtUtil.class,
        // SecurityConfig → CookieUtil → ObjectMapper 의존을 운영과 같은 빈으로 채웁니다.
        CookieUtil.class,
        JacksonConfig.class,
        AuditFailureRecorder.class,
        SecurityProbeController.class
    })
    static class ActuatorSecurityTestApp {}

    @RestController
    static class SecurityProbeController {

        private final CountDownLatch streamRelease = new CountDownLatch(1);

        @GetMapping("/api/admin/security-probe/stream")
        ResponseEntity<StreamingResponseBody> stream() {
            return ResponseEntity.ok()
                    .body(
                            output -> {
                                try {
                                    streamRelease.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException("스트리밍 테스트 대기 중 중단됨", e);
                                }
                                output.write("stream-ok".getBytes(StandardCharsets.UTF_8));
                            });
        }

        void releaseStream() {
            streamRelease.countDown();
        }

        @PostMapping("/api/boards/{blbMngNo}/posts/{nacMngNo}/views")
        ResponseEntity<Void> mutate() {
            // 실제 조회수 명령 경로가 FilterChain의 CORS 경계를 통과하는지만 검증한다.
            return ResponseEntity.noContent().build();
        }
    }
}
