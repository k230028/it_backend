package com.kdb.it.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.system.EnvironmentValidator;
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

/** TokenFingerprint가 전용 시크릿 프로퍼티만 사용하도록 보장하는 설정 테스트입니다. */
class TokenFingerprintConfigurationTest {

    @Test
    @DisplayName("TokenFingerprint 빈은 jwt.secret이 아니라 전용 시크릿으로 지문을 계산한다")
    void contextUsesDedicatedFingerprintSecretInsteadOfJwtSecret() {
        String jwtSecret = "jwt-signing-secret-at-least-256-bits-xxxxxxxx";
        String fingerprintSecret = "token-fingerprint-secret-at-least-256-bits-xxxxxxxx";

        try (ConfigurableApplicationContext context =
                application()
                        .run(
                                arguments(
                                        Map.of(
                                                "spring.config.name",
                                                "token-fingerprint-test-empty",
                                                "spring.datasource.password",
                                                "test-db-password",
                                                "jwt.secret",
                                                jwtSecret,
                                                "security.token-fingerprint-secret",
                                                fingerprintSecret)))) {
            TokenFingerprint fingerprint = context.getBean(TokenFingerprint.class);

            assertThat(fingerprint.forRefreshToken("sample-token"))
                    .isEqualTo(
                            new TokenFingerprint(fingerprintSecret).forRefreshToken("sample-token"))
                    .isNotEqualTo(new TokenFingerprint(jwtSecret).forRefreshToken("sample-token"));
        }
    }

    @Test
    @DisplayName("전용 시크릿이 없으면 jwt.secret이 있어도 TokenFingerprint 빈 기동은 실패한다")
    void contextWithoutDedicatedFingerprintSecretFailsEvenWhenJwtSecretExists() {
        assertThatThrownBy(
                        () -> {
                            try (ConfigurableApplicationContext _ =
                                    application()
                                            .run(
                                                    arguments(
                                                            Map.of(
                                                                    "spring.config.name",
                                                                    "token-fingerprint-test-empty",
                                                                    "spring.datasource.password",
                                                                    "test-db-password",
                                                                    "jwt.secret",
                                                                    "jwt-signing-secret-at-least-256-bits-xxxxxxxx",
                                                                    "security.token-fingerprint-secret",
                                                                    "")))) {
                                // 기동 성공 자체가 전용 시크릿 분리 계약 위반이다.
                            }
                        })
                .hasStackTraceContaining("security.token-fingerprint-secret");
    }

    private SpringApplication application() {
        SpringApplication application =
                new SpringApplication(TokenFingerprintOnlyApplication.class);
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
        properties.putAll(overrides);
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({TokenFingerprint.class, EnvironmentValidator.class})
    static class TokenFingerprintOnlyApplication {}
}
