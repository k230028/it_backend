package com.kdb.it.common.notification.event;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.event.ApprovalRecalledEvent;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 이벤트 리스너.
 *
 * <p>두 종류의 이벤트를 구독한다:</p>
 * <ul>
 *   <li>{@link NotificationEvent} — 명시적 발송 이벤트 (결재요청·멘션 등 도메인 호출자가 발행)</li>
 *   <li>{@link ApprovalCompletedEvent} — 결재 완료/반려 이벤트 (신청자에게 결재결과 알림으로 변환)</li>
 *   <li>{@link ApprovalRecalledEvent} — 결재회수 이벤트 (신청자 및 기승인 중간결재자에게 결재회수 알림)</li>
 * </ul>
 *
 * <p>핸들러는 모두 {@link TransactionPhase#AFTER_COMMIT}이며,
 * 발송 실패는 원본 트랜잭션에 영향을 주지 않도록 catch 후 warn 로깅한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final ApplicationRepository applicationRepository;

    /**
     * 일반 알림 이벤트 처리. 발행자 트랜잭션 커밋 이후 비동기 발송.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationEvent(NotificationEvent event) {
        try {
            notificationService.send(event);
        } catch (Exception ex) {
            log.warn("Notification send failed: recipient={}, svcTc={}",
                event.recipientEno(), event.infmSvcTc(), ex);
        }
    }

    /**
     * 결재 완료/반려 이벤트 → 신청자에게 결재결과 알림.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        try {
            Capplm capplm = applicationRepository.findById(event.apfMngNo()).orElse(null);
            if (capplm == null) {
                log.warn("Approval result notification skipped: capplm not found. apfMngNo={}", event.apfMngNo());
                return;
            }
            String title = "결재 " + event.newStatus() + ": " + safe(capplm.getDcdReqTtl());
            String body  = "신청서가 " + event.newStatus() + " 처리되었습니다.";
            notificationService.send(
                NotificationEvent.builder()
                    .recipientEno(capplm.getDcdReqUsid())
                    .infmSvcTc(NotificationEvent.TYPE_APPROVAL_RESULT)
                    .ttl(abbreviate(title, 100))
                    .infmMsgCone(abbreviate(body, 4000))
                    // 결재 결과 알림도 결재 대기 목록 화면으로 고정 (사용자 정책).
                    .infmRcdUrl("/approval/list?tab=pending")
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Approval result notification failed: apfMngNo={}, status={}",
                event.apfMngNo(), event.newStatus(), ex);
        }
    }

    /**
     * 결재회수 이벤트 → 신청자 및 기승인 중간결재자에게 결재회수 알림 발송.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalRecalled(ApprovalRecalledEvent event) {
        try {
            Capplm capplm = applicationRepository.findById(event.apfMngNo()).orElse(null);
            if (capplm == null) {
                log.warn("Approval recall notification skipped: capplm not found. apfMngNo={}", event.apfMngNo());
                return;
            }
            String apfNm   = safe(capplm.getDcdReqTtl());
            String title   = abbreviate("결재회수: " + apfNm, 100);
            String linkUrl = "/approval/list?tab=pending";

            // 신청자 알림 (회수자가 신청자 본인이 아닌 경우만)
            if (capplm.getDcdReqUsid() != null && !capplm.getDcdReqUsid().equals(event.recallerEno())) {
                notificationService.send(
                    NotificationEvent.builder()
                        .recipientEno(capplm.getDcdReqUsid())
                        .infmSvcTc(NotificationEvent.TYPE_APPROVAL_RECALLED)
                        .ttl(title)
                        .infmMsgCone(abbreviate("신청서가 회수되었습니다: " + apfNm, 4000))
                        .infmRcdUrl(linkUrl)
                        .build()
                );
            }

            // 기승인 중간결재자 알림
            if (event.approvedMiddleApproverEnos() != null) {
                for (String eno : event.approvedMiddleApproverEnos()) {
                    if (eno == null || eno.isBlank()) continue;
                    notificationService.send(
                        NotificationEvent.builder()
                            .recipientEno(eno)
                            .infmSvcTc(NotificationEvent.TYPE_APPROVAL_RECALLED)
                            .ttl(title)
                            .infmMsgCone(abbreviate("귀하가 결재한 신청서가 회수되었습니다: " + apfNm, 4000))
                            .infmRcdUrl(linkUrl)
                            .build()
                    );
                }
            }
        } catch (Exception ex) {
            log.warn("Approval recall notification failed: apfMngNo={}, recaller={}",
                event.apfMngNo(), event.recallerEno(), ex);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
