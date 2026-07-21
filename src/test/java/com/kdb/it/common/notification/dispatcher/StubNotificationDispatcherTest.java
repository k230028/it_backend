package com.kdb.it.common.notification.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.infra.eai.config.GweProperties;
import com.kdb.it.infra.eai.service.EaiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NotificationDispatcherRouter 인앱 호환성 테스트
 *
 * <p>인앱 발송 메타 기록과 부수 효과 실패 격리 정책을 검증합니다.
 */
class StubNotificationDispatcherTest {

    private final NotificationDispatcherRouter dispatcher =
            new NotificationDispatcherRouter(
                    mock(EaiService.class), new GweProperties("TEST00000001"));

    @Test
    @DisplayName("dispatch: 인앱 채널은 성공 결과만 반환하고 상태를 변경하지 않는다")
    void dispatch_정상호출_성공결과() {
        Cinfmm notification = Cinfmm.builder().infmMsgNo("INF-1").build();

        NotificationDispatchResult result =
                dispatcher.dispatch(notification, "{\"event\":\"created\"}");

        assertThat(result.success()).isTrue();
        assertThat(notification.getItPtlSdTc()).isNull();
        assertThat(notification.getSdDtm()).isNull();
    }

    @Test
    @DisplayName("dispatch: 인앱 처리에서는 엔티티 비즈니스 메서드를 호출하지 않는다")
    void dispatch_인앱처리_엔티티변경없음() {
        Cinfmm notification = mock(Cinfmm.class);

        assertThat(dispatcher.dispatch(notification, null).success()).isTrue();
    }
}
