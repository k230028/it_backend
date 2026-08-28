package com.kdb.it.domain.entity;

import jakarta.persistence.EntityManager;

/** 같은 기본키의 논리 삭제 엔티티를 신규 INSERT 대신 복원하는 영속성 지원 함수입니다. */
public final class EntityRestoreSupport {

    /** 인스턴스 생성을 막습니다. */
    private EntityRestoreSupport() {}

    /**
     * 기본키로 관리 엔티티를 조회하고 존재하면 논리 삭제를 해제합니다.
     *
     * @param entityManager 현재 트랜잭션의 엔티티 관리자
     * @param entityType 조회할 엔티티 타입
     * @param primaryKey 엔티티 기본키 또는 복합키
     * @param <T> BaseEntity 하위 타입
     * @return 복원된 관리 엔티티, 행이 없으면 null
     */
    public static <T extends BaseEntity> T findAndRestore(
            EntityManager entityManager, Class<T> entityType, Object primaryKey) {
        T existing = entityManager.find(entityType, primaryKey);
        if (existing != null) {
            existing.restore();
        }
        return existing;
    }
}
