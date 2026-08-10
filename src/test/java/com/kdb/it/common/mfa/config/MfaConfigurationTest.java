package com.kdb.it.common.mfa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.mfa.provider.MfaProviderRegistry;
import java.time.Duration;
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

@DisplayName("MFA 프로파일 설정")
class MfaConfigurationTest {

    @Test
    @DisplayName("local-ext는 모의 공급자 설정과 기본 제한값을 사용한다")
    void localExt_usesMockProviderConfiguration() {
        try (ConfigurableApplicationContext context = start("local-ext", Map.of())) {
            MfaProperties properties = context.getBean(MfaProperties.class);

            assertThat(properties.mockEnabled()).isTrue();
            assertThat(properties.endpoint()).isBlank();
            assertDefaultLimits(properties);
            assertThat(context.getBeansOfType(MfaProviderRegistry.class)).hasSize(1);
        }
    }

    @Test
    @DisplayName("local-int는 개발 OnePass endpoint를 사용한다")
    void localInt_usesDevelopmentOnePassConfiguration() {
        assertOnePassProfile(
                "local-int", "https://dopsap.kdb.co.kr:20443/interfBiz/processRequest.do");
    }

    @Test
    @DisplayName("dev는 개발 OnePass endpoint를 사용한다")
    void dev_usesDevelopmentOnePassConfiguration() {
        assertOnePassProfile("dev", "https://dopsap.kdb.co.kr:20443/interfBiz/processRequest.do");
    }

    @Test
    @DisplayName("prod는 운영 OnePass endpoint를 사용한다")
    void prod_usesProductionOnePassConfiguration() {
        assertOnePassProfile("prod", "https://opsap.kdb.co.kr:20443/interfBiz/processRequest.do");
    }

    @Test
    @DisplayName("prod에서 모의 MFA를 켜면 기동을 차단한다")
    void prod_withMockEnabled_failsStartup() {
        assertThatThrownBy(() -> start("prod", Map.of("app.mfa.mock-enabled", "true")))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mfa.mock-enabled");
    }

    @Test
    @DisplayName("MFA 설정은 challenge 만료 시간을 보관한다")
    void properties_keepsChallengeTtl() {
        MfaProperties properties =
                new MfaProperties(
                        "",
                        "SIT01KDBBANK00000000",
                        "SVC12SIT01KDBBANK000",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(5),
                        true,
                        Duration.ofSeconds(90),
                        5);

        assertThat(properties.challengeTtl()).isEqualTo(Duration.ofSeconds(90));
        assertThat(properties.maxFailures()).isEqualTo(5);
    }

    private void assertOnePassProfile(String profile, String endpoint) {
        try (ConfigurableApplicationContext context = start(profile, Map.of())) {
            MfaProperties properties = context.getBean(MfaProperties.class);

            assertThat(properties.mockEnabled()).isFalse();
            assertThat(properties.endpoint()).isEqualTo(endpoint);
            assertThat(properties.siteId()).isEqualTo("SIT01KDBBANK00000000");
            assertThat(properties.svcId()).isEqualTo("SVC12SIT01KDBBANK000");
            assertDefaultLimits(properties);
            assertThat(context.getBeansOfType(MfaProviderRegistry.class)).hasSize(1);
        }
    }

    private void assertDefaultLimits(MfaProperties properties) {
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.challengeTtl()).isEqualTo(Duration.ofSeconds(90));
        assertThat(properties.maxFailures()).isEqualTo(5);
    }

    private ConfigurableApplicationContext start(String profile, Map<String, String> overrides) {
        SpringApplication application = new SpringApplication(MfaConfigurationTestApplication.class);
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

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.profiles.active", profile);
        properties.putAll(overrides);
        return application.run(arguments(properties));
    }

    private String[] arguments(Map<String, String> properties) {
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(MfaConfig.class)
    static class MfaConfigurationTestApplication {}
}
