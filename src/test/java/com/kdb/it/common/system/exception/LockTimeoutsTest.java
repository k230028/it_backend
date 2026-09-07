package com.kdb.it.common.system.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

/** Oracle 잠금 대기 초과를 원인 사슬에서 찾아내는지 검증합니다. */
class LockTimeoutsTest {

    @Test
    @DisplayName("ORA-30006은 잠금 타임아웃이다")
    void oracle30006IsLockTimeout() {
        assertThat(LockTimeouts.isLockTimeout(new SQLException("lock wait", "61000", 30006)))
                .isTrue();
    }

    @Test
    @DisplayName("ORA-00054는 잠금 타임아웃이다")
    void oracle54IsLockTimeout() {
        assertThat(LockTimeouts.isLockTimeout(new SQLException("resource busy", "61000", 54)))
                .isTrue();
    }

    @Test
    @DisplayName("중첩된 원인도 찾아낸다")
    void nestedCauseIsFound() {
        assertThat(
                        LockTimeouts.isLockTimeout(
                                new RuntimeException(new CannotAcquireLockException("locked"))))
                .isTrue();
    }

    @Test
    @DisplayName("null 입력은 잠금 타임아웃이 아니다")
    void nullIsNotLockTimeout() {
        assertThat(LockTimeouts.isLockTimeout(null)).isFalse();
    }

    @Test
    @DisplayName("무관한 예외는 잠금 타임아웃이 아니다")
    void unrelatedFailureIsNotLockTimeout() {
        assertThat(LockTimeouts.isLockTimeout(new IllegalStateException("nope"))).isFalse();
    }
}
