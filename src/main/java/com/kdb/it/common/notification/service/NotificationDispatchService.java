package com.kdb.it.common.notification.service;

import com.kdb.it.common.notification.dispatcher.NotificationDispatchResult;
import com.kdb.it.common.notification.dispatcher.NotificationDispatcher;
import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PENDING 알림의 채널 발송과 상태 전이를 독립 커밋하는 서비스입니다. */
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final CinfmmRepository repository;
    private final NotificationDispatcher dispatcher;
    private final MeterRegistry meterRegistry;

    @Value("${notification.retry.max-attempts:5}")
    private int maxAttempts;

    /**
     * 알림 한 건을 발송하고 성공 또는 실패 상태를 독립 트랜잭션으로 기록합니다.
     *
     * <p>대상 행을 먼저 잠근 뒤 상태를 검사하고 외부 발송까지 같은 잠금 구간에서 수행합니다. 다중 인스턴스에서 여러 재시도 스케줄러가 같은 알림을 동시에 집어도 나중
     * 트랜잭션은 앞선 트랜잭션의 커밋 결과를 읽으므로 같은 알림이 두 번 나가지 않습니다. 잠금 없이 조회하면 두 인스턴스가 모두 미발송 상태를 보고 통과합니다.
     *
     * @param infmMsgNo 발송할 알림 메시지 번호
     * @throws java.util.NoSuchElementException 알림 행이 존재하지 않는 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(String infmMsgNo) {
        Cinfmm row =
                Objects.requireNonNull(
                        repository.findByIdForUpdate(infmMsgNo).orElseThrow(), "잠금 조회한 알림 행");
        // 재시도 상한 검사는 스케줄러 조회 조건에만 있어 이벤트 경로와 동시 실행을 막지 못한다.
        // 잠금 구간에서 다시 확인해 상한을 넘긴 발송을 차단한다.
        if (!row.canRetry(maxAttempts)) {
            return;
        }

        NotificationDispatchResult result = dispatcher.dispatch(row, row.getSdDocCone());
        if (result.success()) {
            row.markDispatchSent(row.getItPtlSdTc(), row.getSdDocCone());
            return;
        }

        row.markDispatchFailed(result.errorMessage());
        String channel = safeChannel(row.getItPtlSdTc());
        meterRegistry.counter("notification.dispatch.failure", "channel", channel).increment();
        if (row.getReTryNot() >= maxAttempts) {
            meterRegistry
                    .counter("notification.dispatch.exhausted", "channel", channel)
                    .increment();
        }
    }

    private static String safeChannel(String channel) {
        return channel == null || channel.isBlank() ? "unknown" : channel;
    }
}
