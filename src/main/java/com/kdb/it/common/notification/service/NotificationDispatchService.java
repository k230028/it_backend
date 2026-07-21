package com.kdb.it.common.notification.service;

import com.kdb.it.common.notification.dispatcher.NotificationDispatchResult;
import com.kdb.it.common.notification.dispatcher.NotificationDispatcher;
import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import io.micrometer.core.instrument.MeterRegistry;
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
     * @param infmMsgNo 발송할 알림 메시지 번호
     * @throws java.util.NoSuchElementException 알림 행이 존재하지 않는 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(String infmMsgNo) {
        Cinfmm row = repository.findById(infmMsgNo).orElseThrow();
        if (Cinfmm.DISPATCH_SENT.equals(row.getInfmSdStsC())) {
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
