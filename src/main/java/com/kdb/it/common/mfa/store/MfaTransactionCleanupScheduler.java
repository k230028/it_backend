package com.kdb.it.common.mfa.store;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료 후 10분이 지난 추가인증 거래·로그인 대기 행을 물리 삭제한다.
 *
 * <p>정확성은 이 배치와 무관하다. 모든 조회·UPDATE 조건에 {@code END_DTM > now}가 들어가므로 만료 행은
 * 어떤 경로로도 사용되지 않는다. 유예 10분은 만료 직후 요청이 사유(만료/미존재)를 정확히 구분할 수 있게
 * 하는 창이며, 이 정리는 순수한 용량 관리다. 다중 인스턴스에서 여러 인스턴스가 동시에 실행해도 DELETE는
 * 멱등이라 잠금이 필요 없다.
 */
@ConditionalOnProperty(prefix = "app.mfa", name = "store", havingValue = "jpa", matchIfMissing = true)
@Component
@RequiredArgsConstructor
@Slf4j
public class MfaTransactionCleanupScheduler {

    private static final long GRACE_MINUTES = 10;

    private final MfaTransactionJpaRepository transactionRepository;
    private final LoginPendingTransactionJpaRepository pendingRepository;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.mfa.cleanup.fixed-delay-ms:300000}")
    @Transactional
    public void cleanup() {
        LocalDateTime cutoff =
                LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()).minusMinutes(GRACE_MINUTES);
        int deletedTransactions = transactionRepository.deleteExpiredBefore(cutoff);
        int deletedPending = pendingRepository.deleteExpiredBefore(cutoff);
        if (deletedTransactions > 0 || deletedPending > 0) {
            log.info(
                    "[MFA 정리] 만료 후 {}분 경과 행 삭제: 거래={}, 로그인대기={}",
                    GRACE_MINUTES,
                    deletedTransactions,
                    deletedPending);
        }
    }
}
