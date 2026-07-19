package com.kdb.it.common.notification.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CinfmmTest {

    @Test
    @DisplayName("발송 성공은 SENT 상태와 발송 메타를 기록한다")
    void markDispatchSent_recordsSuccess() {
        Cinfmm notification = pending();

        notification.markDispatchSent("04", "payload");

        assertThat(notification.getInfmSdStsC()).isEqualTo(Cinfmm.DISPATCH_SENT);
        assertThat(notification.getSdTc()).isEqualTo("04");
        assertThat(notification.getSdDocCone()).isEqualTo("payload");
        assertThat(notification.getSdDtm()).isNotNull();
        assertThat(notification.getErrCone()).isNull();
    }

    @Test
    @DisplayName("발송 실패는 FAILED 상태와 재시도 횟수·오류내용을 기록한다")
    void markDispatchFailed_recordsFailure() {
        Cinfmm notification = pending();

        notification.markDispatchFailed("EAI timeout");

        assertThat(notification.getInfmSdStsC()).isEqualTo(Cinfmm.DISPATCH_FAILED);
        assertThat(notification.getReTryNot()).isEqualTo(1);
        assertThat(notification.getErrCone()).isEqualTo("EAI timeout");
        assertThat(notification.getSdDtm()).isNotNull();
    }

    @Test
    @DisplayName("5회 실패한 알림은 더 이상 재시도하지 않는다")
    void canRetry_exhausted_false() {
        Cinfmm notification = pending();
        for (int i = 0; i < 5; i++) {
            notification.markDispatchFailed("장애");
        }

        assertThat(notification.canRetry(5)).isFalse();
    }

    private Cinfmm pending() {
        return Cinfmm.builder()
                .infmMsgNo("INF-2026-00000001")
                .infmSdStsC(Cinfmm.DISPATCH_PENDING)
                .reTryNot(0)
                .build();
    }
}
