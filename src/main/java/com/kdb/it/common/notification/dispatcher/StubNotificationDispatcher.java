package com.kdb.it.common.notification.dispatcher;

import com.kdb.it.common.notification.entity.Cinfmm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 인앱(INAPP) 전용 Stub 디스패처
 *
 * <p>
 * 본 페이즈에서는 EAI 외부 시스템 실연동이 없으므로,
 * 알림 적재 자체로 INAPP 발송이 완료된 것으로 간주하고
 * {@code EAI_SD_TP_C='INAPP'} + {@code EAI_SD_DTM=now} + 페이로드만 기록한다.
 * </p>
 *
 * <p>
 * 향후 외부 채널 어댑터(EMAIL/SMS/TALK)가 추가되면
 * 채널별로 구현체를 분리하고 라우터를 도입한다 (Phase 2).
 * </p>
 */
@Slf4j
@Component
public class StubNotificationDispatcher implements NotificationDispatcher {

    /** 인앱 채널 코드 — Ccodem cId=CEAI_SD_TP / CDVA=INAPP 시드와 일치 */
    private static final String CHANNEL_INAPP = "INAPP";

    @Override
    public void dispatch(Cinfmm notification, String eaiPayload) {
        try {
            notification.markDispatched(CHANNEL_INAPP, eaiPayload);
        } catch (RuntimeException ex) {
            log.warn("Notification dispatch metadata update failed: infMngNo={}", notification.getInfMngNo(), ex);
        }
    }
}
