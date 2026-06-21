package com.kdb.it.common.sso;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * SSO 인증 처리 컨트롤러
 *
 * <p>SSO 진입({@code business})부터 콜백 검증({@code checkauth}), 토큰 발급({@code loginProc}
 * → {@code complete})까지 전 과정을 Spring 컨트롤러로 처리합니다(구 JSP Web Agent 대체).
 * 외부 ESSO 통신은 {@link SsoAgentClient}가 담당합니다.</p>
 *
 * <p>SSO 흐름:</p>
 * <ol>
 *   <li>{@code /sso/business}: SSO 인증 진입점. 실연동은 인증서버 점검 후 ESSO 로그인 페이지로,
 *       모의 모드({@code sso.mock-enabled=true})는 mock 사번을 세션에 주입해 {@code loginProc}로 이동.</li>
 *   <li>{@code /sso/checkauth}: ESSO 콜백. 토큰 검증 후 사번을 세션에 저장하고 {@code loginProc}로 이동.</li>
 *   <li>{@code /sso/loginProc}: SSO 세션 결과 검증 후 JWT 발급 단계로 연결</li>
 *   <li>{@code /api/auth/sso/complete}: JWT 발급 및 프론트엔드 복귀</li>
 *   <li>{@code /sso/logout}: 세션 무효화 후 ESSO 통합 로그아웃 페이지로 이동(실연동 시)</li>
 * </ol>
 */
@Controller
@RequiredArgsConstructor
public class SsoController {

    private static final Logger log = LoggerFactory.getLogger(SsoController.class);

    private static final String SSO_VERIFIED_ENO_SESSION_KEY = "ssoVerifiedEno";
    private static final String SSO_RESULT_CODE_SESSION_KEY = "resultCode";
    private static final String SSO_RESULT_DATA_SESSION_KEY = "resultData";
    private static final String SSO_SECURE_SESSION_ID_KEY = "secureSessionId";
    private static final String SSO_NEXT_SESSION_KEY = "ssoNext";
    private static final String SSO_ORIGIN_SESSION_KEY = "ssoOrigin";
    private static final String SSO_SUCCESS_CODE = "000000";

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final SsoAgentClient ssoAgentClient;
    private final SsoProperties ssoProperties;

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
     * SSO 인증 진입점입니다.
     *
     * <p>모의 모드({@code sso.mock-enabled=true})에서는 ESSO와 통신하지 않고 {@code sso.mock-eno}
     * 사번을 세션에 주입해 바로 {@code loginProc}로 이동합니다(SSO 통신 불가한 외부망 개발용).</p>
     *
     * <p>실연동 모드에서는 인증서버 통신을 점검하고 정상이면 ESSO 로그인 페이지로 이동합니다.
     * 통신 실패 시 수동 로그인 화면으로 폴백합니다. {@code next}/{@code origin}은 SSO 왕복 후
     * 원래 경로로 복귀하기 위해 세션에 보관합니다.</p>
     *
     * @param next     SSO 완료 후 복귀할 프론트엔드 내부 경로
     * @param origin   SSO 시작 시 프론트엔드가 전달한 origin
     * @param request  세션 보관 및 생성을 위한 요청
     * @param response 리다이렉트 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @GetMapping("/sso/business")
    public void business(@RequestParam(value = "next", required = false) String next,
                         @RequestParam(value = "origin", required = false) String origin,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(true);
        if (next != null && !next.isBlank()) {
            session.setAttribute(SSO_NEXT_SESSION_KEY, next);
        }
        if (origin != null && !origin.isBlank()) {
            session.setAttribute(SSO_ORIGIN_SESSION_KEY, origin);
        }

        // 모의 모드: ESSO 통신 없이 mock 사번을 세션에 주입 (외부망 개발용)
        if (ssoProperties.mockEnabled()) {
            session.setAttribute(SSO_RESULT_CODE_SESSION_KEY, SSO_SUCCESS_CODE);
            session.setAttribute(SSO_RESULT_DATA_SESSION_KEY, ssoProperties.mockEno());
            response.sendRedirect("/sso/loginProc");
            return;
        }

        // 실연동: 인증서버 통신 점검 후 ESSO 로그인 페이지로 이동. 실패 시 수동 로그인 폴백.
        if (!ssoAgentClient.isServerAlive()) {
            log.warn("SSO 인증서버 통신 실패 - 수동 로그인으로 폴백");
            response.sendRedirect(resolveFrontendBaseUrl(origin) + "/login?error=sso");
            return;
        }
        response.sendRedirect(ssoProperties.loginPageUrl() + "?agentId=" + encode(ssoProperties.agentId()));
    }

    /**
     * ESSO 콜백 처리입니다 ({@code checkauth}).
     *
     * <p>ESSO 로그인 성공 시 인증서버가 {@code secureToken} 등과 함께 호출합니다. 토큰을 검증해
     * 사번을 세션에 저장하고 {@code loginProc}로 이동합니다. 검증 실패·비정상 호출은 수동 로그인
     * 또는 진입점으로 폴백합니다. ESSO가 GET/POST 중 무엇으로 콜백할지 환경에 따라 다르므로 둘 다 허용합니다.</p>
     *
     * @param resultCode      ESSO가 전달한 1차 결과 코드
     * @param secureToken     토큰 검증에 사용할 보안 토큰
     * @param secureSessionId ESSO 보안 세션 ID (로그아웃 연계용)
     * @param request         클라이언트 IP 추출 및 세션 저장을 위한 요청
     * @param response        리다이렉트 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @RequestMapping(value = "/sso/checkauth", method = {RequestMethod.GET, RequestMethod.POST})
    public void checkauth(@RequestParam(value = "resultCode", required = false) String resultCode,
                          @RequestParam(value = "secureToken", required = false) String secureToken,
                          @RequestParam(value = "secureSessionId", required = false) String secureSessionId,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        // 비정상 호출(토큰 없음/1차 코드 실패)은 진입점으로 복귀
        if (!SSO_SUCCESS_CODE.equals(resultCode) || secureToken == null || secureToken.isBlank()) {
            log.warn("SSO checkauth 비정상 호출 - resultCode: {}", resultCode);
            response.sendRedirect("/sso/business");
            return;
        }

        SsoAgentClient.TokenAuthResult result =
                ssoAgentClient.authorize(secureToken, secureSessionId, request.getRemoteAddr());

        HttpSession session = request.getSession(true);
        session.setAttribute(SSO_RESULT_CODE_SESSION_KEY, result.resultCode());
        session.setAttribute(SSO_RESULT_DATA_SESSION_KEY, result.resultData());
        session.setAttribute(SSO_SECURE_SESSION_ID_KEY, secureSessionId);

        if (SSO_SUCCESS_CODE.equals(result.resultCode())) {
            // CS 모드는 토큰 저장 페이지를 경유한 뒤 다시 콜백됩니다.
            if (result.useCSMode()) {
                response.sendRedirect(ssoProperties.saveTokenUrl());
            } else {
                response.sendRedirect("/sso/loginProc");
            }
            return;
        }

        // 검증 실패(권한 실패 310017/310012 포함) → 수동 로그인 폴백
        log.warn("SSO 토큰 검증 실패 - resultCode: {}", result.resultCode());
        String origin = readSessionString(session, SSO_ORIGIN_SESSION_KEY);
        response.sendRedirect(resolveFrontendBaseUrl(origin.isBlank() ? null : origin) + "/login?error=sso");
    }

    /**
     * SSO 통합 로그아웃입니다.
     *
     * <p>업무 세션을 무효화한 뒤, 실연동 모드에서는 ESSO 통합 로그아웃 페이지로 이동합니다.
     * 모의 모드이거나 ESSO URL이 비어 있으면 프론트 로그인 화면으로 복귀합니다.
     * JWT 쿠키 제거는 별도의 {@code /api/auth/logout}에서 수행합니다.</p>
     *
     * @param request  세션 무효화를 위한 요청
     * @param response 리다이렉트 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @GetMapping("/sso/logout")
    public void ssoLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // 이미 무효화된 세션 — 무시
            }
        }

        if (ssoProperties.mockEnabled() || ssoProperties.browserBaseUrl().isBlank()) {
            response.sendRedirect(frontendUrl + "/login");
            return;
        }
        response.sendRedirect(ssoProperties.logoutPageUrl());
    }

    /**
     * SSO 세션 결과({@code resultCode}/{@code resultData})를 읽어 토큰 발급 단계로 연결합니다.
     *
     * <p>{@code business}(모의) 또는 {@code checkauth}(실연동)가 세션에 남긴 인증 결과를 검증해
     * {@code ssoVerifiedEno}로 승격한 뒤 {@code /api/auth/sso/complete}로 이동합니다.</p>
     *
     * @param response JWT 발급 완료 엔드포인트로 이동시키는 Servlet 응답
     * @param request  SSO 세션 결과를 읽기 위한 요청
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
     * <p>{@code loginProc}가 검증된 사번({@code ssoVerifiedEno})을 세션에 남기면
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
            log.error("SSO 인증 실패 - eno: {}, reason: {}", eno, e.getMessage(), e);
            // 오류 리다이렉트 자체도 IOException(클라이언트 연결 종료 등)을 던질 수 있으므로
            // 별도 try-catch로 감싸 2차 예외가 핸들러 밖으로 전파되지 않게 한다.
            try {
                response.sendRedirect(resolveFrontendBaseUrl(origin) + "/login?error=sso");
            } catch (IOException redirectEx) {
                log.warn("SSO 오류 리다이렉트 실패 - eno: {}", eno, redirectEx);
            }
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
