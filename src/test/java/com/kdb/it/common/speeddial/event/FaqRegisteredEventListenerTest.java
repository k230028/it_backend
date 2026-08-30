package com.kdb.it.common.speeddial.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.service.NotificationDispatchService;
import com.kdb.it.common.notification.service.NotificationOutboxService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FaqRegisteredEventListenerTest {

    @Mock private RoleRepository roleRepository;
    @Mock private NotificationOutboxService outboxService;
    @Mock private NotificationDispatchService dispatchService;

    @Test
    void sendsOneGweNotificationPerDistinctActiveSystemAdmin() {
        FaqRegisteredEventListener listener =
                new FaqRegisteredEventListener(
                        roleRepository, outboxService, dispatchService, new ObjectMapper());
        given(roleRepository.findActiveUserEnosByAthId("ITPAD001"))
                .willReturn(java.util.List.of("K100", "K100", "K200"));
        given(outboxService.enqueue(any())).willReturn("INF-2026-0001");

        listener.onFaqRegistered(
                new FaqRegisteredEvent(
                        "NAC-2026-0001",
                        "FAQ 등록",
                        "<p>내용</p>",
                        "K900",
                        "등록자",
                        "/board/BLBM-CHANGED?postId=NAC-2026-0001"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(outboxService, times(2)).enqueue(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getAllValues())
                .extracting(NotificationEvent::recipientEno)
                .containsExactly("K100", "K200");
        org.assertj.core.api.Assertions.assertThat(captor.getAllValues())
                .allSatisfy(
                        event ->
                                org.assertj.core.api.Assertions.assertThat(event.itPtlSdTc())
                                        .isEqualTo("04"));
    }

    @Test
    void ignoresBlankRecipientsAndDoesNotDispatchWhenOutboxIsUnavailable() {
        FaqRegisteredEventListener listener =
                new FaqRegisteredEventListener(
                        roleRepository, outboxService, dispatchService, new ObjectMapper());
        given(roleRepository.findActiveUserEnosByAthId("ITPAD001"))
                .willReturn(Arrays.asList(null, " ", "K100"));
        given(outboxService.enqueue(any())).willReturn(null);

        listener.onFaqRegistered(event());

        verify(outboxService, times(1)).enqueue(any());
        verify(dispatchService, never()).dispatch(any());
    }

    @Test
    void continuesWithLaterRecipientWhenOneNotificationFails() {
        FaqRegisteredEventListener listener =
                new FaqRegisteredEventListener(
                        roleRepository, outboxService, dispatchService, new ObjectMapper());
        given(roleRepository.findActiveUserEnosByAthId("ITPAD001"))
                .willReturn(java.util.List.of("K100", "K200"));
        given(outboxService.enqueue(any()))
                .willThrow(new IllegalStateException("outbox error"))
                .willReturn("INF-2026-0002");

        listener.onFaqRegistered(event());

        verify(outboxService, times(2)).enqueue(any());
        verify(dispatchService).dispatch("INF-2026-0002");
    }

    @Test
    void sendsFallbackNotificationWhenMailPayloadSerializationFails() throws Exception {
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        given(roleRepository.findActiveUserEnosByAthId("ITPAD001"))
                .willReturn(java.util.List.of("K100"));
        given(failingMapper.writeValueAsString(any()))
                .willThrow(new JsonProcessingException("serialization error") {});
        given(outboxService.enqueue(any())).willReturn("INF-2026-0003");
        FaqRegisteredEventListener listener =
                new FaqRegisteredEventListener(
                        roleRepository, outboxService, dispatchService, failingMapper);

        listener.onFaqRegistered(event());

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(outboxService).enqueue(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sdPayload()).isNull();
        verify(dispatchService).dispatch("INF-2026-0003");
    }

    private static FaqRegisteredEvent event() {
        return new FaqRegisteredEvent(
                "NAC-2026-0001",
                "FAQ 등록",
                "<p>내용</p>",
                "K900",
                "등록자",
                "/board/BLBM-CHANGED?postId=NAC-2026-0001");
    }
}
