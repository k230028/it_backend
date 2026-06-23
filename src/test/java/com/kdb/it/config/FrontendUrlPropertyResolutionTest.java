package com.kdb.it.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프론트 URL 단일 환경변수 정합 검증.
 *
 * <p>{@code application.properties}는 {@code app.frontend-url=${APP_FRONTEND_URL:}},
 * {@code cors.allowed-origins=${CORS_ALLOWED_ORIGINS:${app.frontend-url}}}로 정의되어,
 * 프론트 URL 환경변수(APP_FRONTEND_URL) 하나만 지정하면 SSO 복귀 대상과 CORS 허용 오리진이
 * 함께 맞춰집니다. 본 테스트는 실제 properties 파일을 로드해 플레이스홀더 체이닝이 의도대로
 * 해석되는지 확인합니다(매번 두 값을 따로 설정하지 않아도 되는 동작 보장).</p>
 */
class FrontendUrlPropertyResolutionTest {

    /** 실제 application.properties를 로드한 Environment를 구성합니다. overrides는 환경변수처럼 최우선 적용. */
    private StandardEnvironment env(Map<String, Object> overrides) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        if (!overrides.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("test-overrides", overrides));
        }
        env.getPropertySources().addLast(
                new ResourcePropertySource("app", new ClassPathResource("application.properties")));
        return env;
    }

    @Test
    @DisplayName("APP_FRONTEND_URL만 지정하면 app.frontend-url과 cors.allowed-origins가 모두 그 값으로 해석된다")
    void appFrontendUrl_drivesBoth() throws IOException {
        StandardEnvironment env = env(Map.of("APP_FRONTEND_URL", "http://10.9.16.109:3000"));

        assertThat(env.getProperty("app.frontend-url")).isEqualTo("http://10.9.16.109:3000");
        // cors.allowed-origins는 CORS_ALLOWED_ORIGINS 미설정 시 app.frontend-url로 폴백
        assertThat(env.getProperty("cors.allowed-origins")).isEqualTo("http://10.9.16.109:3000");
    }

    @Test
    @DisplayName("CORS_ALLOWED_ORIGINS를 따로 주면 CORS만 그 값으로 오버라이드된다(다중 오리진 대응)")
    void corsAllowedOrigins_overridesIndependently() throws IOException {
        StandardEnvironment env = env(Map.of(
                "APP_FRONTEND_URL", "http://10.9.16.109:3000",
                "CORS_ALLOWED_ORIGINS", "http://10.9.16.109:3000,http://localhost:3000"));

        assertThat(env.getProperty("app.frontend-url")).isEqualTo("http://10.9.16.109:3000");
        assertThat(env.getProperty("cors.allowed-origins"))
                .isEqualTo("http://10.9.16.109:3000,http://localhost:3000");
    }

    @Test
    @DisplayName("둘 다 미설정이면 빈 문자열(허용 오리진 없음 = 거부)로 해석된다")
    void unset_resolvesEmpty() throws IOException {
        StandardEnvironment env = env(Map.of());

        assertThat(env.getProperty("app.frontend-url")).isEmpty();
        assertThat(env.getProperty("cors.allowed-origins")).isEmpty();
    }

    /** local-int 프로파일 properties만 로드한 Environment(실 SSO 테스트 프로파일). */
    private StandardEnvironment localIntEnv(Map<String, Object> overrides) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        if (!overrides.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("test-overrides", overrides));
        }
        env.getPropertySources().addLast(
                new ResourcePropertySource("local-int", new ClassPathResource("application-local-int.properties")));
        return env;
    }

    @Test
    @DisplayName("local-int 프로파일: APP_FRONTEND_URL을 주면 cors까지 그 값으로 오버라이드된다(하드코딩 아님)")
    void localInt_appFrontendUrl_overridesHardcodedCors() throws IOException {
        StandardEnvironment env = localIntEnv(Map.of("APP_FRONTEND_URL", "http://10.9.16.109:3000"));

        assertThat(env.getProperty("app.frontend-url")).isEqualTo("http://10.9.16.109:3000");
        assertThat(env.getProperty("cors.allowed-origins")).isEqualTo("http://10.9.16.109:3000");
    }

    @Test
    @DisplayName("local-int 프로파일: 환경변수 미설정 시 기존 localhost 기본값을 유지한다")
    void localInt_noEnv_keepsLocalhostDefaults() throws IOException {
        StandardEnvironment env = localIntEnv(Map.of());

        assertThat(env.getProperty("app.frontend-url")).isEqualTo("http://localhost:3000");
        assertThat(env.getProperty("cors.allowed-origins"))
                .isEqualTo("http://localhost,http://localhost:3000,http://localhost:3002");
    }
}
