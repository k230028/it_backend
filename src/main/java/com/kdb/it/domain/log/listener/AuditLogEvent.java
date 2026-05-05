package com.kdb.it.domain.log.listener;

import com.kdb.it.domain.log.entity.BaseLogEntity;

/**
 * 변경 로그 Spring 이벤트.
 *
 * <p>JPA {@code @PostPersist}/{@code @PostUpdate} 콜백에서 직접 {@code EntityManager.persist()}를
 * 호출하면 Hibernate ActionQueue 이터레이션 도중 {@link java.util.ConcurrentModificationException}이
 * 발생한다. 이 이벤트를 통해 로그 INSERT를 {@code @TransactionalEventListener(BEFORE_COMMIT)}으로
 * 위임하여 flush 완료 후 안전한 시점에 처리한다.</p>
 */
public class AuditLogEvent {

    private final Object sourceEntity;
    private final Class<? extends BaseLogEntity> logClass;
    private final String chgTp;

    public AuditLogEvent(Object sourceEntity, Class<? extends BaseLogEntity> logClass, String chgTp) {
        this.sourceEntity = sourceEntity;
        this.logClass = logClass;
        this.chgTp = chgTp;
    }

    public Object getSourceEntity() {
        return sourceEntity;
    }

    public Class<? extends BaseLogEntity> getLogClass() {
        return logClass;
    }

    public String getChgTp() {
        return chgTp;
    }
}
