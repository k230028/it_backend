package com.kdb.it.common.speeddial.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.notification.dispatcher.MailPayload;
import com.kdb.it.common.notification.dispatcher.NotificationDispatcherRouter;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.service.NotificationDispatchService;
import com.kdb.it.common.notification.service.NotificationOutboxService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QnaRegisteredEventListenerTest {

    @Mock private RoleRepository roleRepository;
    @Mock private NotificationOutboxService outboxService;
    @Mock private NotificationDispatchService dispatchService;

    @Test
    void sendsAnInAppAndGweNotificationToEveryDistinctSystemAdmin() throws Exception {
        QnaRegisteredEventListener listener =
                new QnaRegisteredEventListener(
                        roleRepository, outboxService, dispatchService, new ObjectMapper());
        given(roleRepository.findActiveUserEnosByAthId("ITPAD001"))
                .willReturn(List.of("K100", "K100", "K200"));
        given(outboxService.enqueue(any())).willReturn("INF-2026-0142");

        listener.onQnaRegistered(
                new QnaRegisteredEvent(
                        "NAC-2026-0142",
                        "[문의] (기능 개선) 검색 조건 저장",
                        "기능 개선",
                        "검색 조건 저장",
                        "K900",
                        "Q&A",
                        "/board/qna",
                        "/board/BLBM-QNA?postId=NAC-2026-0142"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(outboxService, times(2)).enqueue(captor.capture());
        verify(dispatchService, times(2)).dispatch("INF-2026-0142");
        assertThat(captor.getAllValues())
                .extracting(NotificationEvent::recipientEno)
                .containsExactly("K100", "K200");
        assertThat(captor.getAllValues())
                .allSatisfy(
                        notification -> {
                            assertThat(notification.itPtlInfmSvcTc())
                                    .isEqualTo(NotificationEvent.TYPE_SYSTEM);
                            assertThat(notification.itPtlSdTc())
                                    .isEqualTo(NotificationDispatcherRouter.CHANNEL_EAI_GWE);
                            assertThat(notification.ttl())
                                    .isEqualTo("문의 등록: [문의] (기능 개선) 검색 조건 저장");
                            assertThat(notification.infmRcdUrl())
                                    .isEqualTo("/board/BLBM-QNA?postId=NAC-2026-0142");
                        });
        MailPayload payload =
                new ObjectMapper()
                        .readValue(captor.getAllValues().getFirst().sdPayload(), MailPayload.class);
        assertThat(payload.subject()).isEqualTo("[IT정보화포탈] (기능 개선) 검색 조건 저장");
        assertThat(payload.html()).contains("문의 등록", "문의 개요", "등록자", "등록 화면", "문의 확인 ↗");
    }

    @Test
    void null과_빈_관리자_사번은_알림_대상에서_제외한다() {
        QnaRegisteredEventListener listener = listener(new ObjectMapper());
        given(roleRepository.findActiveUserEnosByAthId("ITPAD001"))
                .willReturn(java.util.Arrays.asList(null, " ", "K100"));
        given(outboxService.enqueue(any())).willReturn("OUT-1");

        listener.onQnaRegistered(event(null, null));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(outboxService).enqueue(captor.capture());
        assertThat(captor.getValue().recipientEno()).isEqualTo("K100");
        assertThat(captor.getValue().ttl()).isEqualTo("문의 등록: ");
        verify(dispatchService).dispatch("OUT-1");
    }

    @Test
    void 한_관리자_발송_실패가_다음_관리자_알림을_막지_않는다() {
        QnaRegisteredEventListener listener = listener(new ObjectMapper());
        given(roleRepository.findActiveUserEnosByAthId("ITPAD001"))
                .willReturn(List.of("K100", "K200"));
        given(outboxService.enqueue(any()))
                .willThrow(new IllegalStateException("저장 실패"))
                .willReturn("OUT-2");

        listener.onQnaRegistered(event("문의", "기능"));

        verify(outboxService, times(2)).enqueue(any());
        verify(dispatchService).dispatch("OUT-2");
    }

    @Test
    void 아웃박스_식별자가_null이면_발송하지_않는다() {
        QnaRegisteredEventListener listener = listener(new ObjectMapper());
        given(roleRepository.findActiveUserEnosByAthId("ITPAD001")).willReturn(List.of("K100"));
        given(outboxService.enqueue(any())).willReturn(null);

        listener.onQnaRegistered(event("문의", "기능"));

        verify(dispatchService, never()).dispatch(any());
    }

    @Test
    void 메일_페이로드_직렬화_실패시_null_페이로드로_적재한다() throws Exception {
        ObjectMapper mapper = org.mockito.Mockito.mock(ObjectMapper.class);
        willThrow(new com.fasterxml.jackson.core.JsonProcessingException("직렬화 실패") {})
                .given(mapper)
                .writeValueAsString(any(MailPayload.class));
        QnaRegisteredEventListener listener = listener(mapper);
        given(roleRepository.findActiveUserEnosByAthId("ITPAD001")).willReturn(List.of("K100"));

        listener.onQnaRegistered(event("문의", "기능"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(outboxService).enqueue(captor.capture());
        assertThat(captor.getValue().sdPayload()).isNull();
    }

    private QnaRegisteredEventListener listener(ObjectMapper mapper) {
        return new QnaRegisteredEventListener(
                roleRepository, outboxService, dispatchService, mapper);
    }

    private QnaRegisteredEvent event(String title, String categoryName) {
        return new QnaRegisteredEvent(
                "NAC-1", title, categoryName, null, null, null, null, "/board/qna?postId=NAC-1");
    }
}
