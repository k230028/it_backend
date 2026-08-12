package com.kdb.it.common.sso;

import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * SSO 인증 처리 컨트롤러
 *
 * <p>SSO 진입({@code business})부터 콜백 검증({@code checkauth}), 토큰 발급({@code loginProc} → {@code
 * complete})까지 전 과정을 Spring 컨트롤러로 처리합니다(구 JSP Web Agent 대체). 외부 ESSO 통신은 {@link SsoAgentClient}가
 * 담당합니다.
 *
 * <p>SSO 흐름:
 *
 * <ol>
 *   <li>{@code /sso/business}: SSO 인증 진입점. 실연동은 인증서버 점검 후 ESSO 로그인 페이지로, 모의 모드({@code
 *       sso.mock-enabled=true})는 mock 사번을 세션에 주입해 {@code loginProc}로 이동.
 *   <li>{@code /sso/checkauth}: ESSO 콜백. 토큰 검증 후 사번을 세션에 저장하고 {@code loginProc}로 이동. CS 모드는 {@code
 *       saveToken.html} 저장 후 서버 등록 완료 URL({@code agentProc})로 복귀.
 *   <li>{@code /sso/loginProc} / {@code /sso/agentProc}: SSO 세션 결과 검증 후 JWT 발급 단계로 연결 ({@code
 *       agentProc}는 레퍼런스 SA-WEB 완료 페이지명과 정합되는 별칭).
 *   <li>{@code /api/auth/sso/complete}: JWT 발급 및 프론트엔드 복귀
 *   <li>{@code POST /sso/logout}: 세션 무효화 후 ESSO 통합 로그아웃 페이지로 이동(실연동 시)
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

    /**
     * CS 모드 saveToken POST 브리지 페이지의 자동 제출 인라인 스크립트 본문.
     *
     * <p>{@code SecurityConfig}가 이 값의 sha256을 계산해 CSP {@code script-src}에 등록하므로, 이 스크립트만 인라인 실행이
     * 허용됩니다. 본 상수를 변경하면 CSP 해시도 자동으로 따라갑니다.
     */
    public static final String CS_MODE_SUBMIT_SCRIPT = "document.forms[0].submit()";

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final SsoAgentClient ssoAgentClient;
    private final SsoProperties ssoProperties;

    /** SSO 완료 후 사용자를 돌려보낼 프론트엔드 기준 URL입니다. */
    @Value("${app.frontend-url:}")
    private String frontendUrl;

    /** SSO 완료 후 복귀를 허용할 프론트엔드 origin 목록입니다. */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 테스트 편의용 사번 직접 전달 허용 여부입니다.
     *
     * <p>운영 기본값은 false입니다. true인 경우에만 {@code /api/auth/sso/complete?eno=...} 형태의 직접 발급을 허용합니다.
     */
    @Value("${app.sso.allow-direct-eno:false}")
    private boolean allowDirectEno;

    /**
     * SSO 인증 진입점입니다.
     *
     * <p>모의 모드({@code sso.mock-enabled=true})에서는 ESSO와 통신하지 않고 {@code sso.mock-eno} 사번을 세션에 주입해 바로
     * {@code loginProc}로 이동합니다(SSO 통신 불가한 외부망 개발용).
     *
     * <p>실연동 모드에서는 인증서버 통신을 점검하고 정상이면 ESSO 로그인 페이지로 이동합니다. 통신 실패 시 수동 로그인 화면으로 폴백합니다. {@code
     * next}/{@code origin}은 SSO 왕복 후 원래 경로로 복귀하기 위해 세션에 보관합니다.
     *
     * @param next SSO 완료 후 복귀할 프론트엔드 내부 경로
     * @param origin SSO 시작 시 프론트엔드가 전달한 origin
     * @param request 세션 보관 및 생성을 위한 요청
     * @param response 리다이렉트 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @GetMapping("/sso/business")
    public void business(
            @RequestParam(value = "next", required = false) String next,
            @RequestParam(value = "origin", required = false) String origin,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(true);
        session.removeAttribute(SSO_NEXT_SESSION_KEY);
        session.removeAttribute(SSO_ORIGIN_SESSION_KEY);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.deleteSsoNextCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.deleteSsoOriginCookie().toString());

        String safeNext = SsoNextPathValidator.safePath(next).orElse(null);
        if (safeNext != null) {
            session.setAttribute(SSO_NEXT_SESSION_KEY, safeNext);
        }
        if (origin != null && !origin.isBlank()) {
            session.setAttribute(SSO_ORIGIN_SESSION_KEY, origin);
        }

        // 세션과 별개로 next/origin을 쿠키에도 보관한다. ESSO 교차 출처 왕복(특히 CS 모드 POST 콜백)
        // 중에는 서버 세션이 끊겨(checkauth가 새 세션 생성) next/origin이 유실될 수 있으나, 이 쿠키는
        // 마지막 same-site complete 내비게이션에 전달되어 원본 요청 URL을 복원하게 한다.
        if (safeNext != null) {
            response.addHeader(
                    HttpHeaders.SET_COOKIE, cookieUtil.createSsoNextCookie(safeNext).toString());
        }
        if (origin != null && !origin.isBlank()) {
            response.addHeader(
                    HttpHeaders.SET_COOKIE, cookieUtil.createSsoOriginCookie(origin).toString());
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
        response.sendRedirect(
                ssoProperties.loginPageUrl() + "?agentId=" + encode(ssoProperties.agentId()));
    }

    /**
     * ESSO 콜백 처리입니다 ({@code checkauth}).
     *
     * <p>ESSO 로그인 성공 시 인증서버가 {@code secureToken} 등과 함께 호출합니다. 토큰을 검증해 사번을 세션에 저장하고 {@code
     * loginProc}로 이동합니다. 검증 실패·비정상 호출은 프론트 수동 로그인({@code /login?error=sso})으로 폴백합니다(SSO 재시작 시
     * checkauth↔business 무한 루프를 피하기 위해 진입점으로 되돌리지 않습니다). ESSO가 GET/POST 중 무엇으로 콜백할지 환경에 따라 다르므로 둘 다
     * 허용합니다.
     *
     * @param resultCode ESSO가 전달한 1차 결과 코드
     * @param secureToken 토큰 검증에 사용할 보안 토큰
     * @param secureSessionId ESSO 보안 세션 ID (로그아웃 연계용)
     * @param request 클라이언트 IP 추출 및 세션 저장을 위한 요청
     * @param response 리다이렉트 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @RequestMapping(
            value = "/sso/checkauth",
            method = {RequestMethod.GET, RequestMethod.POST})
    public void checkauth(
            @RequestParam(value = "resultCode", required = false) String resultCode,
            @RequestParam(value = "secureToken", required = false) String secureToken,
            @RequestParam(value = "secureSessionId", required = false) String secureSessionId,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        // 비정상 호출(토큰 없음/1차 코드 실패)은 SSO를 재시작(/sso/business)하지 않고 프론트 수동
        // 로그인으로 보낸다. /sso/business로 되돌리면 ESSO가 다시 resultCode 없이 checkauth를 호출하는
        // 환경에서 checkauth↔business 무한 루프가 발생하므로(로그: "checkauth 비정상 호출 - resultCode: null"
        // 반복), 진입점 재시작 대신 루프를 끊는다. origin은 SSO 시작 시 심은 쿠키에서 복원한다.
        if (!SSO_SUCCESS_CODE.equals(resultCode) || secureToken == null || secureToken.isBlank()) {
            String origin = readCookie(request, CookieUtil.SSO_ORIGIN_COOKIE);
            log.warn(
                    "SSO checkauth 비정상 호출 - resultCode: {} → 수동 로그인으로 폴백(루프 차단)",
                    SsoLogSanitizer.resultCode(resultCode));
            response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.deleteSsoNextCookie().toString());
            response.addHeader(
                    HttpHeaders.SET_COOKIE, cookieUtil.deleteSsoOriginCookie().toString());
            response.sendRedirect(resolveFrontendBaseUrl(origin) + "/login?error=sso");
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
                // saveToken.html은 GET 리다이렉트가 아니라 agentId/errCode/secureSessionId를 담은
                // POST(폼 전송)로 호출해야 합니다(레퍼런스 checkauth.jsp의 auto-submit form과 동일).
                // GET으로 보내면 토큰 저장 페이지가 필요한 페이로드를 받지 못해 CS 모드 저장이 실패합니다.
                log.info(
                        "SSO checkauth 성공(CS 모드) - 토큰 저장 페이지로 POST: {}",
                        ssoProperties.saveTokenUrl());
                writeAutoSubmitPostForm(
                        response,
                        ssoProperties.saveTokenUrl(),
                        result.resultCode(),
                        secureSessionId);
            } else {
                log.info(
                        "SSO checkauth 성공 - loginProc로 이동 (resultData 보유: {})",
                        !result.resultData().isBlank());
                response.sendRedirect("/sso/loginProc");
            }
            return;
        }

        // 검증 실패(권한 실패 310017/310012 포함) → 수동 로그인 폴백.
        log.warn(
                "SSO 토큰 검증 실패 - resultCode: {}, 프록시 헤더 존재: {}",
                SsoLogSanitizer.resultCode(result.resultCode()),
                request.getHeader("X-Forwarded-For") != null);
        String origin = readSessionString(session, SSO_ORIGIN_SESSION_KEY);
        response.sendRedirect(
                resolveFrontendBaseUrl(origin.isBlank() ? null : origin) + "/login?error=sso");
    }

    /**
     * SSO 통합 로그아웃입니다.
     *
     * <p>POST 요청에서만 업무 세션을 무효화한 뒤, 실연동 모드에서는 ESSO 통합 로그아웃 페이지로 이동합니다. 모의 모드이거나 ESSO URL이 비어 있으면 프론트
     * 로그인 화면으로 복귀합니다. JWT 쿠키 제거는 별도의 {@code /api/auth/logout}에서 수행합니다.
     *
     * @param request 세션 무효화를 위한 요청
     * @param response 리다이렉트 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @PostMapping("/sso/logout")
    public void ssoLogout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        invalidateSession(request);

        if (ssoProperties.mockEnabled() || ssoProperties.browserBaseUrl().isBlank()) {
            response.sendRedirect(frontendUrl + "/login");
            return;
        }
        response.sendRedirect(ssoProperties.logoutPageUrl());
    }

    /**
     * SSO 세션 결과({@code resultCode}/{@code resultData})를 읽어 토큰 발급 단계로 연결합니다.
     *
     * <p>{@code business}(모의) 또는 {@code checkauth}(실연동)가 세션에 남긴 인증 결과를 검증해 {@code ssoVerifiedEno}로
     * 승격한 뒤 {@code /api/auth/sso/complete}로 이동합니다.
     *
     * @param response JWT 발급 완료 엔드포인트로 이동시키는 Servlet 응답
     * @param request SSO 세션 결과를 읽기 위한 요청
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @GetMapping("/sso/loginProc")
    public void loginProc(HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        proceedToComplete(request, response, "loginProc");
    }

    /**
     * ISign+ 인증 완료 콜백 별칭입니다 ({@code agentProc}).
     *
     * <p>레퍼런스 Web Agent(SA-WEB)의 완료 페이지가 {@code agentProc.jsp}이고, ISign+ 서버는 agent 등록값(returnUrl)으로
     * 이 경로를 호출합니다. CS 모드는 {@code saveToken.html} 저장 후 등록된 완료 URL({@code .../sso/agentProc})로 복귀하므로,
     * 서버 등록 경로 규약과 정합되도록 {@code /sso/loginProc}와 동일하게 처리합니다. ESSO가 GET/POST 중 무엇으로 호출할지 환경에 따라 다르므로
     * 둘 다 허용합니다.
     *
     * <p>주의: 완료 URL의 <b>호스트</b>(예: {@code uac.kdb.co.kr:20443})는 ISign+ 서버의 agent 등록값으로, 애플리케이션이
     * 보내는 값이 아닙니다. 실제 배포 호스트와 다르면 SSO 관리자가 agent 등록 URL을 수정해야 합니다.
     *
     * @param request SSO 세션 결과를 읽기 위한 요청
     * @param response JWT 발급 완료 엔드포인트로 이동시키는 Servlet 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @RequestMapping(
            value = "/sso/agentProc",
            method = {RequestMethod.GET, RequestMethod.POST})
    public void agentProc(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        proceedToComplete(request, response, "agentProc");
    }

    /**
     * 인증 결과 세션을 검증해 {@code ssoVerifiedEno}로 승격하고 {@code complete}로 이동합니다. {@code loginProc}/{@code
     * agentProc} 공통 처리입니다.
     *
     * @param request SSO 세션 결과를 읽기 위한 요청
     * @param response 리다이렉트 응답
     * @param stage 로그 식별용 호출 단계명({@code loginProc}/{@code agentProc})
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    private void proceedToComplete(
            HttpServletRequest request, HttpServletResponse response, String stage)
            throws IOException {
        HttpSession session = getActiveSession(request);
        if (session == null) {
            log.info("SSO {} - 세션 없음, complete 기본 경로로 이동", stage);
            response.sendRedirect(buildCompleteRedirect(null, null));
            return;
        }

        String resultCode;
        String resultData;
        String next;
        String origin;
        boolean verified;
        try {
            synchronized (session) {
                resultCode = readSessionString(session, SSO_RESULT_CODE_SESSION_KEY);
                resultData = readSessionString(session, SSO_RESULT_DATA_SESSION_KEY);
                next = readSessionString(session, SSO_NEXT_SESSION_KEY);
                origin = readSessionString(session, SSO_ORIGIN_SESSION_KEY);
                verified = SSO_SUCCESS_CODE.equals(resultCode) && !resultData.isBlank();
                try {
                    if (verified) {
                        request.changeSessionId();
                        session.setAttribute(SSO_VERIFIED_ENO_SESSION_KEY, resultData);
                    }
                } finally {
                    clearSsoResultState(session);
                }
            }
        } catch (IllegalStateException ignored) {
            log.info("SSO {} - 유효한 세션 없음, complete 기본 경로로 이동", stage);
            response.sendRedirect(buildCompleteRedirect(null, null));
            return;
        }

        log.debug(
                "SSO {} - resultCode: {}, ssoVerifiedEno 설정: {}, complete로 이동 (복귀 경로 존재: {}, origin"
                        + " 존재: {})",
                stage,
                SsoLogSanitizer.resultCode(resultCode),
                verified,
                !next.isBlank(),
                !origin.isBlank());
        response.sendRedirect(buildCompleteRedirect(next, origin));
    }

    /**
     * SSO 인증 완료 후 JWT 쿠키를 발급하고 프론트엔드로 복귀합니다.
     *
     * <p>{@code loginProc}가 검증된 사번({@code ssoVerifiedEno})을 세션에 남기면 사용자·권한·부서 정보를 조회해 다음 세 쿠키를
     * 발급합니다.
     *
     * <ul>
     *   <li>Access Token (httpOnly, 15분)
     *   <li>Refresh Token (httpOnly, 7일)
     *   <li>it-portal-user (Nuxt 화면 인증 상태 복원용)
     * </ul>
     *
     * @param eno 로컬 테스트에서만 허용되는 사번 직접 전달 값
     * @param next SSO 시작 전 요청했던 프론트엔드 내부 경로
     * @param origin SSO 시작 시 프론트엔드가 전달한 origin
     * @param request SSO Agent 검증 결과가 담긴 서버 세션을 읽기 위한 요청
     * @param response Set-Cookie 및 302 Location 헤더를 작성할 Servlet 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @GetMapping("/api/auth/sso/complete")
    public void complete(
            @RequestParam(value = "eno", required = false) String eno,
            @RequestParam(value = "next", required = false) String next,
            @RequestParam(value = "origin", required = false) String origin,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        String effectiveNext = next;
        String effectiveOrigin = origin;
        try {
            // 쿼리 파라미터가 생략된 상태만 쿠키에서 복원해 명시적인 빈 값도 우선순위를 유지한다.
            if (effectiveNext == null) {
                effectiveNext = readCookie(request, CookieUtil.SSO_NEXT_COOKIE);
            }
            if (effectiveOrigin == null) {
                effectiveOrigin = readCookie(request, CookieUtil.SSO_ORIGIN_COOKIE);
            }

            String verifiedEno = resolveVerifiedEno(request, eno);
            AuthDto.LoginResponse loginResponse = authService.issueSsoTokens(verifiedEno);

            ResponseCookie accessCookie =
                    cookieUtil.createAccessTokenCookie(loginResponse.getAccessToken());
            ResponseCookie refreshCookie =
                    cookieUtil.createRefreshTokenCookie(loginResponse.getRefreshToken());
            ResponseCookie userCookie = cookieUtil.createUserInfoCookie(loginResponse);

            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, userCookie.toString());
            // SSO 복귀 상태 쿠키는 1회용이므로 사용 후 제거한다.
            response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.deleteSsoNextCookie().toString());
            response.addHeader(
                    HttpHeaders.SET_COOKIE, cookieUtil.deleteSsoOriginCookie().toString());

            String dest = SsoNextPathValidator.safePathOrRoot(effectiveNext);
            String target = resolveFrontendBaseUrl(effectiveOrigin) + dest;
            // 복귀 대상이 비어 보이면(app.frontend-url/origin 미설정) 백엔드 자신으로 가 401이 난다.
            log.debug(
                    "SSO 인증 완료 - eno: {}, 토큰 쿠키 발급, 사용자 지정 복귀 경로: {}",
                    SsoLogSanitizer.masked(verifiedEno),
                    !"/".equals(dest));
            response.sendRedirect(target);
        } catch (Exception e) {
            log.error(
                    "SSO 인증 실패 - eno: {}, 오류 유형: {}",
                    SsoLogSanitizer.masked(eno),
                    SsoLogSanitizer.exceptionType(e));
            // 오류 리다이렉트 자체도 IOException(클라이언트 연결 종료 등)을 던질 수 있으므로
            // 별도 try-catch로 감싸 2차 예외가 핸들러 밖으로 전파되지 않게 한다.
            try {
                response.addHeader(
                        HttpHeaders.SET_COOKIE, cookieUtil.deleteSsoNextCookie().toString());
                response.addHeader(
                        HttpHeaders.SET_COOKIE, cookieUtil.deleteSsoOriginCookie().toString());
                response.sendRedirect(resolveFrontendBaseUrl(effectiveOrigin) + "/login?error=sso");
            } catch (IOException redirectEx) {
                log.warn(
                        "SSO 오류 리다이렉트 실패 - eno: {}, 오류 유형: {}",
                        SsoLogSanitizer.masked(eno),
                        SsoLogSanitizer.exceptionType(redirectEx));
            }
        } finally {
            invalidateSession(request);
        }
    }

    /**
     * 허용된 origin 기반으로 프론트엔드 기준 URL을 결정합니다.
     *
     * <p>{@code cors.allowed-origins}에 포함된 origin이면 해당 origin을, 그렇지 않으면 {@code app.frontend-url}
     * 기본값을 반환합니다.
     *
     * @param origin SSO 시작 시 프론트엔드가 전달한 origin
     * @return 리다이렉트 대상 프론트엔드 기준 URL
     */
    private String resolveFrontendBaseUrl(String origin) {
        // 복귀 URL 조립(문자열 연결)이 전부 이 반환값에 의존하므로 널이 아님을 여기서 확정한다.
        // @Value 기본값이 빈 문자열이라 frontendUrl은 실제로 널이 되지 않지만, 프로퍼티가
        // 명시적으로 비워진 경우까지 포함해 계약을 한 지점에 못박는다.
        return getAllowedOrigin(origin)
                .orElseGet(() -> Objects.requireNonNullElse(frontendUrl, ""));
    }

    /**
     * origin이 허용 목록({@code cors.allowed-origins})에 포함되어 있으면 해당 값을 반환합니다.
     *
     * <p>오픈 리다이렉트 방지를 위해 허용 목록에 없는 origin은 {@link Optional#empty()}를 반환합니다.
     *
     * @param origin 검증할 origin 문자열
     * @return 허용된 origin (없으면 {@link Optional#empty()})
     */
    private Optional<String> getAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(value -> value.trim())
                .filter(allowed -> allowed.equals(origin))
                .findFirst();
    }

    /**
     * {@code /api/auth/sso/complete}로의 리다이렉트 URL을 조립합니다.
     *
     * <p>{@code next}와 {@code origin} 파라미터를 URL 인코딩하여 쿼리 스트링으로 추가합니다.
     *
     * @param next SSO 완료 후 복귀할 프론트엔드 내부 경로 (null이면 생략)
     * @param origin 프론트엔드 origin (null이면 생략)
     * @return 완성된 리다이렉트 URL 문자열
     */
    private String buildCompleteRedirect(String next, String origin) {
        StringBuilder redirect = new StringBuilder("/api/auth/sso/complete");
        String sep = "?";
        Optional<String> safeNext = SsoNextPathValidator.safePath(next);
        if (safeNext.isPresent()) {
            redirect.append(sep).append("next=").append(encode(safeNext.get()));
            sep = "&";
        }
        if (origin != null && !origin.isBlank()) {
            redirect.append(sep).append("origin=").append(encode(origin));
        }
        return redirect.toString();
    }

    /**
     * CS 모드 토큰 저장 페이지로 POST 전송하는 자동 제출 HTML 폼을 응답에 씁니다.
     *
     * <p>ISign+ {@code saveToken.html}은 {@code agentId}/{@code errCode}/{@code secureSessionId}를
     * POST(폼 전송)로 받아야 합니다(레퍼런스 {@code checkauth.jsp}의 auto-submit form과 동일). GET 리다이렉트로는 페이로드가 전달되지
     * 않아 CS 모드 토큰 저장이 실패합니다.
     *
     * <p>인라인 제출 스크립트는 {@code SecurityConfig}의 CSP {@code script-src} sha256 해시로만 허용되므로 본문은 정확히
     * {@code document.forms[0].submit()}이어야 합니다(변경 시 해시도 갱신). 사용자 입력값({@code secureSessionId})은 속성
     * 컨텍스트로 이스케이프해 XSS를 방지합니다.
     *
     * @param response 응답
     * @param actionUrl saveToken.html URL
     * @param resultCode 1차 결과 코드(errCode로 전송)
     * @param secureSessionId ESSO 보안 세션 ID
     * @throws IOException 응답 작성 실패 시
     */
    private void writeAutoSubmitPostForm(
            HttpServletResponse response,
            String actionUrl,
            String resultCode,
            String secureSessionId)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String html =
                "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body>"
                        + "<form name=\"sendForm\" method=\"post\" action=\""
                        + attr(actionUrl)
                        + "\">"
                        + "<input type=\"hidden\" name=\"agentId\" value=\""
                        + attr(ssoProperties.agentId())
                        + "\"/>"
                        + "<input type=\"hidden\" name=\"errCode\" value=\""
                        + attr(resultCode)
                        + "\"/>"
                        + "<input type=\"hidden\" name=\"secureSessionId\" value=\""
                        + attr(secureSessionId == null ? "" : secureSessionId)
                        + "\"/>"
                        + "</form>"
                        + "<script>"
                        + CS_MODE_SUBMIT_SCRIPT
                        + "</script>"
                        + "</body></html>";
        response.getWriter().write(html);
    }

    /**
     * HTML 속성값 컨텍스트로 이스케이프합니다(XSS 방지).
     *
     * @param value 원본 값
     * @return 이스케이프된 값 (null이면 빈 문자열)
     */
    private static String attr(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
     * @param key 세션 속성 키
     * @return 세션 값 (없거나 null이면 빈 문자열 반환)
     */
    private String readSessionString(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        return value == null ? "" : value.toString();
    }

    /**
     * 요청 쿠키에서 지정 이름의 값을 URL 디코딩해 반환합니다.
     *
     * @param request 요청
     * @param name 쿠키 이름
     * @return 디코딩된 쿠키 값 (없으면 빈 문자열)
     */
    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value == null) {
                    return "";
                }
                try {
                    return URLDecoder.decode(value, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ignored) {
                    return "";
                }
            }
        }
        return "";
    }

    /**
     * SSO 인증 완료된 사번을 결정합니다.
     *
     * <p>우선순위: 세션({@code ssoVerifiedEno}) → {@code allowDirectEno=true}이고 {@code directEno} 지정 시.
     * 세션 값은 사용 즉시 제거합니다(재사용 방지).
     *
     * @param request SSO Agent 세션이 담긴 서블릿 요청
     * @param directEno 로컬 테스트용 직접 전달 사번 (운영 환경에서는 무시됨)
     * @return 검증된 사번
     * @throws IllegalStateException 유효한 SSO 세션이 없고 직접 전달도 허용되지 않는 경우
     */
    private String resolveVerifiedEno(HttpServletRequest request, String directEno) {
        HttpSession session = getActiveSession(request);
        if (session != null) {
            try {
                synchronized (session) {
                    Object sessionEno = session.getAttribute(SSO_VERIFIED_ENO_SESSION_KEY);
                    session.removeAttribute(SSO_VERIFIED_ENO_SESSION_KEY);
                    session.removeAttribute(SSO_NEXT_SESSION_KEY);
                    session.removeAttribute(SSO_ORIGIN_SESSION_KEY);
                    if (sessionEno != null && !sessionEno.toString().isBlank()) {
                        return sessionEno.toString();
                    }
                }
            } catch (IllegalStateException e) {
                // 다른 요청이 먼저 세션을 무효화한 경우 직접 전달 허용 여부를 이어서 검사한다.
                log.trace("SSO 검증 세션이 이미 무효화되어 직접 전달 경로로 넘어갑니다.", e);
            }
        }

        if (allowDirectEno && directEno != null && !directEno.isBlank()) {
            return directEno;
        }

        throw new IllegalStateException("SSO 인증 세션이 없습니다.");
    }

    /** SSO Agent가 남긴 인증 결과와 세션 식별자를 한 번의 소비 시도 후 제거합니다. */
    private static void clearSsoResultState(HttpSession session) {
        session.removeAttribute(SSO_RESULT_CODE_SESSION_KEY);
        session.removeAttribute(SSO_RESULT_DATA_SESSION_KEY);
        session.removeAttribute(SSO_SECURE_SESSION_ID_KEY);
    }

    /** 이미 무효화된 세션을 제외하고 현재 요청의 활성 세션만 반환합니다. */
    private static HttpSession getActiveSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        try {
            session.getCreationTime();
            return session;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    /** 현재 요청의 세션을 새로 만들지 않고 안전하게 무효화합니다. */
    private static void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        try {
            synchronized (session) {
                session.invalidate();
            }
        } catch (IllegalStateException e) {
            // 이미 무효화된 세션은 추가 처리가 필요하지 않다.
            log.trace("이미 무효화된 세션이라 SSO 세션 무효화를 건너뜁니다.", e);
        }
    }
}
