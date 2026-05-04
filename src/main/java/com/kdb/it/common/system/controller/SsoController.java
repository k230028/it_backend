package com.kdb.it.common.system.controller;

import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * SSO 인증 완료 처리 컨트롤러
 *
 * <p>{@code agentProc.jsp}(JSP 직접 실행)가 SSO Agent 세션에서 인증된 사번을 읽어
 * {@code ssoVerifiedEno} 서버 세션 값으로 넘기면, JWT 쿠키를 발급하고 원래 프론트엔드
 * 경로로 복귀시킵니다.</p>
 *
 * <p>SSO 흐름 (JSP 파일이 직접 실행됨):</p>
 * <ol>
 *   <li>{@code /sso/business.jsp}: SSO 인증 진입점 → agentProc.jsp로 이동 (벤더 교체 대상)</li>
 *   <li>{@code /sso/agentProc.jsp}: 사번 추출 후 complete 호출 (벤더 교체 대상, 테스트 사번 설정 위치)</li>
 *   <li>{@code /api/auth/sso/complete}: JWT 발급 및 프론트엔드 복귀 (이 컨트롤러)</li>
 * </ol>
 */
@Controller
@RequiredArgsConstructor
public class SsoController {

    private static final String SSO_VERIFIED_ENO_SESSION_KEY = "ssoVerifiedEno";

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /**
     * SSO 완료 후 사용자를 돌려보낼 프론트엔드 기준 URL입니다.
     */
    @Value("${app.frontend-url:}")
    private String frontendUrl;

    /**
     * SSO 완료 후 복귀를 허용할 프론트엔드 origin 목록입니다.
     */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 테스트 편의용 사번 직접 전달 허용 여부입니다.
     *
     * <p>운영 기본값은 false입니다. true인 경우에만
     * {@code /api/auth/sso/complete?eno=...} 형태의 직접 발급을 허용합니다.</p>
     */
    @Value("${app.sso.allow-direct-eno:false}")
    private boolean allowDirectEno;

    /**
     * SSO 인증 완료 후 JWT 쿠키를 발급하고 프론트엔드로 복귀합니다.
     *
     * <p>{@code agentProc.jsp}가 인증된 사번을 서버 세션에 저장하면
     * 사용자·권한·부서 정보를 조회해 다음 세 쿠키를 발급합니다.</p>
     * <ul>
     *   <li>Access Token (httpOnly, 15분)</li>
     *   <li>Refresh Token (httpOnly, 7일)</li>
     *   <li>it-portal-user (Nuxt 화면 인증 상태 복원용)</li>
     * </ul>
     *
     * @param eno      로컬 테스트에서만 허용되는 사번 직접 전달 값
     * @param next     SSO 시작 전 요청했던 프론트엔드 내부 경로
     * @param origin   SSO 시작 시 프론트엔드가 전달한 origin
     * @param request  SSO Agent 검증 결과가 담긴 서버 세션을 읽기 위한 요청
     * @param response Set-Cookie 및 302 Location 헤더를 작성할 Servlet 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @GetMapping("/api/auth/sso/complete")
    public void complete(@RequestParam(value = "eno", required = false) String eno,
                         @RequestParam(value = "next", required = false) String next,
                         @RequestParam(value = "origin", required = false) String origin,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        try {
            String verifiedEno = resolveVerifiedEno(request, eno);
            AuthDto.LoginResponse loginResponse = authService.issueSsoTokens(verifiedEno);

            ResponseCookie accessCookie  = cookieUtil.createAccessTokenCookie(loginResponse.getAccessToken());
            ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(loginResponse.getRefreshToken());
            ResponseCookie userCookie    = cookieUtil.createUserInfoCookie(loginResponse);

            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, userCookie.toString());

            // 오픈 리다이렉트 방지: '/'로 시작하는 상대 경로만 허용
            String dest = (next != null && next.startsWith("/")) ? next : "/";
            response.sendRedirect(resolveFrontendBaseUrl(origin) + dest);
        } catch (Exception e) {
            response.sendRedirect(resolveFrontendBaseUrl(origin) + "/login?error=sso");
        }
    }

    private String resolveFrontendBaseUrl(String origin) {
        return getAllowedOrigin(origin).orElse(frontendUrl);
    }

    private Optional<String> getAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(allowed -> allowed.equals(origin))
                .findFirst();
    }

    private String resolveVerifiedEno(HttpServletRequest request, String directEno) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionEno = session.getAttribute(SSO_VERIFIED_ENO_SESSION_KEY);
            session.removeAttribute(SSO_VERIFIED_ENO_SESSION_KEY);
            if (sessionEno != null && !sessionEno.toString().isBlank()) {
                return sessionEno.toString();
            }
        }

        if (allowDirectEno && directEno != null && !directEno.isBlank()) {
            return directEno;
        }

        throw new IllegalStateException("SSO 인증 세션이 없습니다.");
    }
}
