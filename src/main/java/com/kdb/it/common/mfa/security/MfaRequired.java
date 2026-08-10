package com.kdb.it.common.mfa.security;

import com.kdb.it.common.mfa.domain.MfaPurpose;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 실행 전에 현재 JWT 사용자에게 귀속된 1회용 MFA 증표 소비를 요구한다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MfaRequired {

    /** 보호할 명령의 MFA 사용 목적이다. */
    MfaPurpose purpose();
}
