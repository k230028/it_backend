package com.kdb.it.common.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceTest {

    @Mock private CinfmmRepository repository;

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

    @Test
    @DisplayName("enqueue는 수신자가 null이면 시퀀스 조회 없이 종료한다")
    void enqueue_nullRecipient_skipsBeforeSequence() {
        assertThat(service.enqueue(event(null))).isNull();

        verify(repository, never()).getNextVal();
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("enqueue는 저장 컬럼 길이를 넘는 제목과 본문과 URL을 절단한다")
    void enqueue_longValues_clampsToColumnLengths() {
        given(repository.getNextVal()).willReturn(8L);
        NotificationEvent event =
                NotificationEvent.builder()
                        .recipientEno("E0002")
                        .itPtlInfmSvcTc(NotificationEvent.TYPE_SYSTEM)
                        .ttl("가".repeat(101))
                        .infmMsgCone("나".repeat(4001))
                        .infmRcdUrl("/" + "u".repeat(300))
                        .build();

        service.enqueue(event);

        ArgumentCaptor<Cinfmm> captor = ArgumentCaptor.forClass(Cinfmm.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTtl()).hasSize(100);
        assertThat(captor.getValue().getInfmMsgCone()).hasSize(4000);
        assertThat(captor.getValue().getInfmRcdUrl()).hasSize(300);
    }

    @Test
    @DisplayName("enqueue는 SD_DOC_CONE 컬럼 폭을 넘는 발송 페이로드를 null로 저장하고 나머지는 그대로 적재한다")
    void enqueue_oversizedSdPayload_storesNullAndKeepsRestOfNotification() {
        given(repository.getNextVal()).willReturn(9L);
        // JSON 페이로드는 바이트 단위로 잘라내면 파싱 불가능한 문자열이 되므로 clamp(부분 문자열) 대신
        // null로 접어 발송 계층이 기본 본문으로 폴백하게 해야 한다. UTF-8 3바이트 한글로 4000바이트를
        // 확실히 넘기도록 1334자(4002바이트)를 사용한다.
        String oversizedPayload = "가".repeat(1334);
        NotificationEvent event =
                NotificationEvent.builder()
                        .recipientEno("E0003")
                        .itPtlInfmSvcTc(NotificationEvent.TYPE_APPROVAL_REQUEST)
                        .ttl("결재요청: 전산예산 신청서")
                        .infmMsgCone("전산예산 신청서")
                        .itPtlSdTc("04")
                        .sdPayload(oversizedPayload)
                        .build();

        String id = service.enqueue(event);

        ArgumentCaptor<Cinfmm> captor = ArgumentCaptor.forClass(Cinfmm.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(id).isEqualTo(captor.getValue().getInfmMsgNo());
        assertThat(captor.getValue().getSdDocCone()).isNull();
        // 페이로드만 null로 접히고 나머지 알림 행은 그대로 저장된다(행 자체가 유실되지 않는다).
        assertThat(captor.getValue().getTtl()).isEqualTo("결재요청: 전산예산 신청서");
        assertThat(captor.getValue().getInfmMsgCone()).isEqualTo("전산예산 신청서");
        assertThat(captor.getValue().getRmsEno()).isEqualTo("E0003");
        assertThat(captor.getValue().getInfmSdStsC()).isEqualTo(Cinfmm.DISPATCH_PENDING);
    }

    private NotificationEvent event(String recipientEno) {
        return NotificationEvent.builder()
                .recipientEno(recipientEno)
                .itPtlInfmSvcTc(NotificationEvent.TYPE_SYSTEM)
                .ttl("제목")
                .infmMsgCone("본문")
                .itPtlSdTc("04")
                .sdPayload("payload")
                .build();
    }
}
