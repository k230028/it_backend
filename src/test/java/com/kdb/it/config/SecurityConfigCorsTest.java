package com.kdb.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import com.kdb.it.common.system.security.SimpleRequestCsrfFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** SecurityConfig CORS 설정 단위 테스트 (T9a) — 허용 헤더 명시화/오리진 검증. */
class SecurityConfigCorsTest {

    private CorsConfiguration corsFor(String origins) {
        SecurityConfig config =
                new SecurityConfig(
                        Mockito.mock(JwtAuthenticationFilter.class),
                        Mockito.mock(SimpleRequestCsrfFilter.class));
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        // "/**" 매핑을 직접 조회 — 요청 mock보다 결정적
        return source.getCorsConfigurations().get("/**");
    }

    @Test
    @DisplayName("allowedHeaders는 와일드카드(*)가 아니라 명시 헤더 목록이어야 한다")
    void allowedHeaders_명시목록() {
        CorsConfiguration cors = corsFor("https://it.kdb.co.kr");
        assertThat(cors.getAllowedHeaders())
                .containsExactlyInAnyOrder("Content-Type", "Authorization", "X-Requested-With");
        assertThat(cors.getAllowedHeaders()).doesNotContain("*");
    }

    @Test
    @DisplayName("allowCredentials=true 와 함께 명시 오리진을 허용한다")
    void allowedOrigins_명시오리진() {
        CorsConfiguration cors = corsFor("https://it.kdb.co.kr");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://it.kdb.co.kr");
    }

    @Test
    @DisplayName("콤마+공백이 섞인 입력은 빈 항목을 걸러내고 비공백 오리진만 남긴다")
    void allowedOrigins_빈항목필터링() {
        CorsConfiguration cors = corsFor("https://a.example.com, ,");
        assertThat(cors.getAllowedOrigins()).containsExactly("https://a.example.com");
        assertThat(cors.getAllowedOrigins()).doesNotContain("");
    }

    @Test
    @DisplayName("빈 문자열 입력은 빈 오리진 목록으로 설정된다(전체 차단, 리터럴 [\"\"] 미발생)")
    void allowedOrigins_빈값_빈목록() {
        CorsConfiguration cors = corsFor("");
        assertThat(cors.getAllowedOrigins()).isEmpty();
    }

    // ── SSO 콜백 CORS 회귀 (Invalid CORS request 방지) ──────────────────────

    /** allowlist에 없는 외부 origin(ESSO 인증서버/내부망 IP 등)을 흉내내는 값. */
    private static final String EXTERNAL_ORIGIN = "http://intesso.kdb.co.kr:20080";

    private CorsConfiguration configFor(String uri) {
        SecurityConfig config =
                new SecurityConfig(
                        Mockito.mock(JwtAuthenticationFilter.class),
                        Mockito.mock(SimpleRequestCsrfFilter.class));
        ReflectionTestUtils.setField(
                config, "allowedOrigins", "http://localhost:3000,http://localhost:3002");
        CorsConfigurationSource source = config.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return source.getCorsConfiguration(request);
    }

    @Test
    @DisplayName("/sso/checkauth 는 allowlist에 없는 외부 origin도 허용한다 (ESSO 콜백 차단 방지)")
    void ssoCallback_allowsExternalOrigin() {
        CorsConfiguration cfg = configFor("/sso/checkauth");
        assertThat(cfg).isNotNull();
        // checkOrigin이 non-null을 반환하면 CorsFilter가 "Invalid CORS request"(403)로 거부하지 않는다.
        assertThat(cfg.checkOrigin(EXTERNAL_ORIGIN)).isNotNull();
    }

    @Test
    @DisplayName("/sso/logout POST는 SSO CORS 경계를 사용하되 교차 출처 자격증명은 허용하지 않는다")
    void ssoLogout_postAllowedWithoutCredentials() {
        CorsConfiguration cfg = configFor("/sso/logout");

        assertThat(cfg).isNotNull();
        assertThat(cfg.getAllowedMethods()).contains("POST");
        assertThat(cfg.getAllowCredentials()).isFalse();
    }

    @Test
    @DisplayName("일반 API 경로는 allowlist origin만 허용하고 그 외 origin은 거부한다")
    void apiPath_restrictsToAllowlist() {
        CorsConfiguration cfg = configFor("/api/projects");
        assertThat(cfg).isNotNull();
        assertThat(cfg.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
        // allowlist에 없는 origin은 거부(null) → SPA 보안 정책 유지
        assertThat(cfg.checkOrigin(EXTERNAL_ORIGIN)).isNull();
    }

    @Test
    @DisplayName("API와 SSO CORS는 자격증명·허용 Origin·메서드 경계를 분리한다")
    void apiAndSsoCors_credentialAndOriginBoundaries() {
        SecurityConfig config =
                new SecurityConfig(
                        Mockito.mock(JwtAuthenticationFilter.class),
                        Mockito.mock(SimpleRequestCsrfFilter.class));
        ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:3000");
        CorsConfigurationSource source = config.corsConfigurationSource();

        CorsConfiguration apiCors = corsFor(source, "/api/projects");
        CorsConfiguration ssoCors = corsFor(source, "/sso/checkauth");

        assertThat(apiCors.getAllowCredentials()).isTrue();
        assertThat(apiCors.getAllowedOriginPatterns()).isNullOrEmpty();
        assertThat(apiCors.checkOrigin("https://evil.example")).isNull();

        assertThat(ssoCors.getAllowCredentials()).isFalse();
        assertThat(ssoCors.checkOrigin("https://esso.example")).isNotNull();
        assertThat(ssoCors.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "OPTIONS");
    }

    private CorsConfiguration corsFor(CorsConfigurationSource source, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return source.getCorsConfiguration(request);
    }
}
