package com.kdb.it.domain.log.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;

import com.kdb.it.domain.log.entity.BaseLogEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AuditLogWriter 단위 테스트.
 *
 * <p>감사 INSERT를 별도 트랜잭션에서 persist 후 flush까지 완료해 지연 DB 오류를
 * 호출자에게 전파하는지 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogWriterTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AuditLogWriter writer;

    @Test
    @DisplayName("writeInNewTransaction - persist 후 flush 순으로 호출한다")
    void writeInNewTransaction_persist후flush한다() {
        SampleLogEntity logEntity = new SampleLogEntity();
        writer.writeInNewTransaction(logEntity);
        InOrder order = inOrder(entityManager);
        order.verify(entityManager).persist(logEntity);
        order.verify(entityManager).flush();
    }

    @Test
    @DisplayName("writeInNewTransaction - flush 실패를 호출자에게 전파한다")
    void writeInNewTransaction_flush실패를호출자에게전파한다() {
        SampleLogEntity logEntity = new SampleLogEntity();
        doThrow(new PersistenceException("지연 INSERT 실패")).when(entityManager).flush();
        assertThatThrownBy(() -> writer.writeInNewTransaction(logEntity))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("지연 INSERT 실패");
    }

    /** 테스트용 로그 엔티티(BaseLogEntity 최소 구현체). */
    static class SampleLogEntity extends BaseLogEntity {
        public SampleLogEntity() {
        }
    }
}
