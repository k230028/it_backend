package com.kdb.it.common.speeddial.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.notification.service.NotificationDispatchService;
import com.kdb.it.common.notification.service.NotificationOutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import com.kdb.it.common.notification.event.NotificationEvent;

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
                .allSatisfy(event -> org.assertj.core.api.Assertions.assertThat(event.itPtlSdTc()).isEqualTo("04"));
    }
}
