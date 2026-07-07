package com.kdb.it.common.notification.dispatcher;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.infra.eai.dto.EaiRequest;
import com.kdb.it.infra.eai.dto.EaiResult;
import com.kdb.it.infra.eai.dto.GwePayload;
import com.kdb.it.infra.eai.service.EaiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 알림 채널 라우터.
 *
 * <p>기본 인앱 채널은 기존처럼 알림 엔티티의 발송 메타만 기록한다. 외부 EAI 채널로 지정된
 * 알림은 그룹웨어(GWE) 전문으로 변환해 {@link EaiService}에 위임하며, 실패는 원 알림 저장
 * 흐름에 영향을 주지 않고 경고 로그로만 남긴다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcherRouter implements NotificationDispatcher {

    /** 인앱 채널 코드 — 공통코드 {@code C_ID='SD'} / CDVA='01'. */
    public static final String CHANNEL_INAPP = "01";

    /** EAI GWE 채널 코드 — 현재 테이블의 이메일 외부 발송 코드(CDVA='04')를 사용한다. */
    public static final String CHANNEL_EAI_GWE = "04";

    /** GWE 인터페이스ID — 운영팀 확정 전까지 기존 EAI 테스트 규격의 IT Portal GWE 값을 사용한다. */
    public static final String GWE_IF_ID = "IPPG00000001";

    private final EaiService eaiService;

    @Override
    public void dispatch(Cinfmm notification, String sdPayload) {
        String channel = StringUtils.hasText(notification.getSdTc()) ? notification.getSdTc() : CHANNEL_INAPP;
        if (CHANNEL_INAPP.equals(channel)) {
            markDispatched(notification, CHANNEL_INAPP, sdPayload);
            return;
        }
        if (CHANNEL_EAI_GWE.equals(channel)) {
            dispatchGwe(notification, sdPayload);
            return;
        }
        log.warn("지원하지 않는 알림 발송 채널입니다. 인앱으로 처리합니다: infmMsgNo={}, sdTc={}",
                notification.getInfmMsgNo(), channel);
        markDispatched(notification, CHANNEL_INAPP, sdPayload);
    }

    private void dispatchGwe(Cinfmm notification, String sdPayload) {
        try {
            EaiResult result = eaiService.sendEai(EaiRequest.gwe(GWE_IF_ID, GwePayload.builder()
                    .msgGubun("1")
                    .recvIds(notification.getRmsEno())
                    .subject(defaultText(notification.getTtl(), "IT Portal 알림"))
                    .contents(defaultText(notification.getInfmMsgCone(), "새 알림이 도착했습니다."))
                    .url(notification.getInfmRcdUrl())
                    .sendId("systemalert")
                    .sendName("IT Portal")
                    .build()));
            if (!result.success() && !result.skipped()) {
                log.warn("EAI 알림 발송 실패 — 원 알림 처리는 유지합니다. infmMsgNo={}, 사유={}",
                        notification.getInfmMsgNo(), result.errorMessage());
            }
        } catch (RuntimeException ex) {
            log.warn("EAI 알림 발송 예외 — 원 알림 처리는 유지합니다. infmMsgNo={}",
                    notification.getInfmMsgNo(), ex);
        } finally {
            markDispatched(notification, CHANNEL_EAI_GWE, sdPayload);
        }
    }

    private void markDispatched(Cinfmm notification, String channel, String sdPayload) {
        try {
            notification.markDispatched(channel, sdPayload);
        } catch (RuntimeException ex) {
            log.warn("알림 발송 메타 기록 실패: infmMsgNo={}", notification.getInfmMsgNo(), ex);
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
