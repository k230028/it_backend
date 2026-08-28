package com.kdb.it.common.notification.scheduler;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import com.kdb.it.common.notification.service.NotificationDispatchService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실패했거나 정체된 PENDING 알림을 제한된 배치로 재시도합니다. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "notification.retry.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationRetryScheduler {

    private final CinfmmRepository repository;
    private final NotificationDispatchService dispatchService;

    @Value("${notification.retry.max-attempts:5}")
    private int maxAttempts;

    @Value("${notification.retry.batch-size:50}")
    private int batchSize;

    /** 실패 알림과 60초 이상 정체된 PENDING 알림을 한 배치 재처리합니다. */
    @Scheduled(fixedDelayString = "${notification.retry.fixed-delay-ms:60000}")
    public void retry() {
        List<String> ids =
                repository.findRetryableIds(
                        List.of(Cinfmm.DISPATCH_PENDING, Cinfmm.DISPATCH_FAILED),
                        maxAttempts,
                        LocalDateTime.now().minusSeconds(60),
                        PageRequest.of(0, batchSize));
        for (String id : ids) {
            try {
                dispatchService.dispatch(id);
            } catch (RuntimeException ex) {
                log.warn("알림 재시도 처리 실패: infmMsgNo={}", id, ex);
            }
        }
    }
}
