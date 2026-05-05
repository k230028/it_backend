package com.kdb.it.domain.log.listener;

import com.kdb.it.domain.log.entity.BaseLogEntity;
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

            setField(logEntity, "chgTp", chgTp);
            setField(logEntity, "chgDtm", LocalDateTime.now());
            setField(logEntity, "chgUsid", resolveCurrentUserId());

            copyColumnFields(sourceEntity, logEntity);

            entityManager.persist(logEntity);
        } catch (Exception e) {
            throw new RuntimeException("변경 로그 INSERT 실패: " + logClass.getSimpleName(), e);
        }
    }

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

    private String columnName(Field f) {
        String name = f.getAnnotation(Column.class).name();
        return name.isEmpty() ? f.getName() : name;
    }

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

    private String resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getName();
    }
}
