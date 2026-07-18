package com.kdb.it.common.notification.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.notification.entity.Cinfmm;
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

    @Mock
    private EaiService eaiService;

    @Test
    @DisplayName("sdTc가 없으면 기존 인앱 채널로 발송 메타를 기록하고 EAI를 호출하지 않는다")
    void dispatch_nullChannel_marksInAppOnly() {
        NotificationDispatcherRouter router = new NotificationDispatcherRouter(eaiService);
        Cinfmm notification = notification(null);

        router.dispatch(notification, "{\"id\":1}");

        assertThat(notification.getSdTc()).isEqualTo(NotificationDispatcherRouter.CHANNEL_INAPP);
        assertThat(notification.getSdDocCone()).isEqualTo("{\"id\":1}");
        assertThat(notification.getSdDtm()).isNotNull();
        verify(eaiService, never()).sendEai(any());
    }

    @Test
    @DisplayName("외부 EAI 채널이면 GWE 전문 요청으로 EaiService에 위임한다")
    void dispatch_externalChannel_delegatesToEai() {
        NotificationDispatcherRouter router = new NotificationDispatcherRouter(eaiService);
        Cinfmm notification = notification(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
        when(eaiService.sendEai(any())).thenReturn(EaiResult.success("OK"));

        router.dispatch(notification, null);

        ArgumentCaptor<EaiRequest> captor = ArgumentCaptor.forClass(EaiRequest.class);
        verify(eaiService).sendEai(captor.capture());
        assertThat(captor.getValue().ifId()).isEqualTo(NotificationDispatcherRouter.GWE_IF_ID);
        assertThat(captor.getValue().payload()).isInstanceOf(GwePayload.class);
        GwePayload payload = (GwePayload) captor.getValue().payload();
        assertThat(payload.recvIds()).isEqualTo("E0001");
        assertThat(payload.subject()).isEqualTo("알림 제목");
        assertThat(notification.getSdTc()).isEqualTo(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
        assertThat(notification.getSdDtm()).isNotNull();
    }

    @Test
    @DisplayName("EAI 실패 결과는 예외를 던지지 않고 원 알림 흐름을 유지한다")
    void dispatch_externalFailure_doesNotThrow() {
        NotificationDispatcherRouter router = new NotificationDispatcherRouter(eaiService);
        Cinfmm notification = notification(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
        when(eaiService.sendEai(any())).thenReturn(EaiResult.failure("장애"));

        assertThatCode(() -> router.dispatch(notification, "payload")).doesNotThrowAnyException();

        assertThat(notification.getSdTc()).isEqualTo(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
        assertThat(notification.getSdDocCone()).isEqualTo("payload");
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
