package com.kdb.it.domain.log.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.domain.log.entity.BaseLogEntity;
import jakarta.persistence.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * AuditLogPersister 단위 테스트.
 *
 * <p>스냅샷을 즉시 쓰지 않고 원 업무 커밋 이후(afterCommit)에 쓰며, 롤백 시 쓰지 않고, 쓰기 실패를 단계(afterCommit/direct)와 함께 실패
 * recorder로 위임하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogPersisterTest {

    @Mock private AuditLogWriter writer;
    @Mock private AuditFailureRecorder failureRecorder;
    @InjectMocks private AuditLogPersister persister;

    private final SampleEntity source = new SampleEntity();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("persist - 호출 시 즉시 쓰지 않고 afterCommit에서 쓴다")
    void persist_호출시즉시쓰지않고afterCommit에서쓴다() {
        TransactionSynchronizationManager.initSynchronization();
        persister.persist(source, SampleLogEntity.class, "U");
        verifyNoInteractions(writer);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(writer).writeInNewTransaction(any(SampleLogEntity.class));
    }

    @Test
    @DisplayName("persist - 원 업무 rollback이면 감사 쓰기를 실행하지 않는다")
    void persist_원업무rollback이면감사쓰기를실행하지않는다() {
        TransactionSynchronizationManager.initSynchronization();
        persister.persist(source, SampleLogEntity.class, "U");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(
                        sync ->
                                sync.afterCompletion(
                                        TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(writer);
    }

    @Test
    @DisplayName("persist - afterCommit 쓰기 실패를 recorder에 전달한다")
    void persist_afterCommit쓰기실패를recorder에전달한다() {
        doThrow(new RuntimeException("감사 INSERT 실패"))
                .when(writer)
                .writeInNewTransaction(any(BaseLogEntity.class));
        TransactionSynchronizationManager.initSynchronization();
        persister.persist(source, SampleLogEntity.class, "U");
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(failureRecorder)
                .record(eq("SampleEntity"), anyString(), eq("U"), eq("afterCommit"), any());
    }

    @Test
    @DisplayName("persist - 동기화 없는 직접 쓰기 실패는 direct 단계로 기록한다")
    void persist_동기화없는직접쓰기실패는direct단계로기록한다() {
        doThrow(new RuntimeException("감사 INSERT 실패"))
                .when(writer)
                .writeInNewTransaction(any(BaseLogEntity.class));
        persister.persist(source, SampleLogEntity.class, "U");
        verify(failureRecorder)
                .record(eq("SampleEntity"), anyString(), eq("U"), eq("direct"), any());
    }

    // ── 테스트 픽스처 ──

    /** 감사 대상 원본 엔티티(식별자 있음). */
    static class SampleEntity {
        @Id Long id = 1L;
    }

    /** 로그 엔티티(BaseLogEntity 최소 구현체). */
    static class SampleLogEntity extends BaseLogEntity {
        public SampleLogEntity() {}
    }
}
