package com.kdb.it.common.system;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 구동 시 필수 비밀값 존재 여부를 검증하는 컴포넌트 — SEC-01
 *
 * <p>{@link PostConstruct}로 스프링 컨텍스트 초기화 직후 실행되며, 운영에 필요한 비밀값(DB 비밀번호, JWT 시크릿)이 빈값이면 즉시 기동을 중단합니다.
 * 단, {@code application.properties}에 기본값이 남아 있으면 환경변수 미설정도 통과하므로 운영 프로파일에서는 기본값 제거 또는 별도 검증이 필요합니다.
 *
 * <p>검증 대상:
 *
 * <ul>
 *   <li>{@code spring.datasource.password} → 환경변수 {@code DB_PASSWORD}
 *   <li>{@code jwt.secret} → 환경변수 {@code JWT_SECRET}
 *   <li>(운영 프로파일 전용) {@code gemini.api.key}/{@code eai.url}(eai.enabled=true)/{@code
 *       cors.allowed-origins}(와일드카드 금지)/{@code app.sso.allow-direct-eno}(false 고정)/{@code
 *       app.frontend-url}/{@code springdoc.api-docs.enabled}(false 고정)/{@code
 *       springdoc.swagger-ui.enabled}(false 고정)
 * </ul>
 */
@Component
@Lazy(false)
@RequiredArgsConstructor
public class EnvironmentValidator {

    private final Environment environment;

    /**
     * 필수 비밀값 전체 검증.
     *
     * <p>프로퍼티 해석 결과가 빈값 또는 null이면 {@link IllegalStateException}을 던져 구동을 차단합니다. 환경변수명을 메시지에 포함해 운영자가
     * 즉시 원인을 파악할 수 있도록 합니다.
     *
     * @throws IllegalStateException 필수 환경변수가 미설정(null 또는 공백)인 경우
     */
    @PostConstruct
    public void validate() {
        // 전 프로파일 공통: 비밀값 빈값 차단
        checkRequired("spring.datasource.password", "DB_PASSWORD");
        checkRequired("jwt.secret", "JWT_SECRET");

        // 운영 프로파일에서만: 운영 필수 키 빈값/와일드카드/위험 토글 차단
        if (isProdProfile()) {
            validateProdKeys();
        }
    }

    /** 활성 또는 기본 프로파일에 {@code prod}가 적용되면 운영 검증을 수행합니다. */
    private boolean isProdProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        String[] selectedProfiles =
                activeProfiles != null && activeProfiles.length > 0
                        ? activeProfiles
                        : environment.getDefaultProfiles();
        if (selectedProfiles == null) {
            return false;
        }
        for (String profile : selectedProfiles) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 운영 전용 필수 키 검증 — 빈값/와일드카드/위험 토글을 기동 시 차단합니다.
     *
     * <ul>
     *   <li>{@code gemini.api.key} → {@code GEMINI_API_KEY} 비공백
     *   <li>{@code eai.enabled=true}이면 {@code eai.url} → {@code EAI_URL} 비공백
     *   <li>{@code cors.allowed-origins} 비공백 + 와일드카드({@code *}) 금지
     *   <li>{@code app.sso.allow-direct-eno} 운영 false 고정
     *   <li>{@code app.dev.user-switch.enabled} 운영 false 고정 (비밀번호 없이 임의 사번 로그인 경로 차단)
     *   <li>{@code app.frontend-url} 비공백
     *   <li>{@code springdoc.api-docs.enabled}/{@code springdoc.swagger-ui.enabled} 운영 false 고정
     * </ul>
     */
    private void validateProdKeys() {
        checkRequired("gemini.api.key", "GEMINI_API_KEY");

        boolean eaiEnabled = booleanProperty("eai.enabled", false);
        if (eaiEnabled) {
            checkRequired("eai.url", "EAI_URL");
        }

        String corsOrigins = environment.getProperty("cors.allowed-origins");
        if (corsOrigins == null || corsOrigins.isBlank()) {
            throw new IllegalStateException(
                    "운영 필수 키 미설정: cors.allowed-origins — 운영에서는 명시적 오리진이 필요합니다.");
        }
        if (corsOrigins.contains("*")) {
            throw new IllegalStateException(
                    "운영 보안 위반: cors.allowed-origins 와일드카드(*) 금지 — 실제 오리진을 나열하세요. (현재값="
                            + corsOrigins
                            + ")");
        }

        rejectTrue("app.sso.allow-direct-eno");
        rejectTrue("app.dev.user-switch.enabled");
        rejectTrue("sso.mock-enabled");
        rejectTrue("app.auth.allow-bearer-header");
        requireFalse("springdoc.api-docs.enabled");
        requireFalse("springdoc.swagger-ui.enabled");
        requireTrue("app.cookie.secure");

        String frontendUrl = environment.getProperty("app.frontend-url");
        if (frontendUrl == null || frontendUrl.isBlank()) {
            throw new IllegalStateException(
                    "운영 필수 키 미설정: app.frontend-url — SSO 완료 리다이렉트 대상이 필요합니다.");
        }
    }

    private void rejectTrue(String key) {
        if (booleanProperty(key, false)) {
            throw securityViolation(key);
        }
    }

    private void requireFalse(String key) {
        if (environment.getProperty(key) == null || booleanProperty(key, false)) {
            throw securityViolation(key);
        }
    }

    private void requireTrue(String key) {
        if (environment.getProperty(key) == null || !booleanProperty(key, false)) {
            throw securityViolation(key);
        }
    }

    private boolean booleanProperty(String key, boolean defaultValue) {
        String rawValue = environment.getProperty(key);
        if (rawValue == null) {
            return defaultValue;
        }
        if (rawValue.isBlank()) {
            throw securityViolation(key);
        }
        try {
            Boolean value = environment.getProperty(key, Boolean.class);
            if (value == null) {
                throw securityViolation(key);
            }
            return value;
        } catch (RuntimeException conversionFailure) {
            throw securityViolation(key);
        }
    }

    private IllegalStateException securityViolation(String key) {
        return new IllegalStateException("운영 보안 위반: " + key);
    }

    private void checkRequired(String propertyKey, String envVarName) {
        String value = environment.getProperty(propertyKey);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "필수 환경변수 미설정: " + envVarName + " — 운영 환경에서는 빈값을 허용하지 않습니다.");
        }
    }
}
