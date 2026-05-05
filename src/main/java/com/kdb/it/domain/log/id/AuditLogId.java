package com.kdb.it.domain.log.id;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그 테이블 PK 생성 어노테이션.
 *
 * <p>{@link AuditLogIdGenerator}를 Hibernate 6+ 방식으로 등록한다.
 * {@code @GenericGenerator} 대신 이 어노테이션을 {@code @Id} 필드에 직접 붙인다.</p>
 */
@IdGeneratorType(AuditLogIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AuditLogId {
}
