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
    @DisplayName("active 없이 default profile이 prod여도 운영 위험 설정은 기동 차단")
    void defaultProd_dangerousToggle_failsDuringStartup() {
        assertStartupFails(
                Map.of(
                        "spring.profiles.default", "prod",
                        "app.sso.allow-direct-eno", "true"),
                "app.sso.allow-direct-eno");
    }

    private void assertStartupFails(Map<String, String> overrides, String propertyKey) {
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

        assertThatThrownBy(
                        () -> {
                            try (ConfigurableApplicationContext ignored =
                                    application.run(arguments(overrides))) {
                                // 기동 성공 자체가 보안 경계 실패다.
                            }
                        })
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("운영 보안 위반: " + propertyKey);
    }

    private String[] arguments(Map<String, String> overrides) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.password", "test-db-password");
        properties.put("jwt.secret", "test-secret-key-for-junit-test-minimum-256-bits-length-ok");
        properties.put("gemini.api.key", "test-gemini-key");
        properties.put("eai.enabled", "false");
        properties.put("cors.allowed-origins", "https://it.kdb.co.kr");
        properties.put("app.sso.allow-direct-eno", "false");
        properties.put("app.dev.user-switch.enabled", "false");
        properties.put("sso.mock-enabled", "false");
        properties.put("app.auth.allow-bearer-header", "false");
        properties.put("app.cookie.secure", "true");
        properties.put("app.frontend-url", "https://it.kdb.co.kr");
        properties.put("springdoc.api-docs.enabled", "false");
        properties.put("springdoc.swagger-ui.enabled", "false");
        properties.putAll(overrides);
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(EnvironmentValidator.class)
    static class ValidatorOnlyApplication {}
}
