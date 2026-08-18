package com.kdb.it.common.approval.notification;

import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.mail.ApprovalMailPayloadProvider;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.notification.dispatcher.NotificationDispatcherRouter;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.util.NotificationMessageFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 결재요청 알림 발행 전담 컴포넌트.
 *
 * <p>{@link com.kdb.it.common.approval.service.ApplicationService}의 신청서 등록(submit)과 중간 결재
 * 승인(approve) 두 경로에서 호출되어, 결재선의 다음 차례 결재자에게 결재요청 알림을 발행합니다. 알림 발행 자체는
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 리스너가 처리하므로 호출자의 트랜잭션을 차단하지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class ApprovalRequestNotifier {

    private static final Logger log = LoggerFactory.getLogger(ApprovalRequestNotifier.class);

    /** 결재(승인) 데이터 접근 리포지토리 (TPRMPP_CDECIM) */
    private final ApproverRepository approverRepository;

    /** 결재 완료/반려 시 도메인 이벤트 발행과 동일한 통로로 결재요청 알림 이벤트를 발행 (도메인 간 직접 의존 제거) */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 결재요청 메일 페이로드 제공자 — 신청자명·부서명 조회와 렌더링을 {@code REQUIRES_NEW} 독립 트랜잭션에서 수행한다. 실패해도 호출자( {@code
     * ApplicationService})의 submit/approve 트랜잭션을 rollback-only로 오염시키지 않는다(ERR-05).
     */
    private final ApprovalMailPayloadProvider approvalMailPayloadProvider;

    /**
     * 결재선에서 다음 차례인 결재자에게 결재요청 알림을 발행한다.
     *
     * <p>{@code IT_PTL_DCD_STS_C = '1'(미결재)}인 결재 항목 중 가장 작은 {@code DCD_SQN}의 결재자가 대상. 발견되지 않으면(=결재선
     * 모두 처리됨) 알림을 발행하지 않는다.
     *
     * @param capplm 신청서 마스터 — {@code apfMngNo}로 결재선을 조회하고 {@code dcdReqTtl}로 알림 제목·본문을 구성한다
     */
    public void notifyApprovalRequest(Capplm capplm) {
        List<Cdecim> approvers =
                approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(capplm.getApfMngNo());
        Cdecim next =
                approvers.stream()
                        .filter(a -> DecisionStatus.isPendingCode(a.getItPtlDcdStsC()))
                        .findFirst()
                        .orElse(null);
        if (next == null || next.getDcrEno() == null || next.getDcrEno().isBlank()) {
            log.info(
                    "[알림 진단] APPROVAL_REQUEST publishEvent 건너뜀: apfMngNo={}, approvers={}, nextNull={}, nextEnoBlank={}",
                    capplm.getApfMngNo(),
                    approvers.size(),
                    next == null,
                    next != null && (next.getDcrEno() == null || next.getDcrEno().isBlank()));
            return;
        }
        log.debug(
                "[알림 진단] APPROVAL_REQUEST publishEvent: apfMngNo={}, recipientEno={}, dcrSqnSno={}",
                capplm.getApfMngNo(),
                next.getDcrEno(),
                next.getDcrSqnSno());
        eventPublisher.publishEvent(
                NotificationEvent.builder()
                        .recipientEno(next.getDcrEno())
                        .itPtlInfmSvcTc(NotificationEvent.TYPE_APPROVAL_REQUEST)
                        .ttl(
                                NotificationMessageFormatter.abbreviate(
                                        "결재요청: " + safeText(capplm.getDcdReqTtl()), 100))
                        .infmMsgCone(
                                NotificationMessageFormatter.abbreviate(
                                        safeText(capplm.getDcdReqTtl()), 4000))
                        // 결재 알림은 결재 대기 목록 화면으로 고정 (사용자 정책).
                        // 상대 path 사용 — Nuxt navigateTo가 내부 라우팅으로 처리하며 운영 호스트와 무관.
                        .infmRcdUrl("/approval/list?tab=pending")
                        .itPtlSdTc(NotificationDispatcherRouter.CHANNEL_EAI_GWE)
                        .sdPayload(renderApprovalMail(capplm))
                        .build());
    }

    /**
     * 결재요청 메일 페이로드를 만듭니다.
     *
     * <p>실제 조회·렌더링은 {@link ApprovalMailPayloadProvider#render}가 수행합니다. 신청자명·부서명 조회는 그 안에서 {@code
     * REQUIRES_NEW} 독립 트랜잭션으로 실행되지만, 그 트랜잭션을 감싸는 예외 처리는 트랜잭션 경계 밖(제공자 자신은 더 이상
     * {@code @Transactional}이 아님)에 있어 조회 실패가 rollback-only 표시 후 {@code
     * UnexpectedRollbackException}으로 되돌아오는 문제 없이 그대로 제공자 안에서 삼켜집니다. 이 메서드의 try/catch는 그 계약이 어떤 이유로든
     * 깨졌을 때(예: 제공자 빈 자체의 예상 밖 예외)에 대비한 마지막 방어선이며, 정상 경로에서는 제공자가 이미 null을 반환하므로 이 catch가 실행될 일은
     * 없습니다. 렌더링이나 이름·부서 조회가 실패해도 알림 발행을 막지 않습니다. null을 반환하면 발송 계층이 기존 기본 본문으로 폴백합니다.
     *
     * @param capplm 신청서 마스터
     * @return 메일 페이로드 JSON. 실패 시 null
     */
    private String renderApprovalMail(Capplm capplm) {
        try {
            return approvalMailPayloadProvider.render(capplm);
        } catch (RuntimeException e) {
            log.warn(
                    "결재요청 메일 페이로드 생성 실패 — 기본 본문으로 발송합니다: apfMngNo={}, 사유={}",
                    capplm.getApfMngNo(),
                    e.toString());
            return null;
        }
    }

    /**
     * null을 빈 문자열로 접어 알림 제목·본문 조립에서 {@code NullPointerException}을 방지합니다.
     *
     * @param s 원본 문자열
     * @return null이면 빈 문자열, 아니면 원본 그대로
     */
    private static String safeText(String s) {
        return s == null ? "" : s;
    }
}
