package com.kdb.it.common.notification.service;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceTest {

    @Mock
    private CinfmmRepository repository;

    private NotificationOutboxService service;

    @BeforeEach
    void setUp() {
        service = new NotificationOutboxService(repository);
    }

    @Test
    @DisplayName("enqueue는 PENDING 행을 저장하고 ID를 반환한다")
    void enqueue_pendingSaved() {
        given(repository.getNextVal()).willReturn(7L);

        String id = service.enqueue(event("E0001"));

        ArgumentCaptor<Cinfmm> captor = ArgumentCaptor.forClass(Cinfmm.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(id).isEqualTo(captor.getValue().getInfmMsgNo());
        assertThat(captor.getValue().getInfmSdStsC()).isEqualTo(Cinfmm.DISPATCH_PENDING);
        assertThat(captor.getValue().getReTryNot()).isZero();
    }

    @Test
    @DisplayName("enqueue는 수신자가 비어 있으면 저장하지 않는다")
    void enqueue_blankRecipient_skips() {
        assertThat(service.enqueue(event(" "))).isNull();
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    private NotificationEvent event(String recipientEno) {
        return NotificationEvent.builder()
                .recipientEno(recipientEno)
                .infmSvcTc(NotificationEvent.TYPE_SYSTEM)
                .ttl("제목")
                .infmMsgCone("본문")
                .sdTc("04")
                .sdPayload("payload")
                .build();
    }
}
