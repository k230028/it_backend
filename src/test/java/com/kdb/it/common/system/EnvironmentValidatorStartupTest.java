package com.kdb.it.common.system;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;

/** 실제 SpringApplication 기동에서 운영 환경 검증기의 초기화 시점을 검증한다. */
class EnvironmentValidatorStartupTest {

    @Test
    @DisplayName("전역 lazy 초기화가 켜져도 운영 위험 설정은 컨텍스트 기동 중 차단")
    void lazyProd_dangerousToggle_failsDuringStartup() {
        assertStartupFails(
                Map.of(
                        "spring.profiles.active", "prod",
                        "spring.main.lazy-initialization", "true",
                        "app.sso.allow-direct-eno", "on"),
                "app.sso.allow-direct-eno");
    }

    @Test
    @DisplayName("전역 lazy 초기화가 켜져도 운영 세션 쿠키 Secure=false는 기동 중 차단")
    void lazyProd_insecureSessionCookie_failsDuringStartup() {
        assertStartupFails(
                Map.of(
                        "spring.profiles.active", "prod",
                        "spring.main.lazy-initialization", "true",
                        "server.servlet.session.cookie.secure", "false"),
                "server.servlet.session.cookie.secure");
    }

    @Test
    @DisplayName("active 없이 default profile이 prod여도 운영 위험 설정은 기동 차단")
    void defaultProd_dangerousToggle_failsDuringStartup() {
        assertStartupFails(
                Map.of(
                        "spring.profiles.default", "prod",
                        "app.sso.allow-direct-eno", "true"),
                "app.sso.allow-direct-eno");
    }

    @Test
    @DisplayName("대문자 active PROD도 실제 기동 중 운영 위험 설정을 차단")
    void uppercaseActiveProd_dangerousToggle_failsDuringStartup() {
        assertStartupFails(
                Map.of(
                        "spring.profiles.active", "PROD",
                        "app.sso.allow-direct-eno", "true"),
                "app.sso.allow-direct-eno");
    }

    @Test
    @DisplayName("active가 없으면 대문자 default PROD도 실제 기동 중 운영 위험 설정을 차단")
    void uppercaseDefaultProd_dangerousToggle_failsDuringStartup() {
        assertStartupFails(
                Map.of(
                        "spring.profiles.default", "PROD",
                        "app.sso.allow-direct-eno", "true"),
                "app.sso.allow-direct-eno");
    }

    @Test
    @DisplayName("운영 프로파일에서 TOKEN_FINGERPRINT_SECRET 누락은 실제 기동 중 차단")
    void prod_missingTokenFingerprintSecret_failsDuringStartup() {
        assertStartupFailsWithEnvVar(
                Map.of(
                        "spring.profiles.active", "prod",
                        "security.token-fingerprint-secret", ""),
                "TOKEN_FINGERPRINT_SECRET");
    }

    @Test
    @DisplayName("active non-prod가 있으면 default prod보다 active를 우선해 정상 기동")
    void activeNonProdWithDefaultProd_startsSuccessfully() {
        assertStartupSucceeds(
                Map.of(
                        "spring.profiles.active", "local-ext",
                        "spring.profiles.default", "prod",
                        "app.sso.allow-direct-eno", "true"));
    }

    private void assertStartupFails(Map<String, String> overrides, String propertyKey) {
        SpringApplication application = application();

        assertThatThrownBy(
                        () -> {
                            try (ConfigurableApplicationContext _ =
                                    application.run(arguments(overrides))) {
                                // 기동 성공 자체가 보안 경계 실패다.
                            }
                        })
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: " + propertyKey);
    }

    private void assertStartupFailsWithEnvVar(Map<String, String> overrides, String envVarName) {
        SpringApplication application = application();

        assertThatThrownBy(
                        () -> {
                            try (ConfigurableApplicationContext _ =
                                    application.run(arguments(overrides))) {
                                // 기동 성공 자체가 보안 경계 실패다.
                            }
                        })
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(envVarName);
    }

    private void assertStartupSucceeds(Map<String, String> overrides) {
        SpringApplication application = application();
        try (ConfigurableApplicationContext _ = application.run(arguments(overrides))) {
            // 컨텍스트 refresh 완료가 active profile 우선순위 계약의 관찰 결과다.
        }
    }

    private SpringApplication application() {
        SpringApplication application = new SpringApplication(ValidatorOnlyApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment
                .getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);
        return application;
    }

    private String[] arguments(Map<String, String> overrides) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.password", "test-db-password");
        properties.put("jwt.secret", "test-secret-key-for-junit-test-minimum-256-bits-length-ok");
        properties.put(
                "security.token-fingerprint-secret",
                "test-token-fingerprint-secret-for-junit-minimum-256-bits-ok");
        properties.put("gemini.api.key", "test-gemini-key");
        properties.put("eai.enabled", "false");
        properties.put("cors.allowed-origins", "https://it.kdb.co.kr");
        properties.put("app.sso.allow-direct-eno", "false");
        properties.put("app.dev.user-switch.enabled", "false");
        properties.put("sso.mock-enabled", "false");
        properties.put("app.auth.allow-bearer-header", "false");
        properties.put("app.cookie.secure", "true");
        properties.put("server.servlet.session.cookie.secure", "true");
        properties.put("server.servlet.session.cookie.http-only", "true");
        properties.put("server.servlet.session.cookie.same-site", "lax");
        properties.put("app.frontend-url", "https://it.kdb.co.kr");
        properties.put("springdoc.api-docs.enabled", "false");
        properties.put("springdoc.swagger-ui.enabled", "false");
        properties.put("app.approval.it-budget.preview.active-key-id", "prod-v2");
        properties.put(
                "app.approval.it-budget.preview.active-signing-key",
                "preview-signing-key-for-production-minimum-32-bytes");
        properties.putAll(overrides);
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(EnvironmentValidator.class)
    static class ValidatorOnlyApplication {}
}
