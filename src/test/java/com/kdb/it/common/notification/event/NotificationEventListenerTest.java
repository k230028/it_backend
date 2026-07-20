package com.kdb.it.common.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.event.ApprovalRecalledEvent;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.notification.service.NotificationDispatchService;
import com.kdb.it.common.notification.service.NotificationOutboxService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * NotificationEventListener 단위 테스트
 *
 * <p>명시적 알림과 결재 결과·회수 이벤트의 알림 변환 및 실패 격리를 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationOutboxService outboxService;

    @Mock
    private NotificationDispatchService dispatchService;

    @Mock
    private ApplicationRepository applicationRepository;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(
                outboxService, dispatchService, applicationRepository, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("onNotificationEvent: 수신 이벤트를 알림 서비스에 전달한다")
    void onNotificationEvent_정상요청_서비스전달() {
        NotificationEvent event = NotificationEvent.builder().recipientEno("10001").itPtlInfmSvcTc("01").build();

        given(outboxService.enqueue(event)).willReturn("INF-1");

        listener.onNotificationEvent(event);

        verify(outboxService).enqueue(event);
        verify(dispatchService).dispatch("INF-1");
    }

    @Test
    @DisplayName("onNotificationEvent: 발송 실패가 발생해도 예외를 전파하지 않는다")
    void onNotificationEvent_발송실패_예외흡수() {
        NotificationEvent event = NotificationEvent.builder().recipientEno("10001").itPtlInfmSvcTc("01").build();
        given(outboxService.enqueue(event)).willReturn("INF-1");
        doThrow(new IllegalStateException("발송 실패")).when(dispatchService).dispatch("INF-1");

        listener.onNotificationEvent(event);

        verify(outboxService).enqueue(event);
        verify(dispatchService).dispatch("INF-1");
    }

    @Test
    @DisplayName("onApprovalCompleted: 신청서가 없으면 알림을 발송하지 않는다")
    void onApprovalCompleted_신청서없음_발송하지않음() {
        given(applicationRepository.findById("APF-1")).willReturn(Optional.empty());

        listener.onApprovalCompleted(new ApprovalCompletedEvent("APF-1", "결재완료"));

        verify(outboxService, never()).enqueue(any());
    }

    @Test
    @DisplayName("onApprovalCompleted: 완료 결과를 신청자 알림으로 변환하며 긴 제목을 제한한다")
    void onApprovalCompleted_완료_알림변환() {
        Capplm application = Capplm.builder()
                .apfMngNo("APF-1")
                .dcdReqTtl("가".repeat(120))
                .dcdReqUsid("10001")
                .build();
        given(applicationRepository.findById("APF-1")).willReturn(Optional.of(application));
        given(outboxService.enqueue(any())).willReturn("INF-1");

        listener.onApprovalCompleted(new ApprovalCompletedEvent("APF-1", "결재완료"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(outboxService).enqueue(captor.capture());
        verify(dispatchService).dispatch("INF-1");
        assertThat(captor.getValue().recipientEno()).isEqualTo("10001");
        assertThat(captor.getValue().itPtlInfmSvcTc()).isEqualTo(NotificationEvent.TYPE_APPROVAL_RESULT);
        assertThat(captor.getValue().infmMsgCone()).isNotBlank();
        assertThat(captor.getValue().infmRcdUrl()).isEqualTo("/approval/list?tab=pending");
    }

    @Test
    @DisplayName("onApprovalCompleted: 알림 변환 중 실패가 발생해도 예외를 전파하지 않는다")
    void onApprovalCompleted_조회실패_예외흡수() {
        given(applicationRepository.findById("APF-1")).willThrow(new IllegalStateException("조회 실패"));

        listener.onApprovalCompleted(new ApprovalCompletedEvent("APF-1", "반려"));

        verify(outboxService, never()).enqueue(any());
    }

    @Test
    @DisplayName("onApprovalRecalled: 신청자와 유효한 기승인자에게만 회수 알림을 발송한다")
    void onApprovalRecalled_다중대상_유효수신자발송() {
        Capplm application = Capplm.builder()
                .apfMngNo("APF-1")
                .dcdReqTtl("회수 신청서")
                .dcdReqUsid("REQUESTER")
                .build();
        given(applicationRepository.findById("APF-1")).willReturn(Optional.of(application));
        given(outboxService.enqueue(any())).willReturn("INF-1");
        ApprovalRecalledEvent event = new ApprovalRecalledEvent(
                "APF-1", "RECALLER", Arrays.asList("APPROVER", " ", null));

        listener.onApprovalRecalled(event);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(outboxService, times(2)).enqueue(captor.capture());
        verify(dispatchService, times(2)).dispatch("INF-1");
        assertThat(captor.getAllValues()).extracting(value -> value.recipientEno())
                .containsExactly("REQUESTER", "APPROVER");
        assertThat(captor.getAllValues()).allSatisfy(notification ->
                assertThat(notification.itPtlInfmSvcTc()).isEqualTo(NotificationEvent.TYPE_APPROVAL_RECALLED));
    }

    @Test
    @DisplayName("onApprovalRecalled: 신청자 본인 회수이며 기승인자가 없으면 발송하지 않는다")
    void onApprovalRecalled_본인회수_발송하지않음() {
        Capplm application = Capplm.builder().apfMngNo("APF-1").dcdReqTtl(null).dcdReqUsid("10001").build();
        given(applicationRepository.findById("APF-1")).willReturn(Optional.of(application));

        listener.onApprovalRecalled(new ApprovalRecalledEvent("APF-1", "10001", null));

        verify(outboxService, never()).enqueue(any());
    }

    @Test
    @DisplayName("onApprovalRecalled: 신청서가 없으면 알림을 발송하지 않는다")
    void onApprovalRecalled_신청서없음_발송하지않음() {
        given(applicationRepository.findById("APF-1")).willReturn(Optional.empty());

        listener.onApprovalRecalled(new ApprovalRecalledEvent("APF-1", "10001", List.of("20001")));

        verify(outboxService, never()).enqueue(any());
    }

    @Test
    @DisplayName("onApprovalRecalled: 발송 중 실패가 발생해도 예외를 전파하지 않는다")
    void onApprovalRecalled_발송실패_예외흡수() {
        Capplm application = Capplm.builder().apfMngNo("APF-1").dcdReqTtl("신청서").dcdReqUsid("10001").build();
        given(applicationRepository.findById("APF-1")).willReturn(Optional.of(application));
        given(outboxService.enqueue(any())).willThrow(new IllegalStateException("적재 실패"));

        listener.onApprovalRecalled(new ApprovalRecalledEvent("APF-1", "20001", List.of()));

        verify(outboxService).enqueue(any());
        verify(dispatchService, never()).dispatch(any());
    }
}
