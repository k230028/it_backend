package com.kdb.it.domain.log.listener;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BaseLogEntity;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.slf4j.LoggerFactory;

/**
 * JPA 엔티티 변경 이벤트 리스너.
 *
 * <p>{@link LogTarget}이 붙은 엔티티의 {@code @PrePersist} / {@code @PreUpdate} 이벤트를
 * 감지하여 {@link AuditLogPersister}로 로그 INSERT를 위임한다.</p>
 *
 * <p>JPA가 직접 인스턴스화하므로 Spring 빈이 아니며,
 * {@link ApplicationContextHolder}를 통해 {@link AuditLogPersister}를 조회한다.</p>
 *
 * <p>{@code @PostPersist}/{@code @PostUpdate} 대신 {@code @PrePersist}/{@code @PreUpdate}를
 * 사용하는 이유: Post 콜백은 Hibernate ActionQueue 이터레이션 도중 호출되므로
 * {@code entityManager.persist()}를 호출하면 {@link java.util.ConcurrentModificationException}이
 * 발생한다. Pre 콜백은 이터레이션 이전에 호출되므로 안전하다.</p>
 *
 * <p>동일 트랜잭션 내 AUTO flush → commit flush 이중 실행 방지:
 * {@code AuditingEntityListener}가 {@code @PreUpdate}에서 {@code lstChgDtm}을 변경하면
 * Hibernate가 dirty 재감지하여 {@code @PreUpdate}가 두 번 호출될 수 있다.
 * {@code inFlightEntities}(identity 기반 ThreadLocal Set)로 flush 사이클당 1회만 기록한다.</p>
 */
public class ChangeLogEntityListener {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ChangeLogEntityListener.class);

    /** flush 사이클 내 이미 로그를 기록한 엔티티 인스턴스 추적 (identity 비교) */
    private static final ThreadLocal<Set<Object>> inFlightEntities =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    @PrePersist
    public void onPrePersist(Object entity) {
        if (entity.getClass().getAnnotation(LogTarget.class) == null) {
            return;
        }
        persistLog(entity, "C");
    }

    @PreUpdate
    public void onPreUpdate(Object entity) {
        if (entity.getClass().getAnnotation(LogTarget.class) == null) {
            return;
        }
        // 동일 flush 사이클에서 이미 기록한 인스턴스면 중복 방지
        if (!inFlightEntities.get().add(entity)) {
            return;
        }
        // DEL_YN='Y'이면 논리삭제(D), 그 외 수정(U)
        String chgTp = resolveUpdateType(entity);
        persistLog(entity, chgTp);
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        inFlightEntities.get().remove(entity);
    }

    private void persistLog(Object entity, String chgTp) {
        LogTarget ann = entity.getClass().getAnnotation(LogTarget.class);
        Class<? extends BaseLogEntity> logClass = ann.entity();
        try {
            AuditLogPersister persister = ApplicationContextHolder.getBean(AuditLogPersister.class);
            persister.persist(entity, logClass, chgTp);
        } catch (Exception e) {
            // 감사로그 실패가 본 업무 트랜잭션을 롤백시키지 않도록 예외를 삼킨다.
            // 시퀀스 미생성(ORA-02289) 등 인프라 오류 시 본 작업은 정상 완료되어야 한다.
            log.warn("[감사로그 기록 실패] entity={}, logClass={}, chgTp={}, reason={}",
                    entity.getClass().getSimpleName(), logClass.getSimpleName(), chgTp, e.getMessage());
        }
    }

    private String resolveUpdateType(Object entity) {
        Field delYnField = findField(entity.getClass(), "delYn");
        if (delYnField == null) {
            return "U";
        }
        try {
            delYnField.setAccessible(true);
            return "Y".equals(delYnField.get(entity)) ? "D" : "U";
        } catch (IllegalAccessException e) {
            return "U";
        }
    }

    private Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
