package com.kdb.it.common.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

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
}
