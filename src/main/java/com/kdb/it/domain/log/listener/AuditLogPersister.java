package com.kdb.it.domain.log.listener;

import com.kdb.it.domain.log.entity.BaseLogEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 변경 로그 영속화 컴포넌트.
 *
 * <p>원본 엔티티의 {@code @Column} 필드를 리플렉션으로 복사하여
 * 대응하는 로그 엔티티를 현재 트랜잭션 내에 INSERT한다.</p>
 *
 * <p>{@link ChangeLogEntityListener}의 {@code @PrePersist}/{@code @PreUpdate} 콜백에서
 * 직접 호출된다. Pre 콜백은 Hibernate ActionQueue 이터레이션 이전에 실행되므로
 * {@code entityManager.persist()}를 안전하게 호출할 수 있다.</p>
 */
@Component
@Transactional
public class AuditLogPersister {

    private static final Logger log = LoggerFactory.getLogger(AuditLogPersister.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 변경 로그 INSERT.
     *
     * @param sourceEntity 원본 엔티티 (CUD 이벤트 발생 엔티티)
     * @param logClass     대응하는 로그 엔티티 클래스
     * @param chgTp        변경유형 ('C'=생성, 'U'=수정, 'D'=논리삭제)
     */
    public void persist(Object sourceEntity, Class<? extends BaseLogEntity> logClass, String chgTp) {
        try {
            var ctor = logClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            BaseLogEntity logEntity = (BaseLogEntity) ctor.newInstance();

            // 생성(C) 시점에는 JPA 콜백 순서상 BaseEntity.prePersist가 아직 실행되지 않아
            // 원본의 delYn/guid/guidPrgSno가 null이다. 스냅샷 복사 전에 동일 기본값을 미리
            // 채워, 로그 스냅샷이 *L NOT NULL 제약을 만족하고 원본과 같은 값을 갖게 한다(멱등).
            applyBaseAuditDefaults(sourceEntity);

            setField(logEntity, "chgTp", chgTp);
            setField(logEntity, "chgDtm", LocalDateTime.now());
            setField(logEntity, "chgUsid", resolveCurrentUserId());

            copyColumnFields(sourceEntity, logEntity);

            entityManager.persist(logEntity);
        } catch (Exception e) {
            throw new RuntimeException("변경 로그 INSERT 실패: " + logClass.getSimpleName(), e);
        }
    }

    /**
     * 원본 엔티티의 공통 감사 기본값(delYn/guid/guidPrgSno)을 보정합니다.
     *
     * <p>{@link com.kdb.it.domain.entity.BaseEntity#prePersist()}와 동일한 규칙이며,
     * 값이 null인 경우에만 채우므로 멱등합니다. 생성(C) 로그 스냅샷이 NOT NULL 제약을
     * 만족하고 원본 행과 동일한 GUID를 갖도록, prePersist보다 먼저(스냅샷 직전) 적용합니다.
     * 해당 필드가 없는 엔티티(비-BaseEntity 로그 대상)는 조용히 건너뜁니다.</p>
     *
     * @param source 원본 엔티티 (CUD 이벤트 발생 엔티티)
     */
    private void applyBaseAuditDefaults(Object source) {
        if (getFieldValue(source, "delYn") == null) {
            setFieldQuiet(source, "delYn", "N");
        }
        if (getFieldValue(source, "guid") == null) {
            setFieldQuiet(source, "guid", UUID.randomUUID().toString());
        }
        if (getFieldValue(source, "guidPrgSno") == null) {
            setFieldQuiet(source, "guidPrgSno", 1);
        }
    }

    /**
     * 리플렉션으로 필드 값을 읽습니다. 필드가 없거나 접근 실패 시 {@code null}을 반환합니다.
     *
     * @param target    대상 객체
     * @param fieldName 읽을 필드명
     * @return 필드 값, 없으면 {@code null}
     */
    private Object getFieldValue(Object target, String fieldName) {
        Field f = findField(target.getClass(), fieldName);
        if (f == null) {
            return null;
        }
        try {
            f.setAccessible(true);
            return f.get(target);
        } catch (IllegalAccessException e) {
            // TODO: 실제 null과 접근 실패를 구분할 수 있도록 필드명을 포함해 경고하거나 예외를 전파한다.
            return null;
        }
    }

    /**
     * 리플렉션으로 필드 값을 설정합니다. 필드가 없거나 접근 실패 시 조용히 무시합니다.
     *
     * @param target    대상 객체
     * @param fieldName 설정할 필드명
     * @param value     설정할 값
     */
    private void setFieldQuiet(Object target, String fieldName, Object value) {
        Field f = findField(target.getClass(), fieldName);
        if (f == null) {
            return;
        }
        try {
            f.setAccessible(true);
            f.set(target, value);
        } catch (IllegalAccessException ignored) {
            // TODO: 감사 필드 유실 원인을 추적할 수 있도록 필드명을 포함한 진단 정보를 남긴다.
        }
    }

    /**
     * 클래스 계층을 탐색하여 지정한 이름의 필드를 찾습니다. 없으면 {@code null}.
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

    /**
     * 원본 엔티티의 {@code @Column} 필드를 컬럼명 기준으로 로그 엔티티에 복사합니다.
     *
     * <p>상속 계층 전체({@link #collectColumnFields})를 탐색하므로
     * {@link com.kdb.it.domain.entity.BaseEntity}의 공통 컬럼도 복사됩니다.</p>
     *
     * @param source 원본 엔티티 (CUD 이벤트 발생 엔티티)
     * @param target 대응하는 로그 엔티티 (빈 인스턴스, 필드에 값 설정됨)
     * @throws IllegalAccessException 리플렉션 필드 접근 실패 시
     */
    private void copyColumnFields(Object source, BaseLogEntity target) throws IllegalAccessException {
        List<Field> sourceFields = collectColumnFields(source.getClass());
        List<Field> targetFields = collectColumnFields(target.getClass());

        for (Field sf : sourceFields) {
            String colName = columnName(sf);
            for (Field tf : targetFields) {
                if (colName.equalsIgnoreCase(columnName(tf))) {
                    sf.setAccessible(true);
                    tf.setAccessible(true);
                    tf.set(target, sf.get(source));
                    break;
                }
            }
        }
    }

    /**
     * {@code @Column} 어노테이션의 {@code name} 속성으로 컬럼명을 반환합니다.
     *
     * <p>{@code name}이 빈 문자열이면 JPA 기본값 규칙(필드명 그대로 사용)을 따릅니다.</p>
     *
     * @param f {@code @Column}이 붙은 필드
     * @return DB 컬럼명 (소문자/대문자 구분 없이 비교에 사용)
     */
    private String columnName(Field f) {
        String name = f.getAnnotation(Column.class).name();
        return name.isEmpty() ? f.getName() : name;
    }

    /**
     * 클래스 계층을 순회하며 {@code @Column}이 붙은 필드를 모두 수집합니다.
     *
     * <p>{@link Object}까지 순회하므로 엔티티 상속 구조에서도 공통 컬럼이 누락되지 않습니다.</p>
     *
     * @param clazz 대상 엔티티 클래스
     * @return {@code @Column} 어노테이션이 붙은 필드 목록 (선언 순서, 상위 클래스 포함)
     */
    private List<Field> collectColumnFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Column.class)) {
                    fields.add(f);
                }
            }
            c = c.getSuperclass();
        }
        return fields;
    }

    /**
     * 리플렉션으로 대상 객체의 특정 필드 값을 설정합니다.
     *
     * <p>클래스 계층 전체를 탐색하여 필드를 찾습니다.
     * 주로 {@link BaseLogEntity}의 {@code chgTp}, {@code chgDtm}, {@code chgUsid} 설정에 사용됩니다.</p>
     *
     * @param target    값을 설정할 대상 객체
     * @param fieldName 설정할 필드명
     * @param value     설정할 값
     * @throws Exception 필드를 찾지 못하거나 접근 권한이 없는 경우
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException("필드를 찾을 수 없음: " + fieldName);
    }

    /**
     * {@link org.springframework.security.core.context.SecurityContext}에서 현재 로그인 사용자 ID를 반환합니다.
     *
     * <p>인증 컨텍스트가 없거나 미인증 상태이면 {@code null}을 반환합니다.
     * 배치 또는 비인증 컨텍스트에서 호출되는 경우에도 안전하게 처리됩니다.</p>
     *
     * @return 현재 인증된 사용자의 사번, 미인증 시 {@code null}
     */
    private String resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            // 인증 컨텍스트가 없는 경로(시스템 트리거·비동기 등)에서는 CHG_USID가 null로 적재된다.
            // 동작은 유지하되, 변경자 추적 누락을 진단할 수 있도록 warn 로그를 남긴다.
            log.warn("[감사로그] 인증 컨텍스트 없음 — CHG_USID null로 기록됨");
            return null;
        }
        return auth.getName();
    }
}
