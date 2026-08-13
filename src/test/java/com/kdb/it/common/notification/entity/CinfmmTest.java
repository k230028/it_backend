package com.kdb.it.common.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CinfmmTest {

    @Test
    @DisplayName("발송 성공은 SENT 상태와 발송 메타를 기록한다")
    void markDispatchSent_recordsSuccess() {
        Cinfmm notification = pending();

        notification.markDispatchSent("04", "payload");

        assertThat(notification.getInfmSdStsC()).isEqualTo(Cinfmm.DISPATCH_SENT);
        assertThat(notification.getItPtlSdTc()).isEqualTo("04");
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
    @DisplayName("한글이 섞인 긴 오류내용도 ERR_CONE 상한 바이트를 넘기지 않는다")
    void markDispatchFailed_clampsErrorMessageWithinColumnBytes() {
        Cinfmm notification = pending();
        String message =
                "전송 실패: I/O error on POST request for"
                        + " \"https://deaiaa11.kdb.co.kr:20014/eaicall\": (certificate_unknown) PKIX path"
                        + " building failed";

        notification.markDispatchFailed(message);

        String stored = notification.getErrCone();
        assertThat(stored.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(100);
        assertThat(message).startsWith(stored);
    }

    @Test
    @DisplayName("오류내용을 자를 때 다바이트 문자를 쪼개지 않는다")
    void markDispatchFailed_doesNotSplitMultiByteCharacter() {
        Cinfmm notification = pending();
        // 한글 34자 = UTF-8 102바이트 — 상한 100바이트 경계가 문자 중간에 걸린다.
        String message = "가".repeat(34);

        notification.markDispatchFailed(message);

        String stored = notification.getErrCone();
        assertThat(stored).isEqualTo("가".repeat(33));
        assertThat(stored.getBytes(StandardCharsets.UTF_8).length).isEqualTo(99);
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
