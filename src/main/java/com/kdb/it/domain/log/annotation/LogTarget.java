package com.kdb.it.domain.log.annotation;

import com.kdb.it.domain.log.entity.BaseLogEntity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CUD 변경 로그 대상 엔티티를 지정하는 마커 어노테이션.
 *
 * <p>이 어노테이션이 붙은 엔티티는 {@code @PostPersist} / {@code @PostUpdate} 시
 * {@link com.kdb.it.domain.log.listener.ChangeLogEntityListener}가
 * {@link #entity()}로 지정된 로그 엔티티에 변경 이력을 자동으로 INSERT한다.</p>
 *
 * <p>사용 예:</p>
 * <pre>{@code
 * @LogTarget(entity = BprojmL.class)
 * @Entity
 * @Table(name = "TAAABB_BPROJM")
 * public class Bprojm extends BaseEntity { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface LogTarget {

    /** 대응하는 로그 엔티티 클래스 */
    Class<? extends BaseLogEntity> entity();
}
