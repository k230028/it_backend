package com.kdb.it.domain.log.listener;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 감사 실패 로그에 사용할 엔티티 식별자를 생성하는 utility.
 *
 * <p>단일 {@code @Id}, 다중 {@code @Id}, {@code @EmbeddedId}를 읽어 {@code field=value} 형태로 결합한다. PK가 아직
 * 할당되지 않았으면 {@code guid}로 대체하고, 둘 다 없으면 {@code <unavailable>}을 반환한다. 식별자는 로그 전용이며 Micrometer 태그에는
 * 사용하지 않는다.
 */
public final class AuditEntityIdentifier {

    private AuditEntityIdentifier() {}

    /**
     * 단일·다중·복합 PK를 읽고 아직 할당되지 않았으면 guid로 대체한다.
     *
     * @param sourceEntity 감사 대상 엔티티
     * @return 필드명=값 목록, 식별 불가 시 {@code <unavailable>}
     */
    public static String resolve(Object sourceEntity) {
        List<String> identifiers = new ArrayList<>();
        Class<?> type = sourceEntity.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)
                        || field.isAnnotationPresent(EmbeddedId.class)) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(sourceEntity);
                        if (value != null) {
                            if (field.isAnnotationPresent(EmbeddedId.class)) {
                                identifiers.addAll(describeEmbeddedId(value));
                            } else {
                                identifiers.add(field.getName() + "=" + value);
                            }
                        }
                    } catch (IllegalAccessException exception) {
                        identifiers.add(field.getName() + "=<unavailable>");
                    }
                }
            }
            type = type.getSuperclass();
        }
        if (!identifiers.isEmpty()) {
            return String.join(",", identifiers);
        }
        Object guid = readField(sourceEntity, "guid");
        return guid == null ? "<unavailable>" : "guid=" + guid;
    }

    private static List<String> describeEmbeddedId(Object embeddedId) {
        List<String> identifiers = new ArrayList<>();
        for (Field field : embeddedId.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                identifiers.add(field.getName() + "=" + field.get(embeddedId));
            } catch (IllegalAccessException exception) {
                identifiers.add(field.getName() + "=<unavailable>");
            }
        }
        return identifiers;
    }

    private static Object readField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            } catch (IllegalAccessException exception) {
                return null;
            }
        }
        return null;
    }
}
