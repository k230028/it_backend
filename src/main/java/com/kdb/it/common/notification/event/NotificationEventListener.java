package com.kdb.it.common.notification.event;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
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
 *   <li>{@link ApprovalCompletedEvent} — 기존 결재 완료/반려 이벤트
 *       (신청자에게 결재결과 알림으로 변환)</li>
 * </ul>
 *
 * <p>두 핸들러 모두 {@link TransactionPhase#AFTER_COMMIT}이며,
 * 발송 실패는 원본 트랜잭션에 영향을 주지 않도록 catch 후 warn 로깅한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final ApplicationRepository applicationRepository;

    /** 일반 알림 이벤트 처리. 발행자 트랜잭션 커밋 이후 비동기 발송. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationEvent(NotificationEvent event) {
        log.info("[알림 진단] onNotificationEvent 진입: recipient={}, type={}, ttl={}",
            event.recipientEno(), event.infTpC(), event.infTtl());
        try {
            notificationService.send(event);
            log.info("[알림 진단] onNotificationEvent 완료: recipient={}, type={}",
                event.recipientEno(), event.infTpC());
        } catch (Exception ex) {
            log.warn("[알림 진단] onNotificationEvent 예외: recipient={}, type={}, cause={}",
                event.recipientEno(), event.infTpC(), ex.toString(), ex);
        }
    }

    /**
     * 결재 완료/반려 이벤트 → 신청자에게 결재결과 알림.
     *
     * <p>{@link ApplicationRepository}에서 {@code Capplm}을 조회해 신청자 사번과
     * 신청서명을 함께 알림 본문에 포함한다.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        try {
            Capplm capplm = applicationRepository.findById(event.apfMngNo()).orElse(null);
            if (capplm == null) {
                log.warn("Approval result notification skipped: capplm not found. apfMngNo={}", event.apfMngNo());
                return;
            }
            String title = "결재 " + event.newStatus() + ": " + safe(capplm.getApfNm());
            String body  = "신청서가 " + event.newStatus() + " 처리되었습니다.";
            notificationService.send(
                NotificationEvent.builder()
                    .recipientEno(capplm.getRqsEno())
                    .infTpC(NotificationEvent.TYPE_APPROVAL_RESULT)
                    .infTtl(abbreviate(title, 100))
                    .infCone(abbreviate(body, 300))
                    // 결재 결과 알림도 결재 대기 목록 화면으로 고정 (사용자 정책).
                    // 상대 path 사용 — Nuxt navigateTo가 내부 라우팅으로 처리하며 운영 호스트와 무관.
                    .infLnkUrl("/approval/list?tab=pending")
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Approval result notification failed: apfMngNo={}, status={}",
                event.apfMngNo(), event.newStatus(), ex);
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
