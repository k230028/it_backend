package com.kdb.it.common.notification.service;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** 알림을 발송 대기 상태로 독립 적재하는 서비스입니다. */
@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private final CinfmmRepository repository;

    /**
     * 알림 이벤트를 PENDING 행으로 즉시 커밋합니다.
     *
     * @param event 적재할 알림 이벤트
     * @return 생성된 알림 번호, 수신자가 비어 있으면 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "notificationUnreadCount", key = "#event.recipientEno()",
            condition = "#event.recipientEno() != null")
    public String enqueue(NotificationEvent event) {
        if (event.recipientEno() == null || event.recipientEno().isBlank()) {
            return null;
        }
        String id = String.format("INF-%d-%08d", LocalDate.now().getYear(), repository.getNextVal());
        Cinfmm row = Cinfmm.builder()
                .infmMsgNo(id)
                .infmSvcTc(event.infmSvcTc())
                .ttl(clamp(event.ttl(), 100))
                .infmMsgCone(clamp(event.infmMsgCone(), 4000))
                .infmRcdUrl(clamp(event.infmRcdUrl(), 300))
                .rmsEno(event.recipientEno())
                .inqYn("N")
                .sdTc(event.sdTc())
                .sdDocCone(event.sdPayload())
                .infmSdStsC(Cinfmm.DISPATCH_PENDING)
                .reTryNot(0)
                .build();
        repository.saveAndFlush(row);
        return id;
    }

    private static String clamp(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
