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

    /**
     * JPA INSERT 전 로그 저장 진입점.
     *
     * <p>{@link LogTarget} 어노테이션이 없는 엔티티는 처리하지 않습니다.</p>
     *
     * @param entity INSERT 대상 엔티티 인스턴스
     */
    @PrePersist
    public void onPrePersist(Object entity) {
        if (entity.getClass().getAnnotation(LogTarget.class) == null) {
            return;
        }
        persistLog(entity, "C");
    }

    /**
     * JPA UPDATE 전 처리 진입점.
     *
     * <p>동일 flush 사이클 내 동일 인스턴스에 대한 중복 로그 방지 로직이 포함되어 있습니다.
     * {@link LogTarget} 어노테이션이 없는 엔티티는 처리하지 않습니다.</p>
     *
     * @param entity UPDATE 대상 엔티티 인스턴스
     */
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

    /**
     * JPA UPDATE 후 처리 진입점.
     *
     * <p>flush 사이클 완료 후 {@code inFlightEntities}에서 해당 인스턴스를 제거하여
     * 다음 flush 사이클에서 재기록이 가능하도록 정리합니다.</p>
     *
     * @param entity UPDATE가 완료된 엔티티 인스턴스
     */
    @PostUpdate
    public void onPostUpdate(Object entity) {
        inFlightEntities.get().remove(entity);
    }

    /**
     * 실제 감사 로그를 DB에 적재하는 내부 메서드.
     *
     * <p>{@link AuditLogPersister}를 통해 로그 엔티티를 생성하고 저장합니다.
     * 로그 저장 실패 시 예외를 삼켜 원본 트랜잭션 롤백을 방지합니다.</p>
     *
     * @param entity 로그 대상 엔티티
     * @param chgTp  변경 유형 코드 (C=생성, U=수정, D=논리삭제)
     */
    private void persistLog(Object entity, String chgTp) {
        LogTarget ann = entity.getClass().getAnnotation(LogTarget.class);
        Class<? extends BaseLogEntity> logClass = ann.entity();
        try {
            AuditLogPersister persister = ApplicationContextHolder.getBean(AuditLogPersister.class);
            persister.persist(entity, logClass, chgTp);
        } catch (Exception e) {
            // 감사로그 실패가 본 업무 트랜잭션을 롤백시키지 않도록 예외를 삼킨다.
            // 시퀀스 미생성(ORA-02289) 등 인프라 오류 시 본 작업은 정상 완료되어야 한다.
            // 단, 진단을 위해 스택트레이스(e)를 마지막 인자로 전달한다.
            log.warn("[감사로그 기록 실패] entity={}, logClass={}, chgTp={}",
                    entity.getClass().getSimpleName(), logClass.getSimpleName(), chgTp, e);
        }
    }

    /**
     * 엔티티의 {@code DEL_YN} 필드 값을 기반으로 변경 유형을 판별합니다.
     *
     * <p>리플렉션으로 {@code delYn} 필드를 조회합니다.
     * 필드가 없으면 수정(U)으로 간주하며, 접근 실패 시에도 수정(U)을 반환합니다.</p>
     *
     * @param entity 변경 유형을 판별할 엔티티
     * @return 변경 유형 코드 (D=논리삭제, U=수정)
     */
    private String resolveUpdateType(Object entity) {
        Field delYnField = findField(entity.getClass(), "delYn");
        if (delYnField == null) {
            return "U";
        }
        try {
            delYnField.setAccessible(true);
            return "Y".equals(delYnField.get(entity)) ? "D" : "U";
        } catch (IllegalAccessException e) {
            // delYn 리플렉션 실패 시 삭제 판정 불가 → 기본값 U로 폴백하되, 원인 예외를 warn으로 추적한다.
            log.warn("[감사로그] delYn 리플렉션 실패 — 변경유형 U로 폴백: entity={}",
                    entity.getClass().getSimpleName(), e);
            return "U";
        }
    }

    /**
     * 클래스 계층 구조를 탐색하여 지정된 이름의 필드를 찾습니다 (리플렉션).
     *
     * <p>현재 클래스부터 상위 클래스를 순서대로 탐색합니다.
     * 필드를 찾지 못하면 {@code null}을 반환합니다 (예외 미발생).</p>
     *
     * @param clazz 탐색 시작 클래스
     * @param name  찾을 필드명
     * @return 발견된 {@link Field}, 없으면 {@code null}
     */
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
