package com.kdb.it.common.system.validation;

import com.kdb.it.common.system.security.OwnershipVerifier;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/** 시스템관리자 여부에 따라 문자열 필수값 검증을 적용합니다. */
public final class NotBlankUnlessAdminValidator
        implements ConstraintValidator<NotBlankUnlessAdmin, String> {

    private Pattern pattern;
    private String patternMessage;

    @Override
    public void initialize(NotBlankUnlessAdmin annotation) {
        pattern = annotation.pattern().isBlank() ? null : Pattern.compile(annotation.pattern());
        patternMessage = annotation.patternMessage();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (OwnershipVerifier.isCurrentUserAdmin()) {
            return true;
        }
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        if (pattern != null && !pattern.matcher(value).matches()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            patternMessage.isBlank()
                                    ? context.getDefaultConstraintMessageTemplate()
                                    : patternMessage)
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
