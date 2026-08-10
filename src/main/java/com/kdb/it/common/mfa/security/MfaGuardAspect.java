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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 전자결재 명령 진입 전에 현재 사용자의 MFA 증표를 원자적으로 한 번 소비하는 공통 경계다. */
public class MfaGuardAspect implements HandlerInterceptor, WebMvcConfigurer {

    private final MfaService mfaService;
    private final boolean secureCookie;

    public MfaGuardAspect(
            MfaService mfaService, @Value("${app.cookie.secure:false}") boolean secureCookie) {
        this.mfaService = mfaService;
        this.secureCookie = secureCookie;
    }

    /** 현재 인스턴스를 전자결재 MFA 인터셉터로 등록한다. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    /**
     * 보호된 전자결재 명령보다 먼저 proof 쿠키와 JWT 사번을 검증하고 증표를 한 번 소비한다.
     *
     * @param request 현재 HTTP 요청
     * @param response 현재 HTTP 응답
     * @param handler 선택된 MVC 핸들러
     * @return 보호 대상이 아니거나 증표 소비가 끝나면 {@code true}
     * @throws MfaException 인증 사용자 또는 유효한 1회용 증표가 없는 경우
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        MfaRequired requirement = requirement(handler);
        if (requirement == null) {
            return true;
        }
        // 응답이 커밋되기 전에 삭제 헤더를 선등록해 이후 성공·예외 경로 모두에서 전달되게 한다.
        deleteProofCookie(response);
        if (requirement.purpose() != MfaPurpose.APPROVAL) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
        CustomUserDetails currentUser = currentUser();
        String proof = cookie(request, CookieUtil.MFA_PROOF_COOKIE);
        if (proof == null || proof.isBlank()) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
        mfaService.consumeApprovalProof(currentUser, proof);
        return true;
    }

    private static MfaRequired requirement(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }
        return AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), MfaRequired.class);
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
        ResponseCookie deleteCookie =
                ResponseCookie.from(CookieUtil.MFA_PROOF_COOKIE, "")
                        .httpOnly(true)
                        .secure(secureCookie)
                        .path("/")
                        .maxAge(0)
                        .sameSite("Lax")
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
    }
}
