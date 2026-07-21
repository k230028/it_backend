package com.kdb.it.domain.log.listener;

import com.kdb.it.domain.log.entity.BaseLogEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사로그 INSERT를 원 업무와 분리된 별도 트랜잭션에서 수행하는 컴포넌트.
 *
 * <p>{@code REQUIRES_NEW}로 독립 트랜잭션을 시작하고 {@code flush()}까지 강제해, 커밋 지연 시점의 DB 오류를 호출자(실패 recorder)가
 * 포착할 수 있게 한다.
 */
@Component
public class AuditLogWriter {

    @PersistenceContext private EntityManager entityManager;

    /**
     * 감사 로그 엔티티를 별도 트랜잭션에서 저장한다.
     *
     * @param logEntity 저장할 감사 로그 엔티티
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTransaction(BaseLogEntity logEntity) {
        entityManager.persist(logEntity);
        // INSERT 오류를 이 트랜잭션 경계 안에서 확정해 실패 recorder가 포착하도록 강제한다.
        entityManager.flush();
    }
}
