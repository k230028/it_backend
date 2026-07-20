package com.kdb.it.common.notification.service;

import com.kdb.it.common.notification.dispatcher.NotificationDispatchResult;
import com.kdb.it.common.notification.dispatcher.NotificationDispatcher;
import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock
    private CinfmmRepository repository;
    @Mock
    private NotificationDispatcher dispatcher;

    private NotificationDispatchService service;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new NotificationDispatchService(repository, dispatcher, meterRegistry);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
    }

    @Test
    @DisplayName("dispatch 실패는 FAILED 상태를 남기고 예외를 전파하지 않는다")
    void dispatch_failure_marksFailed() {
        Cinfmm row = pending();
        given(repository.findById(row.getInfmMsgNo())).willReturn(Optional.of(row));
        given(dispatcher.dispatch(row, row.getSdDocCone()))
                .willReturn(NotificationDispatchResult.failure("timeout"));

        service.dispatch(row.getInfmMsgNo());

        assertThat(row.getInfmSdStsC()).isEqualTo(Cinfmm.DISPATCH_FAILED);
        assertThat(row.getErrCone()).isEqualTo("timeout");
    }

    @Test
    @DisplayName("dispatch 성공은 SENT 상태를 남긴다")
    void dispatch_success_marksSent() {
        Cinfmm row = pending();
        given(repository.findById(row.getInfmMsgNo())).willReturn(Optional.of(row));
        given(dispatcher.dispatch(row, row.getSdDocCone())).willReturn(NotificationDispatchResult.sent());

        service.dispatch(row.getInfmMsgNo());

        assertThat(row.getInfmSdStsC()).isEqualTo(Cinfmm.DISPATCH_SENT);
    }

    @Test
    @DisplayName("이미 SENT인 행은 다시 발송하지 않는다")
    void dispatch_alreadySent_skips() {
        Cinfmm row = pending();
        row.markDispatchSent("04", "payload");
        given(repository.findById(row.getInfmMsgNo())).willReturn(Optional.of(row));

        service.dispatch(row.getInfmMsgNo());

        verify(dispatcher, never()).dispatch(row, row.getSdDocCone());
    }

    @Test
    @DisplayName("dispatch 실패가 최대 재시도 횟수에 도달하면 소진 지표를 기록한다")
    void dispatch_exhaustedBlankChannel_recordsBothMetrics() {
        Cinfmm row = Cinfmm.builder()
                .infmMsgNo("INF-2026-00000002")
                .sdTc(" ")
                .sdDocCone("payload")
                .infmSdStsC(Cinfmm.DISPATCH_PENDING)
                .reTryNot(4)
                .build();
        given(repository.findById(row.getInfmMsgNo())).willReturn(Optional.of(row));
        given(dispatcher.dispatch(row, row.getSdDocCone()))
                .willReturn(NotificationDispatchResult.failure("장애"));

        service.dispatch(row.getInfmMsgNo());

        assertThat(row.getReTryNot()).isEqualTo(5);
        assertThat(meterRegistry.counter("notification.dispatch.failure", "channel", "unknown").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("notification.dispatch.exhausted", "channel", "unknown").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("dispatch 실패의 채널이 null이면 unknown 지표로 기록한다")
    void dispatch_nullChannel_recordsUnknownMetric() {
        Cinfmm row = Cinfmm.builder()
                .infmMsgNo("INF-2026-00000003")
                .sdTc(null)
                .infmSdStsC(Cinfmm.DISPATCH_PENDING)
                .reTryNot(0)
                .build();
        given(repository.findById(row.getInfmMsgNo())).willReturn(Optional.of(row));
        given(dispatcher.dispatch(row, null)).willReturn(NotificationDispatchResult.failure(null));

        service.dispatch(row.getInfmMsgNo());

        assertThat(meterRegistry.counter("notification.dispatch.failure", "channel", "unknown").count())
                .isEqualTo(1.0);
        assertThat(row.getErrCone()).isNull();
    }

    @Test
    @DisplayName("dispatch 대상 알림이 없으면 예외를 반환한다")
    void dispatch_missingNotification_throws() {
        given(repository.findById("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.dispatch("UNKNOWN"))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private Cinfmm pending() {
        return Cinfmm.builder()
                .infmMsgNo("INF-2026-00000001")
                .sdTc("04")
                .sdDocCone("payload")
                .infmSdStsC(Cinfmm.DISPATCH_PENDING)
                .reTryNot(0)
                .build();
    }
}
