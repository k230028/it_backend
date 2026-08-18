package com.kdb.it.common.notification.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.infra.eai.config.GweProperties;
import com.kdb.it.infra.eai.dto.EaiRequest;
import com.kdb.it.infra.eai.dto.EaiResult;
import com.kdb.it.infra.eai.dto.GwePayload;
import com.kdb.it.infra.eai.service.EaiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** sdPayload 유무에 따른 GWE 전문 구성 분기를 검증한다. */
class NotificationDispatcherRouterMailPayloadTest {

    private final EaiService eaiService = mock(EaiService.class);
    private final NotificationDispatcherRouter router =
            new NotificationDispatcherRouter(
                    eaiService,
                    new GweProperties("ITPO00051630"),
                    "https://it.kdb.co.kr",
                    new ObjectMapper());

    private GwePayload dispatchAndCapture(String sdPayload) {
        when(eaiService.sendEai(any())).thenReturn(EaiResult.success(""));
        Cinfmm notification =
                Cinfmm.builder()
                        .infmMsgNo("INF-1")
                        .rmsEno("k140024")
                        .ttl("결재요청: 전산예산 신청서")
                        .infmMsgCone("본문")
                        .itPtlSdTc(NotificationDispatcherRouter.CHANNEL_EAI_GWE)
                        .build();

        router.dispatch(notification, sdPayload);

        ArgumentCaptor<EaiRequest> captor = ArgumentCaptor.forClass(EaiRequest.class);
        verify(eaiService).sendEai(captor.capture());
        return (GwePayload) captor.getValue().payload();
    }

    @Test
    @DisplayName("페이로드가 있으면 그 제목과 본문을 전문에 싣는다")
    void withPayload_usesSubjectAndHtml() {
        GwePayload payload =
                dispatchAndCapture(
                        "{\"subject\":\"[IT정보화포탈] 전산예산 신청서 결재 요청\"," + "\"html\":\"<p>총괄표</p>\"}");

        assertThat(payload.subject()).isEqualTo("[IT정보화포탈] 전산예산 신청서 결재 요청");
        assertThat(payload.contents()).isEqualTo("<p>총괄표</p>");
    }

    @Test
    @DisplayName("페이로드가 없으면 기존 알림 제목·본문으로 폴백한다")
    void withoutPayload_fallsBack() {
        GwePayload payload = dispatchAndCapture(null);

        assertThat(payload.subject()).isEqualTo("결재요청: 전산예산 신청서");
        assertThat(payload.contents()).contains("본문").contains("결재 화면으로 이동");
    }

    @Test
    @DisplayName("페이로드가 깨져 있으면 폴백하고 발송은 계속한다")
    void brokenPayload_fallsBack() {
        GwePayload payload = dispatchAndCapture("{broken JSON");

        assertThat(payload.subject()).isEqualTo("결재요청: 전산예산 신청서");
        assertThat(payload.contents()).contains("결재 화면으로 이동");
    }
}
