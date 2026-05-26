package com.kdb.it.common.notification.dispatcher;

import com.kdb.it.common.notification.entity.Cinfmm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 인앱(IN-APP) 전용 Stub 디스패처
 *
 * <p>
 * 본 페이즈에서는 외부 발송 채널 실연동이 없으므로,
 * 알림 적재 자체로 인앱 발송이 완료된 것으로 간주하고
 * {@code SD_TC='01'} + {@code SD_DTM=now} + 페이로드만 기록한다.
 * </p>
 *
 * <p>향후 외부 채널 어댑터(알림톡/SMS/이메일)가 추가되면
 * 채널별로 구현체를 분리하고 라우터를 도입한다.</p>
 */
@Slf4j
@Component
public class StubNotificationDispatcher implements NotificationDispatcher {

    /**
     * 인앱 채널 코드 — 공통코드 {@code C_ID='SD'} / CDVA='01' (사내 인앱 알림) 시드와 일치.
     * SD_TC 컬럼은 VARCHAR2(2)이므로 2자리 코드 유지.
     */
    private static final String CHANNEL_INAPP = "01";

    @Override
    public void dispatch(Cinfmm notification, String sdPayload) {
        try {
            notification.markDispatched(CHANNEL_INAPP, sdPayload);
        } catch (RuntimeException ex) {
            log.warn("Notification dispatch metadata update failed: infmMsgNo={}", notification.getInfmMsgNo(), ex);
        }
    }
}
