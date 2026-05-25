package com.kdb.it.common.notification.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.notification.entity.Cinfmm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * StubNotificationDispatcher 단위 테스트
 *
 * <p>인앱 발송 메타 기록과 부수 효과 실패 격리 정책을 검증합니다.</p>
 */
class StubNotificationDispatcherTest {

    private final StubNotificationDispatcher dispatcher = new StubNotificationDispatcher();

    @Test
    @DisplayName("dispatch: 인앱 채널과 페이로드를 알림에 기록한다")
    void dispatch_정상호출_인앱메타기록() {
        Cinfmm notification = Cinfmm.builder().infMngNo("INF-1").build();

        dispatcher.dispatch(notification, "{\"event\":\"created\"}");

        assertThat(notification.getEaiSdTpC()).isEqualTo("001");
        assertThat(notification.getEaiSdCone()).isEqualTo("{\"event\":\"created\"}");
        assertThat(notification.getEaiSdDtm()).isNotNull();
    }

    @Test
    @DisplayName("dispatch: 메타 기록 예외는 외부로 전파하지 않는다")
    void dispatch_메타기록실패_예외흡수() {
        Cinfmm notification = mock(Cinfmm.class);
        doThrow(new IllegalStateException("기록 실패")).when(notification).markDispatched("001", null);

        assertThatCode(() -> dispatcher.dispatch(notification, null)).doesNotThrowAnyException();
    }
}
