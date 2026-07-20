package com.kdb.it.common.notification.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.infra.eai.config.GweProperties;
import com.kdb.it.infra.eai.dto.EaiRequest;
import com.kdb.it.infra.eai.dto.EaiResult;
import com.kdb.it.infra.eai.dto.GwePayload;
import com.kdb.it.infra.eai.service.EaiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherRouterTest {

    private static final GweProperties GWE_PROPERTIES = new GweProperties("TEST00000001");

    @Mock
    private EaiService eaiService;

    @Test
    @DisplayName("sdTc가 없으면 인앱 성공 결과를 반환하고 EAI를 호출하지 않는다")
    void dispatch_nullChannel_returnsSent() {
        NotificationDispatcherRouter router = new NotificationDispatcherRouter(eaiService, GWE_PROPERTIES);
        Cinfmm notification = notification(null);

        NotificationDispatchResult result = router.dispatch(notification, "{\"id\":1}");

        assertThat(result.success()).isTrue();
        assertThat(notification.getSdTc()).isNull();
        assertThat(notification.getSdDtm()).isNull();
        verify(eaiService, never()).sendEai(any());
    }

    @Test
    @DisplayName("외부 EAI 채널이면 GWE 전문 요청으로 EaiService에 위임한다")
    void dispatch_externalChannel_delegatesToEai() {
        NotificationDispatcherRouter router = new NotificationDispatcherRouter(eaiService, GWE_PROPERTIES);
        Cinfmm notification = notification(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
        when(eaiService.sendEai(any())).thenReturn(EaiResult.success("OK"));

        NotificationDispatchResult result = router.dispatch(notification, null);

        assertThat(result.success()).isTrue();
        ArgumentCaptor<EaiRequest> captor = ArgumentCaptor.forClass(EaiRequest.class);
        verify(eaiService).sendEai(captor.capture());
        assertThat(captor.getValue().ifId()).isEqualTo(GWE_PROPERTIES.ifId());
        assertThat(captor.getValue().payload()).isInstanceOf(GwePayload.class);
        GwePayload payload = (GwePayload) captor.getValue().payload();
        assertThat(payload.recvIds()).isEqualTo("E0001");
        assertThat(payload.subject()).isEqualTo("알림 제목");
        assertThat(notification.getSdTc()).isEqualTo(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
        assertThat(notification.getSdDtm()).isNull();
    }

    @Test
    @DisplayName("EAI 실패 결과는 예외를 던지지 않고 원 알림 흐름을 유지한다")
    void dispatch_externalFailure_doesNotThrow() {
        NotificationDispatcherRouter router = new NotificationDispatcherRouter(eaiService, GWE_PROPERTIES);
        Cinfmm notification = notification(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
        when(eaiService.sendEai(any())).thenReturn(EaiResult.failure("장애"));

        NotificationDispatchResult result = router.dispatch(notification, "payload");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("장애");
        assertThat(notification.getSdTc()).isEqualTo(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
        assertThat(notification.getSdDtm()).isNull();
    }

    @Test
    @DisplayName("비활성화된 EAI 발송은 성공으로 처리하고 기본 제목과 본문을 사용한다")
    void dispatch_externalSkipped_usesDefaultTextAndReturnsSent() {
        NotificationDispatcherRouter router = new NotificationDispatcherRouter(eaiService, GWE_PROPERTIES);
        Cinfmm notification = Cinfmm.builder()
                .infmMsgNo("INF-2026-00000002")
                .infmSvcTc("01")
                .ttl(" ")
                .infmMsgCone(null)
                .rmsEno("E0002")
                .inqYn("N")
                .sdTc(NotificationDispatcherRouter.CHANNEL_EAI_GWE)
                .build();
        when(eaiService.sendEai(any())).thenReturn(EaiResult.skip());

        NotificationDispatchResult result = router.dispatch(notification, null);

        assertThat(result.success()).isTrue();
        ArgumentCaptor<EaiRequest> captor = ArgumentCaptor.forClass(EaiRequest.class);
        verify(eaiService).sendEai(captor.capture());
        GwePayload payload = (GwePayload) captor.getValue().payload();
        assertThat(payload.subject()).isEqualTo("IT Portal 알림");
        assertThat(payload.contents()).isEqualTo("새 알림이 도착했습니다.");
    }

    @Test
    @DisplayName("지원하지 않는 채널은 인앱 성공으로 처리한다")
    void dispatch_unknownChannel_fallsBackToSent() {
        NotificationDispatcherRouter router = new NotificationDispatcherRouter(eaiService, GWE_PROPERTIES);
        Cinfmm notification = notification("99");

        NotificationDispatchResult result = router.dispatch(notification, "payload");

        assertThat(result.success()).isTrue();
        verify(eaiService, never()).sendEai(any());
    }

    @Test
    @DisplayName("EAI 호출 예외는 실패 결과로 변환한다")
    void dispatch_externalException_returnsFailure() {
        NotificationDispatcherRouter router = new NotificationDispatcherRouter(eaiService, GWE_PROPERTIES);
        Cinfmm notification = notification(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
        when(eaiService.sendEai(any())).thenThrow(new IllegalStateException("연계 중단"));

        NotificationDispatchResult result = router.dispatch(notification, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("연계 중단");
    }

    private Cinfmm notification(String sdTc) {
        return Cinfmm.builder()
                .infmMsgNo("INF-2026-00000001")
                .infmSvcTc("01")
                .ttl("알림 제목")
                .infmMsgCone("알림 본문")
                .infmRcdUrl("/notifications")
                .rmsEno("E0001")
                .inqYn("N")
                .sdTc(sdTc)
                .build();
    }
}
