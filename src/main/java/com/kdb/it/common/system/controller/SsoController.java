package com.kdb.it.common.system.controller;

import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>{@code agentProc.jsp}(JSP 직접 실행)가 SSO Agent 세션에 남긴 인증 결과를
 * {@code /sso/loginProc}로 넘기면, JWT 쿠키를 발급하고 원래 프론트엔드 경로로 복귀시킵니다.</p>
 *
 * <p>SSO 흐름 (JSP 파일이 직접 실행됨):</p>
 * <ol>
 *   <li>{@code /sso/business.jsp}: SSO 인증 진입점 → agentProc.jsp로 이동 (벤더 교체 대상)</li>
 *   <li>{@code /sso/agentProc.jsp}: 성공 시 {@code loginProc}로 이동 (벤더 파일 최소 수정)</li>
 *   <li>{@code /sso/loginProc}: SSO 세션 결과 검증 후 JWT 발급 단계로 연결</li>
 *   <li>{@code /api/auth/sso/complete}: JWT 발급 및 프론트엔드 복귀</li>
 * </ol>
 */
@Controller
@RequiredArgsConstructor
public class SsoController {

    private static final Logger log = LoggerFactory.getLogger(SsoController.class);

    private static final String SSO_VERIFIED_ENO_SESSION_KEY = "ssoVerifiedEno";
    private static final String SSO_RESULT_CODE_SESSION_KEY = "resultCode";
    private static final String SSO_RESULT_DATA_SESSION_KEY = "resultData";
    private static final String SSO_NEXT_SESSION_KEY = "ssoNext";
    private static final String SSO_ORIGIN_SESSION_KEY = "ssoOrigin";
    private static final String SSO_SUCCESS_CODE = "000000";

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
     * 벤더 {@code agentProc.jsp}의 성공 후처리 URL입니다.
     *
     * <p>실제 JSP에서는 기존 TODO의 {@code response.sendRedirect("loginProc"); return;}만
     * 활성화하면 이 메서드가 SSO 세션의 {@code resultCode/resultData}를 읽어 애플리케이션
     * 토큰 발급 단계로 연결합니다.</p>
     *
     * @param response JWT 발급 완료 엔드포인트로 이동시키는 Servlet 응답
     * @param request  SSO Agent가 남긴 세션 결과를 읽기 위한 요청
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @GetMapping("/sso/loginProc")
    public void loginProc(HttpServletResponse response,
                          HttpServletRequest request) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.sendRedirect(buildCompleteRedirect(null, null));
            return;
        }

        String resultCode = readSessionString(session, SSO_RESULT_CODE_SESSION_KEY);
        String resultData = readSessionString(session, SSO_RESULT_DATA_SESSION_KEY);
        String next = readSessionString(session, SSO_NEXT_SESSION_KEY);
        String origin = readSessionString(session, SSO_ORIGIN_SESSION_KEY);

        if (SSO_SUCCESS_CODE.equals(resultCode) && !resultData.isBlank()) {
            session.setAttribute(SSO_VERIFIED_ENO_SESSION_KEY, resultData);
        }

        response.sendRedirect(buildCompleteRedirect(next, origin));
    }

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
            // TODO: [B-H-06] sendRedirect() IOException을 내부 try-catch로 감싸고 에링 로그 추가 필요
            log.error("SSO 인증 실패 - eno: {}, reason: {}", eno, e.getMessage(), e);
            response.sendRedirect(resolveFrontendBaseUrl(origin) + "/login?error=sso");
        }
    }

    /**
     * 허용된 origin 기반으로 프론트엔드 기준 URL을 결정합니다.
     *
     * <p>{@code cors.allowed-origins}에 포함된 origin이면 해당 origin을,
     * 그렇지 않으면 {@code app.frontend-url} 기본값을 반환합니다.</p>
     *
     * @param origin SSO 시작 시 프론트엔드가 전달한 origin
     * @return 리다이렉트 대상 프론트엔드 기준 URL
     */
    private String resolveFrontendBaseUrl(String origin) {
        return getAllowedOrigin(origin).orElse(frontendUrl);
    }

    /**
     * origin이 허용 목록({@code cors.allowed-origins})에 포함되어 있으면 해당 값을 반환합니다.
     *
     * <p>오픈 리다이렉트 방지를 위해 허용 목록에 없는 origin은 {@link Optional#empty()}를 반환합니다.</p>
     *
     * @param origin 검증할 origin 문자열
     * @return 허용된 origin (없으면 {@link Optional#empty()})
     */
    private Optional<String> getAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(allowed -> allowed.equals(origin))
                .findFirst();
    }

    /**
     * {@code /api/auth/sso/complete}로의 리다이렉트 URL을 조립합니다.
     *
     * <p>{@code next}와 {@code origin} 파라미터를 URL 인코딩하여 쿼리 스트링으로 추가합니다.</p>
     *
     * @param next   SSO 완료 후 복귀할 프론트엔드 내부 경로 (null이면 생략)
     * @param origin 프론트엔드 origin (null이면 생략)
     * @return 완성된 리다이렉트 URL 문자열
     */
    private String buildCompleteRedirect(String next, String origin) {
        StringBuilder redirect = new StringBuilder("/api/auth/sso/complete");
        String sep = "?";
        if (next != null && !next.isBlank()) {
            redirect.append(sep).append("next=").append(encode(next));
            sep = "&";
        }
        if (origin != null && !origin.isBlank()) {
            redirect.append(sep).append("origin=").append(encode(origin));
        }
        return redirect.toString();
    }

    /**
     * 문자열을 URL 안전 형식으로 인코딩합니다 (UTF-8 기반 퍼센트 인코딩).
     *
     * @param value 인코딩할 원본 문자열
     * @return URL 인코딩된 문자열
     */
    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 세션에서 지정 키의 값을 문자열로 반환합니다.
     *
     * @param session HTTP 세션
     * @param key     세션 속성 키
     * @return 세션 값 (없거나 null이면 빈 문자열 반환)
     */
    private String readSessionString(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        return value == null ? "" : value.toString();
    }

    /**
     * SSO 인증 완료된 사번을 결정합니다.
     *
     * <p>우선순위: 세션({@code ssoVerifiedEno}) → {@code allowDirectEno=true}이고 {@code directEno} 지정 시.
     * 세션 값은 사용 즉시 제거합니다(재사용 방지).</p>
     *
     * @param request    SSO Agent 세션이 담긴 서블릿 요청
     * @param directEno  로컬 테스트용 직접 전달 사번 (운영 환경에서는 무시됨)
     * @return 검증된 사번
     * @throws IllegalStateException 유효한 SSO 세션이 없고 직접 전달도 허용되지 않는 경우
     */
    private String resolveVerifiedEno(HttpServletRequest request, String directEno) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionEno = session.getAttribute(SSO_VERIFIED_ENO_SESSION_KEY);
            session.removeAttribute(SSO_VERIFIED_ENO_SESSION_KEY);
            session.removeAttribute(SSO_NEXT_SESSION_KEY);
            session.removeAttribute(SSO_ORIGIN_SESSION_KEY);
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
