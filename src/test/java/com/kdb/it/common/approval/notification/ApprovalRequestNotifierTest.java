package com.kdb.it.common.approval.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.mail.ApprovalMailPayloadProvider;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.notification.dispatcher.NotificationDispatcherRouter;
import com.kdb.it.common.notification.event.NotificationEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * ApprovalRequestNotifier 단위 테스트
 *
 * <p>결재선의 다음 결재자 선정, EAI 채널 알림 이벤트 발행, 메일 페이로드 렌더링 실패 격리를 검증합니다. 원래 {@code ApplicationServiceTest}에
 * 있던 {@code submit_결재요청알림_EAI채널발행}, {@code submit_메일페이로드생성실패_신청서등록과알림발행유지}, {@code
 * submit_다음결재자사번공백_알림생략}의 상세 검증을 이 클래스로 이관했습니다.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalRequestNotifierTest {

    private static final String APF_MNG_NO = "APF-2026-00000001";

    @Mock private ApproverRepository approverRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ApprovalMailPayloadProvider approvalMailPayloadProvider;

    @InjectMocks private ApprovalRequestNotifier approvalRequestNotifier;

    private Capplm capplm() {
        return Capplm.builder().apfMngNo(APF_MNG_NO).dcdReqTtl("테스트 신청서").build();
    }

    /** 미결재(itPtlDcdStsC="1") 상태의 Cdecim 생성 */
    private Cdecim pendingApprover(String eno, int sqn, String lstDcdYn) {
        return Cdecim.builder()
                .dcdMngNo(APF_MNG_NO)
                .dcrSqnSno(sqn)
                .dcrEno(eno)
                .lstDcdYn(lstDcdYn)
                .itPtlDcdStsC(com.kdb.it.common.approval.domain.DecisionStatus.PENDING.code())
                .build();
    }

    @Test
    @DisplayName("notifyApprovalRequest: 다음 결재자에게 EAI 채널로 결재요청 알림을 발행한다")
    void notifyApprovalRequest_다음결재자_EAI채널발행() {
        Capplm capplm = capplm();
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("10002", 1, "Y")));
        given(approvalMailPayloadProvider.render(capplm)).willReturn("{\"subject\":\"s\"}");

        approvalRequestNotifier.notifyApprovalRequest(capplm);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().recipientEno()).isEqualTo("10002");
        assertThat(captor.getValue().itPtlInfmSvcTc())
                .isEqualTo(NotificationEvent.TYPE_APPROVAL_REQUEST);
        assertThat(captor.getValue().itPtlSdTc())
                .isEqualTo(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
        assertThat(captor.getValue().infmRcdUrl()).isEqualTo("/approval/list?tab=pending");
        assertThat(captor.getValue().sdPayload()).isEqualTo("{\"subject\":\"s\"}");
    }

    @Test
    @DisplayName("notifyApprovalRequest: 메일 페이로드 생성이 실패해도 sdPayload=null로 알림은 그대로 발행된다")
    void notifyApprovalRequest_메일페이로드생성실패_알림발행유지() {
        Capplm capplm = capplm();
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("10002", 1, "Y")));
        given(approvalMailPayloadProvider.render(capplm))
                .willThrow(new RuntimeException("메일 렌더링 실패(테스트)"));

        // 렌더링 실패가 알림 발행 자체를 막지 않는다 — 실패 격리 계약의 핵심
        approvalRequestNotifier.notifyApprovalRequest(capplm);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().itPtlInfmSvcTc())
                .isEqualTo(NotificationEvent.TYPE_APPROVAL_REQUEST);
        assertThat(captor.getValue().sdPayload()).isNull();
    }

    @Test
    @DisplayName("notifyApprovalRequest: 다음 결재자 사번이 공백이면 알림을 발행하지 않는다")
    void notifyApprovalRequest_다음결재자사번공백_알림생략() {
        Capplm capplm = capplm();
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(pendingApprover("   ", 1, "Y")));

        approvalRequestNotifier.notifyApprovalRequest(capplm);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("notifyApprovalRequest: 미결재 결재자가 없으면(결재선 전부 처리됨) 알림을 발행하지 않는다")
    void notifyApprovalRequest_미결재없음_알림생략() {
        Capplm capplm = capplm();
        Cdecim approved =
                Cdecim.builder()
                        .dcdMngNo(APF_MNG_NO)
                        .dcrSqnSno(1)
                        .dcrEno("10002")
                        .lstDcdYn("Y")
                        .itPtlDcdStsC(
                                com.kdb.it.common.approval.domain.DecisionStatus.APPROVED.code())
                        .build();
        given(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(APF_MNG_NO))
                .willReturn(List.of(approved));

        approvalRequestNotifier.notifyApprovalRequest(capplm);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
