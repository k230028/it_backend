package com.kdb.it.common.system.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** 시스템관리자만 비워 두거나 지정된 형식 검증을 생략할 수 있는 필드에 적용합니다. */
@Documented
@Constraint(validatedBy = NotBlankUnlessAdminValidator.class)
@Target({FIELD, METHOD, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface NotBlankUnlessAdmin {

    String message() default "필수 입력값입니다.";

    /** 비관리자에게 적용할 선택적 정규식입니다. */
    String pattern() default "";

    /** pattern 불일치 시 사용할 메시지입니다. 비어 있으면 기본 메시지를 사용합니다. */
    String patternMessage() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
