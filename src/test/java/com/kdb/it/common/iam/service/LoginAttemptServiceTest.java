package com.kdb.it.common.iam.service;

import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * LoginAttemptService 단위 테스트 — SEC-03
 *
 * <p>CLOGNH 기반 Brute-force 감지: 10분 내 로그인 실패 5회 초과 시 계정 잠금 검증</p>
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    @Test
    @DisplayName("10분 내 실패 4회 — 잠금 없음, 예외 없음")
    void checkLocked_under5Failures_noException() {
        given(loginHistoryRepository.countByEnoAndItPtlLgnTcAndLgnDtmAfter(
                eq("E001"), eq("2"), any(LocalDateTime.class)))
                .willReturn(4L);

        assertThatCode(() -> loginAttemptService.checkLocked("E001"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("10분 내 실패 5회 — CustomGeneralException 발생 (423 계정 잠금)")
    void checkLocked_exactly5Failures_throwsException() {
        given(loginHistoryRepository.countByEnoAndItPtlLgnTcAndLgnDtmAfter(
                eq("E001"), eq("2"), any(LocalDateTime.class)))
                .willReturn(5L);

        assertThatThrownBy(() -> loginAttemptService.checkLocked("E001"))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("계정 잠금");
    }

    @Test
    @DisplayName("10분 내 실패 6회 — CustomGeneralException 발생")
    void checkLocked_over5Failures_throwsException() {
        given(loginHistoryRepository.countByEnoAndItPtlLgnTcAndLgnDtmAfter(
                eq("E001"), eq("2"), any(LocalDateTime.class)))
                .willReturn(6L);

        assertThatThrownBy(() -> loginAttemptService.checkLocked("E001"))
                .isInstanceOf(CustomGeneralException.class);
    }

    @Test
    @DisplayName("최초 로그인 시도(이력 없음) — 예외 없음")
    void checkLocked_noHistory_noException() {
        given(loginHistoryRepository.countByEnoAndItPtlLgnTcAndLgnDtmAfter(
                eq("NEWUSER"), eq("2"), any(LocalDateTime.class)))
                .willReturn(0L);

        assertThatCode(() -> loginAttemptService.checkLocked("NEWUSER"))
                .doesNotThrowAnyException();
    }
}
