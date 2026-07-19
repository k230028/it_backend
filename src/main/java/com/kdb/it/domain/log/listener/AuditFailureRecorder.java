package com.kdb.it.domain.log.listener;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 감사로그 저장 실패를 지속 탐지 가능한 형태로 기록하는 컴포넌트.
 *
 * <p>실패 시 Micrometer 카운터({@code audit.log.write.failure})를 제한된 태그(entity/chgTp/stage)로
 * 증가시키고 ERROR 로그를 남긴다. 메트릭 기록 실패가 감사 실패 로깅을 막지 않도록 내부 try로 격리한다.</p>
 *
 * <p>정적 ThreadLocal 플래그로 실패 처리 중 재진입을 차단해, 향후 catch 내부에서
 * 감사 대상을 다시 건드려도 재귀·연쇄 실패가 발생하지 않게 한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditFailureRecorder {

    /** 감사 실패 처리 중 재진입 차단 플래그(스레드 단위). */
    private static final ThreadLocal<Boolean> HANDLING_FAILURE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final MeterRegistry meterRegistry;

    /** 현재 스레드가 감사 실패를 처리 중인지 여부. */
    public static boolean isHandlingFailure() {
        return Boolean.TRUE.equals(HANDLING_FAILURE.get());
    }

    /**
     * 감사 실패를 카운터·구조화 로그로 기록한다.
     *
     * <p>이미 실패 처리 중이면(재진입) 즉시 반환한다. 메트릭 기록 실패는 삼켜 로깅을 보장하고,
     * 완료 후 ThreadLocal 상태를 제거한다.</p>
     *
     * @param entityName 감사 대상 엔티티 종류명
     * @param entityId   엔티티 식별자(로그 전용, 메트릭 태그에는 사용하지 않음)
     * @param chgTp      변경 유형(C/U/D)
     * @param stage      실패 단계(direct/afterCommit/schedule)
     * @param cause      실패 원인 예외
     */
    public void record(String entityName, String entityId, String chgTp,
            String stage, Exception cause) {
        if (isHandlingFailure()) {
            return;
        }
        HANDLING_FAILURE.set(Boolean.TRUE);
        try {
            try {
                meterRegistry.counter("audit.log.write.failure",
                        "entity", entityName,
                        "chgTp", chgTp,
                        "stage", stage).increment();
            } catch (Exception metricException) {
                log.warn("[감사로그] 실패 메트릭 기록 실패: entity={}, chgTp={}, stage={}",
                        entityName, chgTp, stage, metricException);
            }
            log.error("[감사로그 기록 실패] entity={}, entityId={}, chgTp={}, stage={}",
                    entityName, entityId, chgTp, stage, cause);
        } finally {
            HANDLING_FAILURE.remove();
        }
    }
}
