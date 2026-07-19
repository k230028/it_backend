package com.kdb.it.common.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * EnvironmentValidator 단위 테스트 — SEC-01
 *
 * <p>구동 시 필수 환경변수(DB_PASSWORD, JWT_SECRET) 빈값 감지 및 즉시 실패 검증</p>
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentValidatorTest {

    @Mock
    private Environment environment;

    @Test
    @DisplayName("필수 환경변수 전체 정상 설정 시 예외 없음")
    void validate_allVarsSet_noException() {
        given(environment.getProperty("spring.datasource.password")).willReturn("securePassword!");
        given(environment.getProperty("jwt.secret")).willReturn("super-secret-key-at-least-256-bits-long");

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
        env.setProperty("jwt.secret", "super-secret-key-at-least-256-bits-long-xxxxxxxxxxxxxxxxxxxxxxxx");
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
        env.setProperty("jwt.secret", "super-secret-key-at-least-256-bits-long-xxxxxxxxxxxxxxxxxxxxxxxx");
        // gemini/eai/cors/sso 미설정
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
