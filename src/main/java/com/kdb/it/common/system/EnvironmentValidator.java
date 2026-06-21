package com.kdb.it.common.system;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 구동 시 필수 비밀값 존재 여부를 검증하는 컴포넌트 — SEC-01
 *
 * <p>{@link PostConstruct}로 스프링 컨텍스트 초기화 직후 실행되며,
 * 운영에 필요한 비밀값(DB 비밀번호, JWT 시크릿)이 빈값이면 즉시 기동을 중단합니다.
 * 단, {@code application.properties}에 기본값이 남아 있으면 환경변수 미설정도 통과하므로
 * 운영 프로파일에서는 기본값 제거 또는 별도 검증이 필요합니다.</p>
 *
 * <p>검증 대상:</p>
 * <ul>
 *   <li>{@code spring.datasource.password} → 환경변수 {@code DB_PASSWORD}</li>
 *   <li>{@code jwt.secret} → 환경변수 {@code JWT_SECRET}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EnvironmentValidator {

    private final Environment environment;

    /**
     * 필수 비밀값 전체 검증.
     *
     * <p>프로퍼티 해석 결과가 빈값 또는 null이면 {@link IllegalStateException}을 던져 구동을 차단합니다.
     * 환경변수명을 메시지에 포함해 운영자가 즉시 원인을 파악할 수 있도록 합니다.</p>
     *
     * @throws IllegalStateException 필수 환경변수가 미설정(null 또는 공백)인 경우
     */
    @PostConstruct
    public void validate() {
        checkRequired("spring.datasource.password", "DB_PASSWORD");
        checkRequired("jwt.secret", "JWT_SECRET");
    }

    private void checkRequired(String propertyKey, String envVarName) {
        String value = environment.getProperty(propertyKey);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "필수 환경변수 미설정: " + envVarName + " — 운영 환경에서는 빈값을 허용하지 않습니다.");
        }
    }
}
