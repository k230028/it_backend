package com.kdb.it.common.mfa.security;

import com.kdb.it.common.mfa.service.MfaService;
import com.kdb.it.common.util.CookieUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/** 운영 애플리케이션에 전자결재 MFA 공통 경계를 등록한다. */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class MfaGuardConfiguration {

    /**
     * MFA 서비스와 쿠키 보안 설정을 사용하는 전자결재 인터셉터를 생성한다.
     *
     * @param mfaService MFA 증표 1회 소비 서비스
     * @param secureCookie HTTPS 전용 쿠키 여부
     * @return 전자결재 MFA 인터셉터
     */
    @Bean
    MfaGuardAspect mfaGuardAspect(MfaService mfaService, CookieUtil cookieUtil) {
        return new MfaGuardAspect(mfaService, cookieUtil);
    }
}
