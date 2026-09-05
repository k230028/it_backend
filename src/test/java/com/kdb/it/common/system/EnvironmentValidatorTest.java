package com.kdb.it.common.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
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
        lenient()
                .when(environment.getProperty("security.token-fingerprint-secret"))
                .thenReturn("token-fingerprint-secret-at-least-256-bits");

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
        lenient()
                .when(environment.getProperty("security.token-fingerprint-secret"))
                .thenReturn("token-fingerprint-secret-at-least-256-bits");

        EnvironmentValidator validator = new EnvironmentValidator(environment);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("TOKEN_FINGERPRINT_SECRET 빈값 시 IllegalStateException 발생 — 메시지에 환경변수명 포함")
    void validate_blankTokenFingerprintSecret_throwsIllegalState() {
        given(environment.getProperty("spring.datasource.password")).willReturn("securePassword!");
        given(environment.getProperty("jwt.secret"))
                .willReturn("super-secret-key-at-least-256-bits-long");
        given(environment.getProperty("security.token-fingerprint-secret")).willReturn("   ");

        EnvironmentValidator validator = new EnvironmentValidator(environment);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOKEN_FINGERPRINT_SECRET");
    }

    @Test
    @DisplayName("TOKEN_FINGERPRINT_SECRET가 UTF-8 32바이트보다 짧으면 기동 차단")
    void validate_shortTokenFingerprintSecret_throwsIllegalState() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.password", "securePassword!");
        env.setProperty("jwt.secret", "super-secret-key-at-least-256-bits-long");
        env.setProperty("security.token-fingerprint-secret", "short-secret");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOKEN_FINGERPRINT_SECRET")
                .hasMessageContaining("32바이트");
    }

    // ── 운영 프로파일 전용 키 검증 (T8) ───────────────────────────────────

    private MockEnvironment prodEnvWithAllRequired() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.password", "pw");
        env.setProperty(
                "jwt.secret", "super-secret-key-at-least-256-bits-long-xxxxxxxxxxxxxxxxxxxxxxxx");
        env.setProperty(
                "security.token-fingerprint-secret",
                "token-fingerprint-secret-at-least-256-bits-long-xxxxxxxxxxxx");
        env.setProperty("gemini.api.key", "gk-real-key");
        env.setProperty("eai.enabled", "true");
        env.setProperty("eai.url", "http://eai.internal/std");
        env.setProperty("cors.allowed-origins", "https://it.kdb.co.kr");
        env.setProperty("app.sso.allow-direct-eno", "false");
        env.setProperty("sso.mock-enabled", "false");
        env.setProperty("app.auth.allow-bearer-header", "false");
        env.setProperty("springdoc.api-docs.enabled", "false");
        env.setProperty("springdoc.swagger-ui.enabled", "false");
        env.setProperty("app.cookie.secure", "true");
        env.setProperty("server.servlet.session.cookie.secure", "true");
        env.setProperty("server.servlet.session.cookie.http-only", "true");
        env.setProperty("server.servlet.session.cookie.same-site", "lax");
        env.setProperty("app.frontend-url", "https://it.kdb.co.kr");
        env.setProperty("app.approval.it-budget.preview.active-key-id", "prod-v2");
        env.setProperty(
                "app.approval.it-budget.preview.active-signing-key",
                "preview-signing-key-for-production-minimum-32-bytes");
        return env;
    }

    @Test
    @DisplayName("운영 프로파일에서 미리보기 활성 키 ID가 없으면 기동을 차단한다")
    void validate_prodMissingPreviewActiveKeyId_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.approval.it-budget.preview.active-key-id", " ");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IT_BUDGET_PREVIEW_ACTIVE_KEY_ID");
    }

    @Test
    @DisplayName("운영 프로파일에서 미리보기 활성 서명 키가 UTF-8 32바이트보다 짧으면 기동을 차단한다")
    void validate_prodShortPreviewActiveSigningKey_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.approval.it-budget.preview.active-signing-key", "가나다라마바사아자차");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IT_BUDGET_PREVIEW_SIGNING_KEY")
                .hasMessageContaining("32바이트");
    }

    @Test
    @DisplayName("운영 프로파일에서 직전 키 ID와 서명 키는 함께 설정해야 한다")
    void validate_prodPreviewPreviousKeyPairRequired_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.approval.it-budget.preview.previous-key-id", "prod-v1");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("previous-key-id")
                .hasMessageContaining("previous-signing-key");
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

    @ParameterizedTest
    @ValueSource(strings = {"true", "on", "yes", "1"})
    @DisplayName("운영 위험 토글은 Spring이 true로 바인딩하는 모든 표현을 차단")
    void validate_prodDirectEnoSpringTrueAliases_throws(String rawValue) {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.sso.allow-direct-eno", rawValue);

        assertThat(env.getProperty("app.sso.allow-direct-eno", Boolean.class)).isTrue();
        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: app.sso.allow-direct-eno");
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "off", "no", "0"})
    @DisplayName("운영 위험 토글은 Spring이 false로 바인딩하는 모든 표현을 허용")
    void validate_prodDirectEnoSpringFalseAliases_noException(String rawValue) {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.sso.allow-direct-eno", rawValue);

        assertThat(env.getProperty("app.sso.allow-direct-eno", Boolean.class)).isFalse();
        assertThatCode(() -> new EnvironmentValidator(env).validate()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "invalid"})
    @DisplayName("운영 위험 토글의 공백·잘못된 값은 fail-closed로 기동 차단")
    void validate_prodDirectEnoInvalidBoolean_throwsWithoutValue(String rawValue) {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.sso.allow-direct-eno", rawValue);

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: app.sso.allow-direct-eno");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("dangerousSystemEnvironmentProperties")
    @DisplayName("운영 위험 환경변수의 on 표현은 런타임 Boolean 바인딩과 동일하게 기동 차단")
    void validate_prodDangerousSystemEnvironmentOn_throws(String environmentKey, String propertyKey)
            throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithSystemEnvironment(Map.of(environmentKey, "on"));

        assertThat(env.getProperty(propertyKey, Boolean.class)).isTrue();
        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: " + propertyKey);
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

    @ParameterizedTest
    @ValueSource(
            strings = {
                "server.servlet.session.cookie.secure",
                "server.servlet.session.cookie.http-only"
            })
    @DisplayName("운영 세션 쿠키의 Secure·HttpOnly가 false이면 기동 차단")
    void validate_prodSessionCookieBooleanFalse_throws(String key) {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty(key, "false");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: " + key);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "server.servlet.session.cookie.secure",
                "server.servlet.session.cookie.http-only"
            })
    @DisplayName("운영 세션 쿠키 Boolean의 공백·잘못된 값은 fail-closed로 기동 차단")
    void validate_prodSessionCookieBooleanInvalid_throws(String key) {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty(key, "invalid");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: " + key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lax", "LAX", "Lax"})
    @DisplayName("운영 세션 쿠키 SameSite는 Spring enum 바인딩으로 Lax 표현을 허용")
    void validate_prodSessionCookieSameSiteLax_noException(String rawValue) {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("server.servlet.session.cookie.same-site", rawValue);

        assertThatCode(() -> new EnvironmentValidator(env).validate()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "strict", "invalid", " "})
    @DisplayName("운영 세션 쿠키 SameSite가 Lax가 아니거나 잘못된 값이면 기동 차단")
    void validate_prodSessionCookieSameSiteUnsafe_throws(String rawValue) {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("server.servlet.session.cookie.same-site", rawValue);

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: server.servlet.session.cookie.same-site");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("unsafeSessionCookieSystemEnvironmentProperties")
    @DisplayName("운영 세션 쿠키의 OS 환경변수 override는 실제 Spring 바인딩 후 기동 차단")
    void validate_prodSessionCookieSystemEnvironmentOverride_throws(
            String environmentKey, String propertyKey) throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithSystemEnvironment(Map.of(environmentKey, "false"));

        assertThat(env.getProperty(propertyKey, Boolean.class)).isFalse();
        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: " + propertyKey);
    }

    @Test
    @DisplayName("운영 세션 쿠키 SameSite의 OS 환경변수 none override는 기동 차단")
    void validate_prodSessionCookieSameSiteSystemEnvironmentOverride_throws() throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithSystemEnvironment(
                        Map.of("SERVER_SERVLET_SESSION_COOKIE_SAME_SITE", "none"));

        assertThat(env.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("none");
        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: server.servlet.session.cookie.same-site");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "server.servlet.session.cookie.secure",
                "server.servlet.session.cookie.http-only",
                "server.servlet.session.cookie.same-site"
            })
    @DisplayName("운영 세션 쿠키 보안 속성이 명시되지 않으면 기동 차단")
    void validate_prodSessionCookiePropertyMissing_throws(String missingKey) throws IOException {
        StandardEnvironment env = prodEnvironmentWithoutProperty(missingKey);

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: " + missingKey);
    }

    @Test
    @DisplayName("운영 프로파일에서 모든 운영 필수 키가 채워지면 예외 없음")
    void validate_prodAllKeysSet_noException() {
        EnvironmentValidator validator = new EnvironmentValidator(prodEnvWithAllRequired());
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("active profile이 없어도 default profile이 prod이면 운영 검증 수행")
    void validate_defaultProdProfile_dangerousToggleThrows() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setActiveProfiles();
        env.setDefaultProfiles("prod");
        env.setProperty("app.sso.allow-direct-eno", "true");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: app.sso.allow-direct-eno");
    }

    @Test
    @DisplayName("대문자 active PROD도 운영 프로파일로 판정")
    void validate_uppercaseActiveProd_dangerousToggleThrows() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setActiveProfiles("PROD");
        env.setProperty("app.sso.allow-direct-eno", "true");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: app.sso.allow-direct-eno");
    }

    @Test
    @DisplayName("active가 없을 때 대문자 default PROD도 운영 프로파일로 판정")
    void validate_uppercaseDefaultProd_dangerousToggleThrows() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setActiveProfiles();
        env.setDefaultProfiles("PROD");
        env.setProperty("app.sso.allow-direct-eno", "true");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: app.sso.allow-direct-eno");
    }

    @Test
    @DisplayName("active non-prod가 있으면 default prod보다 active를 우선")
    void validate_activeNonProdWithDefaultProd_skipsProdValidation() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setActiveProfiles("local-ext");
        env.setDefaultProfiles("prod");
        env.setProperty("app.sso.allow-direct-eno", "true");

        assertThatCode(() -> new EnvironmentValidator(env).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("운영 프로파일에서 상위 우선순위 API docs=true override는 기동 차단")
    void validate_prodApiDocsEnabledOverride_throws() throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithFileOverrides(Map.of("springdoc.api-docs.enabled", "true"));

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: springdoc.api-docs.enabled");
    }

    @Test
    @DisplayName("운영 프로파일에서 상위 우선순위 Swagger UI=true override는 기동 차단")
    void validate_prodSwaggerUiEnabledOverride_throws() throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithFileOverrides(Map.of("springdoc.swagger-ui.enabled", "true"));

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: springdoc.swagger-ui.enabled");
    }

    @Test
    @DisplayName("운영 프로파일에서 파일 기본값의 OpenAPI 비활성화는 정상 기동")
    void validate_prodOpenApiDisabledByProfileFile_noException() throws IOException {
        StandardEnvironment env = prodEnvironmentWithFileOverrides(Map.of());

        assertThatCode(() -> new EnvironmentValidator(env).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("운영 프로파일에서 SPRINGDOC_API_DOCS_ENABLED=true 환경변수는 기동 차단")
    void validate_prodApiDocsSystemEnvironmentOverride_throws() throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithSystemEnvironment(Map.of("SPRINGDOC_API_DOCS_ENABLED", "true"));

        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: springdoc.api-docs.enabled");
    }

    @Test
    @DisplayName("운영 프로파일에서 SPRINGDOC_SWAGGER_UI_ENABLED=true 환경변수는 기동 차단")
    void validate_prodSwaggerUiSystemEnvironmentOverride_throws() throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithSystemEnvironment(
                        Map.of("SPRINGDOC_SWAGGER_UI_ENABLED", "true"));

        assertThat(env.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");
        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: springdoc.swagger-ui.enabled");
    }

    @ParameterizedTest
    @ValueSource(strings = {"springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled"})
    @DisplayName("운영 프로파일에서 OpenAPI 비활성화 키가 누락되면 기동 차단")
    void validate_prodOpenApiPropertyMissing_throws(String missingKey) throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithoutProfileFile(
                        Map.of(otherOpenApiProperty(missingKey), "false"));

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: " + missingKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled"})
    @DisplayName("운영 프로파일에서 OpenAPI 비활성화 키가 빈값이면 기동 차단")
    void validate_prodOpenApiPropertyBlank_throws(String blankKey) throws IOException {
        StandardEnvironment env = prodEnvironmentWithFileOverrides(Map.of(blankKey, ""));

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: " + blankKey);
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
    @DisplayName("운영 프로파일에서 TOKEN_FINGERPRINT_SECRET 빈값이면 기동 차단")
    void validate_prodBlankTokenFingerprintSecret_throws() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("security.token-fingerprint-secret", " ");
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOKEN_FINGERPRINT_SECRET");
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
    @DisplayName("운영 프로파일에서 app.mfa.store=memory는 기동을 차단한다")
    void 운영에서_mfa_store가_memory면_기동을_차단한다() {
        MockEnvironment env = prodEnvWithAllRequired();
        env.setProperty("app.mfa.store", "memory");

        assertThatThrownBy(() -> new EnvironmentValidator(env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mfa.store");
    }

    @Test
    @DisplayName("비운영 프로파일(local-ext)에서는 운영 전용 키가 비어도 통과")
    void validate_nonProdProfile_skipsProdKeys() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local-ext");
        env.setProperty("spring.datasource.password", "pw");
        env.setProperty(
                "jwt.secret", "super-secret-key-at-least-256-bits-long-xxxxxxxxxxxxxxxxxxxxxxxx");
        env.setProperty(
                "security.token-fingerprint-secret",
                "token-fingerprint-secret-at-least-256-bits-long-xxxxxxxxxxxx");
        // gemini/eai/cors/sso 미설정
        EnvironmentValidator validator = new EnvironmentValidator(env);
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    /** prod 파일보다 우선하는 canonical override를 포함한 격리 Environment를 구성한다. */
    private StandardEnvironment prodEnvironmentWithFileOverrides(Map<String, Object> overrides)
            throws IOException {
        return prodEnvironment(overrides, Map.of(), true);
    }

    /** 실제 OS 환경변수와 같은 relaxed binding 입력을 포함한 격리 Environment를 구성한다. */
    private StandardEnvironment prodEnvironmentWithSystemEnvironment(
            Map<String, Object> systemEnvironment) throws IOException {
        return prodEnvironment(Map.of(), systemEnvironment, true);
    }

    /** prod 파일에서 springdoc 키가 누락된 배포 구성을 재현한다. */
    private StandardEnvironment prodEnvironmentWithoutProfileFile(Map<String, Object> overrides)
            throws IOException {
        return prodEnvironment(overrides, Map.of(), false);
    }

    /** 운영 프로파일 파일과 특정 보안 기본값이 모두 없는 배포 구성을 재현한다. */
    private StandardEnvironment prodEnvironmentWithoutProperty(String missingKey)
            throws IOException {
        StandardEnvironment env =
                prodEnvironmentWithoutProfileFile(
                        Map.of(
                                "springdoc.api-docs.enabled", "false",
                                "springdoc.swagger-ui.enabled", "false"));
        MapPropertySource overrides =
                (MapPropertySource) env.getPropertySources().get("test-overrides");
        overrides.getSource().remove(missingKey);
        return env;
    }

    private StandardEnvironment prodEnvironment(
            Map<String, Object> overrides,
            Map<String, Object> systemEnvironment,
            boolean includeProdProfile)
            throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.setActiveProfiles("prod");

        Map<String, Object> requiredProperties = new HashMap<>();
        requiredProperties.put("spring.datasource.password", "pw");
        requiredProperties.put(
                "jwt.secret", "super-secret-key-at-least-256-bits-long-xxxxxxxxxxxxxxxxxxxxxxxx");
        requiredProperties.put(
                "security.token-fingerprint-secret",
                "token-fingerprint-secret-at-least-256-bits-long-xxxxxxxxxxxx");
        requiredProperties.put("gemini.api.key", "gk-real-key");
        requiredProperties.put("eai.enabled", "false");
        requiredProperties.put("cors.allowed-origins", "https://it.kdb.co.kr");
        requiredProperties.put("app.sso.allow-direct-eno", "false");
        requiredProperties.put("sso.mock-enabled", "false");
        requiredProperties.put("app.auth.allow-bearer-header", "false");
        requiredProperties.put("app.cookie.secure", "true");
        requiredProperties.put("server.servlet.session.cookie.secure", "true");
        requiredProperties.put("server.servlet.session.cookie.http-only", "true");
        requiredProperties.put("server.servlet.session.cookie.same-site", "lax");
        requiredProperties.put("app.frontend-url", "https://it.kdb.co.kr");
        requiredProperties.put("app.approval.it-budget.preview.active-key-id", "prod-v2");
        requiredProperties.put(
                "app.approval.it-budget.preview.active-signing-key",
                "preview-signing-key-for-production-minimum-32-bytes");
        requiredProperties.putAll(overrides);

        if (includeProdProfile) {
            env.getPropertySources()
                    .addLast(
                            new ResourcePropertySource(
                                    "prod-profile",
                                    new ClassPathResource("application-prod.properties")));
        }
        env.getPropertySources()
                .addFirst(new MapPropertySource("test-overrides", requiredProperties));
        if (!systemEnvironment.isEmpty()) {
            env.getPropertySources()
                    .addFirst(
                            new SystemEnvironmentPropertySource(
                                    "test-system-environment", systemEnvironment));
        }
        return env;
    }

    private String otherOpenApiProperty(String property) {
        return "springdoc.api-docs.enabled".equals(property)
                ? "springdoc.swagger-ui.enabled"
                : "springdoc.api-docs.enabled";
    }

    private static Stream<Arguments> dangerousSystemEnvironmentProperties() {
        return Stream.of(
                Arguments.of("APP_SSO_ALLOW_DIRECT_ENO", "app.sso.allow-direct-eno"),
                Arguments.of("APP_DEV_USER_SWITCH_ENABLED", "app.dev.user-switch.enabled"),
                Arguments.of("SSO_MOCK_ENABLED", "sso.mock-enabled"),
                Arguments.of("APP_AUTH_ALLOW_BEARER_HEADER", "app.auth.allow-bearer-header"),
                Arguments.of("SPRINGDOC_API_DOCS_ENABLED", "springdoc.api-docs.enabled"),
                Arguments.of("SPRINGDOC_SWAGGER_UI_ENABLED", "springdoc.swagger-ui.enabled"));
    }

    private static Stream<Arguments> unsafeSessionCookieSystemEnvironmentProperties() {
        return Stream.of(
                Arguments.of(
                        "SERVER_SERVLET_SESSION_COOKIE_SECURE",
                        "server.servlet.session.cookie.secure"),
                Arguments.of(
                        "SERVER_SERVLET_SESSION_COOKIE_HTTP_ONLY",
                        "server.servlet.session.cookie.http-only"));
    }
}
