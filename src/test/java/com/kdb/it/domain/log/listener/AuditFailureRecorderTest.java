package com.kdb.it.domain.log.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AuditFailureRecorder 단위 테스트.
 *
 * <p>감사 실패를 제한된 태그의 Micrometer 카운터와 구조화 로그로 기록하고,
 * 실패 처리 중 재진입을 정적 ThreadLocal로 차단하며, 메트릭 기록 실패가
 * 호출자에게 전파되지 않는지 검증한다.</p>
 */
class AuditFailureRecorderTest {

    private SimpleMeterRegistry registry;
    private AuditFailureRecorder recorder;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new AuditFailureRecorder(registry);
    }

    @Test
    @DisplayName("record - 실패 카운터를 entity/chgTp/stage 태그로 1 증가한다")
    void record_실패카운터를제한된태그로증가한다() {
        recorder.record("Bprojm", "PRJ-1", "U", "afterCommit", new RuntimeException("DB 오류"));
        assertThat(registry.counter("audit.log.write.failure",
                "entity", "Bprojm", "chgTp", "U", "stage", "afterCommit").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("record - 실패 처리 중 재호출은 가드로 무시한다")
    void record_처리중재호출은무시한다() {
        MeterRegistry reentrantRegistry = mock(MeterRegistry.class);
        given(reentrantRegistry.counter(anyString(), any(String[].class))).willAnswer(invocation -> {
            assertThat(AuditFailureRecorder.isHandlingFailure()).isTrue();
            recorder.record("Bprojm", "PRJ-1", "U", "nested", new RuntimeException("재진입"));
            throw new IllegalStateException("메트릭 실패");
        });
        assertThatCode(() -> new AuditFailureRecorder(reentrantRegistry)
                .record("Bprojm", "PRJ-1", "U", "afterCommit", new RuntimeException("DB 오류")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("record - 완료 후 ThreadLocal 상태를 제거한다")
    void record_완료후ThreadLocal을제거한다() {
        recorder.record("Bprojm", "PRJ-1", "U", "afterCommit", new RuntimeException("DB 오류"));
        assertThat(AuditFailureRecorder.isHandlingFailure()).isFalse();
    }

    @Test
    @DisplayName("record - 메트릭 기록 실패가 예외로 전파되지 않는다")
    void record_메트릭실패가예외로전파되지않는다() {
        MeterRegistry broken = mock(MeterRegistry.class);
        given(broken.counter(anyString(), any(String[].class))).willThrow(new IllegalStateException("registry 오류"));
        assertThatCode(() -> new AuditFailureRecorder(broken)
                .record("Bprojm", "PRJ-1", "C", "schedule", new RuntimeException("원인")))
                .doesNotThrowAnyException();
    }
}
