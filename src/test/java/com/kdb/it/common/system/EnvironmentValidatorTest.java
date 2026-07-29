package com.kdb.it.common.system;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * EnvironmentValidator 단위 테스트 — SEC-01
 *
 * <p>구동 시 필수 환경변수(DB_PASSWORD, JWT_SECRET) 빈값 감지 및 즉시 실패 검증
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentValidatorTest {

    @Mock private Environment environment;

    @Test
    @DisplayName("필수 환경변수 전체 정상 설정 시 예외 없음")
    void validate_allVarsSet_noException() {
        given(environment.getProperty("spring.datasource.password")).willReturn("securePassword!");
        given(environment.getProperty("jwt.secret"))
                .willReturn("super-secret-key-at-least-256-bits-long");

        EnvironmentValidator validator = new EnvironmentValidator(environment);
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DB_PASSWORD 빈값 시 IllegalStateException 발생 — 메시지에 환경변수명 포함")
    void validate_blankDbPassword_throwsIllegalState() {
        given(environment.getProperty("spring.datasource.password")).willReturn("");

        EnvironmentValidator validator = new EnvironmentValidator(environment);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("DB_PASSWORD null 시 IllegalStateException 발생")
    void validate_nullDbPassword_throwsIllegalState() {
        given(environment.getProperty("spring.datasource.password")).willReturn(null);

        EnvironmentValidator validator = new EnvironmentValidator(environment);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("JWT_SECRET 빈값 시 IllegalStateException 발생 — 메시지에 환경변수명 포함")
    void validate_blankJwtSecret_throwsIllegalState() {
        given(environment.getProperty("spring.datasource.password")).willReturn("securePassword!");
        given(environment.getProperty("jwt.secret")).willReturn("   ");

        EnvironmentValidator validator = new EnvironmentValidator(environment);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    // ── 운영 프로파일 전용 키 검증 (T8) ───────────────────────────────────

    private MockEnvironment prodEnvWithAllRequired() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.password", "pw");
        env.setProperty(
                "jwt.secret", "super-secret-key-at-least-256-bits-long-xxxxxxxxxxxxxxxxxxxxxxxx");
        env.setProperty("gemini.api.key", "gk-real-key");
        env.setProperty("eai.enabled", "true");
        env.setProperty("eai.url", "http://eai.internal/std");
        env.setProperty("cors.allowed-origins", "https://it.kdb.co.kr");
        env.setProperty("app.sso.allow-direct-eno", "false");
        env.setProperty("sso.mock-enabled", "false");
        env.setProperty("app.auth.allow-bearer-header", "false");
        env.setProperty("app.cookie.secure", "true");
        env.setProperty("app.frontend-url", "https://it.kdb.co.kr");
        return env;
    }

    @ParameterizedTest
    @ValueSource(strings = {"sso.mock-enabled", "app.auth.allow-bearer-header"})
    @DisplayName("운영 프로파일에서 인증 우회 토글이 true면 기동 차단")
    void validate_prodBypassToggleTrue_throws(String key) {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty(key, "true");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(key);
    }

    @Test
    @DisplayName("운영 프로파일에서 app.cookie.secure=false면 기동 차단")
    void validate_prodCookieNotSecure_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.cookie.secure", "false");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.cookie.secure");
    }

    @Test
    @DisplayName("운영 프로파일에서 모든 운영 필수 키가 채워지면 예외 없음")
    void validate_prodAllKeysSet_noException() {
        EnvironmentValidator validator = new EnvironmentValidator(prodEnvWithAllRequired());
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("운영 프로파일에서 상위 우선순위 API docs=true override는 기동 차단")
    void validate_prodApiDocsEnabledOverride_throws() throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithFileOverrides(Map.of("springdoc.api-docs.enabled", "true"));

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("springdoc.api-docs.enabled");
    }

    @Test
    @DisplayName("운영 프로파일에서 상위 우선순위 Swagger UI=true override는 기동 차단")
    void validate_prodSwaggerUiEnabledOverride_throws() throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithFileOverrides(Map.of("springdoc.swagger-ui.enabled", "true"));

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("springdoc.swagger-ui.enabled");
    }

    @Test
    @DisplayName("운영 프로파일에서 파일 기본값의 OpenAPI 비활성화는 정상 기동")
    void validate_prodOpenApiDisabledByProfileFile_noException() throws IOException {
        StandardEnvironment env = prodEnvironmentWithFileOverrides(Map.of());

        assertThatCode(() -> new EnvironmentValidator(env).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("운영 프로파일에서 gemini.api.key 빈값이면 기동 차단")
    void validate_prodBlankGeminiKey_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("gemini.api.key", "");
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    @DisplayName("운영 프로파일에서 eai.enabled=true인데 eai.url 빈값이면 기동 차단")
    void validate_prodEaiEnabledBlankUrl_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("eai.url", "");
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EAI_URL");
    }

    @Test
    @DisplayName("운영 프로파일에서 eai.enabled=false면 eai.url 빈값이어도 통과")
    void validate_prodEaiDisabledBlankUrl_noException() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("eai.enabled", "false");
        env.setProperty("eai.url", "");
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("운영 프로파일에서 cors.allowed-origins가 와일드카드(*)면 기동 차단")
    void validate_prodCorsWildcard_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("cors.allowed-origins", "*");
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cors.allowed-origins");
    }

    @Test
    @DisplayName("운영 프로파일에서 cors.allowed-origins 빈값이면 기동 차단")
    void validate_prodCorsBlank_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("cors.allowed-origins", "");
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cors.allowed-origins");
    }

    @Test
    @DisplayName("운영 프로파일에서 app.sso.allow-direct-eno=true면 기동 차단")
    void validate_prodSsoDirectEnoTrue_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.sso.allow-direct-eno", "true");
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allow-direct-eno");
    }

    @Test
    @DisplayName("운영 프로파일에서 app.dev.user-switch.enabled=true면 기동 차단")
    void validate_prod_devUserSwitchEnabled_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.dev.user-switch.enabled", "true");
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user-switch");
    }

    @Test
    @DisplayName("운영 프로파일에서 app.frontend-url 빈값이면 기동 차단")
    void validate_prodBlankFrontendUrl_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.frontend-url", "");
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.frontend-url");
    }

    @Test
    @DisplayName("비운영 프로파일(local-ext)에서는 운영 전용 키가 비어도 통과")
    void validate_nonProdProfile_skipsProdKeys() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local-ext");
        env.setProperty("spring.datasource.password", "pw");
        env.setProperty(
                "jwt.secret", "super-secret-key-at-least-256-bits-long-xxxxxxxxxxxxxxxxxxxxxxxx");
        // gemini/eai/cors/sso 미설정
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    /** prod 파일보다 우선하는 override를 포함한 격리 Environment를 구성한다. */
    private StandardEnvironment prodEnvironmentWithFileOverrides(Map<String, Object> overrides)
            throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.setActiveProfiles("prod");

        Map<String, Object> requiredProperties = new HashMap<>();
        requiredProperties.put("spring.datasource.password", "pw");
        requiredProperties.put(
                "jwt.secret", "super-secret-key-at-least-256-bits-long-xxxxxxxxxxxxxxxxxxxxxxxx");
        requiredProperties.put("gemini.api.key", "gk-real-key");
        requiredProperties.put("eai.enabled", "false");
        requiredProperties.put("cors.allowed-origins", "https://it.kdb.co.kr");
        requiredProperties.put("app.sso.allow-direct-eno", "false");
        requiredProperties.put("sso.mock-enabled", "false");
        requiredProperties.put("app.auth.allow-bearer-header", "false");
        requiredProperties.put("app.cookie.secure", "true");
        requiredProperties.put("app.frontend-url", "https://it.kdb.co.kr");
        requiredProperties.putAll(overrides);

        env.getPropertySources()
                .addLast(
                        new ResourcePropertySource(
                                "prod-profile", new ClassPathResource("application-prod.properties")));
        env.getPropertySources().addFirst(new MapPropertySource("test-overrides", requiredProperties));
        return env;
    }
}
