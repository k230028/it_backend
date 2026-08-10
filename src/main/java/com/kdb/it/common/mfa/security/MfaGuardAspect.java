package com.kdb.it.common.mfa.security;

import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.exception.MfaErrorCode;
import com.kdb.it.common.mfa.exception.MfaException;
import com.kdb.it.common.mfa.service.MfaService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 컨트롤러 인자 바인딩과 검증이 끝난 뒤 전자결재 MFA 증표를 한 번 소비하는 공통 경계다. */
@Aspect
public class MfaGuardAspect {

    private final MfaService mfaService;
    private final CookieUtil cookieUtil;

    public MfaGuardAspect(MfaService mfaService, CookieUtil cookieUtil) {
        this.mfaService = mfaService;
        this.cookieUtil = cookieUtil;
    }

    /** 보호된 컨트롤러 명령의 실제 서비스 호출 직전에 증표를 검증하고 소비한다. */
    @Around("@annotation(requirement)")
    public Object consumeProof(ProceedingJoinPoint joinPoint, MfaRequired requirement)
            throws Throwable {
        ServletRequestAttributes attributes = currentServletRequest();
        deleteProofCookie(attributes.getResponse());
        if (requirement.purpose() != MfaPurpose.APPROVAL) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }

        CustomUserDetails currentUser = currentUser();
        String proof = cookie(attributes.getRequest(), CookieUtil.MFA_PROOF_COOKIE);
        if (proof == null || proof.isBlank()) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
        mfaService.consumeApprovalProof(currentUser, proof);
        return joinPoint.proceed();
    }

    private static ServletRequestAttributes currentServletRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes;
        }
        throw new MfaException(MfaErrorCode.MFA_REQUIRED);
    }

    private static CustomUserDetails currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails user) {
            return user;
        }
        throw new MfaException(MfaErrorCode.MFA_REQUIRED);
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void deleteProofCookie(HttpServletResponse response) {
        if (response != null) {
            response.addHeader(
                    HttpHeaders.SET_COOKIE, cookieUtil.deleteMfaProofCookie().toString());
        }
    }
}
