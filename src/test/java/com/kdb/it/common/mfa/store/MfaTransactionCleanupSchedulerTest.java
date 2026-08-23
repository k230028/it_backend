package com.kdb.it.common.mfa.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 정리 배치의 컷오프 계산과 로그 분기를 실 DB 없이 검증한다.
 *
 * <p>실 Oracle에서 어떤 행이 지워지는지는 {@code MfaTransactionCleanupSchedulerIT}가 검증한다. 여기서는 유예 10분이 컷오프에
 * 반영되는지와 삭제 건수에 따른 분기만 본다.
 */
@ExtendWith(MockitoExtension.class)
class MfaTransactionCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-23T02:30:00Z");

    @Mock private MfaTransactionJpaRepository transactionRepository;
    @Mock private LoginPendingTransactionJpaRepository pendingRepository;

    private MfaTransactionCleanupScheduler scheduler(Clock clock) {
        return new MfaTransactionCleanupScheduler(transactionRepository, pendingRepository, clock);
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneId.systemDefault());
    }

    @Test
    @DisplayName("두 저장소에 현재시각 -10분 컷오프를 같은 값으로 넘긴다")
    void cleanup_passesGraceCutoffToBothRepositories() {
        given(transactionRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(0);
        given(pendingRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(0);

        scheduler(fixedClock()).cleanup();

        ArgumentCaptor<LocalDateTime> txCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> pendingCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionRepository).deleteExpiredBefore(txCutoff.capture());
        verify(pendingRepository).deleteExpiredBefore(pendingCutoff.capture());

        LocalDateTime expected =
                LocalDateTime.ofInstant(NOW, ZoneId.systemDefault()).minusMinutes(10);
        assertThat(txCutoff.getValue()).isEqualTo(expected);
        assertThat(pendingCutoff.getValue()).isEqualTo(expected);
    }

    @Test
    @DisplayName("거래만 지워져도 요약 로그 분기를 탄다")
    void cleanup_logsWhenOnlyTransactionsDeleted() {
        given(transactionRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(2);
        given(pendingRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(0);

        scheduler(fixedClock()).cleanup();

        verify(transactionRepository).deleteExpiredBefore(org.mockito.ArgumentMatchers.any());
        verify(pendingRepository).deleteExpiredBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("로그인 대기만 지워져도 요약 로그 분기를 탄다")
    void cleanup_logsWhenOnlyPendingDeleted() {
        given(transactionRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(0);
        given(pendingRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(5);

        scheduler(fixedClock()).cleanup();

        verify(pendingRepository).deleteExpiredBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("지운 행이 없으면 로그 분기를 타지 않고 조용히 끝난다")
    void cleanup_silentWhenNothingDeleted() {
        given(transactionRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(0);
        given(pendingRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(0);

        scheduler(fixedClock()).cleanup();

        verify(transactionRepository).deleteExpiredBefore(org.mockito.ArgumentMatchers.any());
        verify(pendingRepository).deleteExpiredBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("시계가 흐르면 컷오프도 함께 이동한다")
    void cleanup_followsClock() {
        given(transactionRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(0);
        given(pendingRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any()))
                .willReturn(0);
        Instant later = NOW.plusSeconds(3600);

        scheduler(Clock.fixed(later, ZoneId.systemDefault())).cleanup();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionRepository).deleteExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue())
                .isEqualTo(LocalDateTime.ofInstant(later, ZoneId.systemDefault()).minusMinutes(10));
    }
}
