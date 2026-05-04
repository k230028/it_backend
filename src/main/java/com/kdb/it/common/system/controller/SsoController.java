package com.kdb.it.common.system.controller;

import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * SSO 인증 브리지 컨트롤러
 *
 * <p>프론트엔드가 미인증 사용자를 {@code /sso/business.jsp?next=...}로 보내면,
 * 테스트 환경에서는 이 컨트롤러가 실제 SSO JSP의 리다이렉트 흐름을 흉내 냅니다.
 * 최종적으로 {@code /api/auth/sso/complete}에서 JWT 쿠키를 발급하고 사용자가 처음
 * 요청했던 프론트엔드 경로로 돌려보냅니다.</p>
 *
 * <p>테스트 흐름은 다음 순서로 동작합니다.</p>
 * <ol>
 *   <li>{@code business.jsp}: SSO 인증 시작 지점. 원본 경로 {@code next}를 유지한 채 agentProc으로 이동</li>
 *   <li>{@code agentProc.jsp}: SSO 인증 성공 결과를 의미. 테스트 사번 {@code K140024}를 complete로 전달</li>
 *   <li>{@code complete}: 사번으로 사용자와 권한을 조회하고 Access/Refresh/User 쿠키 발급</li>
 *   <li>프론트엔드 {@code app.frontend-url + next}로 302 리다이렉트</li>
 * </ol>
 *
 * <p>운영 전환 시에는 {@code eno} 쿼리 파라미터를 그대로 신뢰하면 안 됩니다.
 * 벤더 SSO Agent가 제공하는 서명 검증, 세션 검증, 사용자 식별 API로 인증 결과를 확인한 뒤
 * 이 브리지의 토큰 발급 단계만 재사용해야 합니다.</p>
 */
@Controller
@RequiredArgsConstructor
public class SsoController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /**
     * SSO 완료 후 사용자를 돌려보낼 프론트엔드 기준 URL입니다.
     *
     * 개발 환경에서는 {@code http://localhost:3000}처럼 백엔드와 프론트엔드 origin이 다르므로
     * 절대 URL이 필요합니다. 운영에서 같은 origin 또는 프록시 뒤에 배포한다면 빈 값으로 두어
     * {@code /info/projects/...} 같은 상대 경로 리다이렉트를 사용할 수 있습니다.
     */
    @Value("${app.frontend-url:}")
    private String frontendUrl;

    /**
     * SSO 완료 후 복귀를 허용할 프론트엔드 origin 목록입니다.
     *
     * {@code cors.allowed-origins}와 같은 값을 사용해 개발/운영 프론트엔드 주소를 한곳에서
     * 관리합니다. 정적 generate 산출물을 http://localhost 같은 별도 origin에서 서빙하면
     * 해당 origin도 이 목록에 포함되어야 SSO 완료 후 3000 포트로 튀지 않습니다.
     */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    // ── 테스트용 JSP 시뮬레이션 ────────────────────────────────────────────
    // bootRun 내장 Tomcat은 src/main/webapp/ JSP를 서블릿으로 처리하지 않으므로
    // 컨트롤러로 동일한 리다이렉트 흐름을 구현합니다.
    // 실제 SSO 연동(WAR 배포) 시 벤더 JSP 파일이 이 경로를 대체합니다.

    /**
     * 테스트용 SSO 진입점입니다.
     *
     * <p>실제 SSO 제품의 {@code business.jsp}는 사용자 브라우저를 SSO 서버로 보내거나
     * 이미 인증된 SSO 세션을 확인하는 역할을 합니다. 로컬 bootRun 환경에서는 JSP Agent를
     * 직접 실행하지 않기 때문에 컨트롤러가 같은 URL을 받아 다음 단계인 agentProc으로
     * 즉시 리다이렉트합니다.</p>
     *
     * <p>{@code next}는 사용자가 처음 요청한 프론트엔드 내부 경로입니다. 이 값을 잃어버리면
     * SSO 완료 후 항상 홈으로 가게 되므로 agentProc과 complete까지 계속 전달합니다.</p>
     *
     * @param next     SSO 완료 후 복원할 프론트엔드 내부 경로
     * @param response 302 Location 헤더를 내려보낼 Servlet 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @GetMapping("/sso/business.jsp")
    public void businessJsp(@RequestParam(value = "next", required = false) String next,
                            @RequestParam(value = "origin", required = false) String origin,
                            HttpServletResponse response) throws IOException {
        String redirect = UriComponentsBuilder.fromPath("/sso/agentProc.jsp")
                .queryParamIfPresent("next", java.util.Optional.ofNullable(next))
                .queryParamIfPresent("origin", Optional.ofNullable(origin))
                .toUriString();
        response.sendRedirect(redirect);
    }

    /**
     * 테스트용 SSO 인증 결과 콜백입니다.
     *
     * <p>실제 SSO의 {@code agentProc.jsp}는 벤더 Agent가 SSO 인증 결과를 검증하고,
     * 성공 시 사번 같은 사용자 식별자를 애플리케이션에 넘기는 단계입니다. 현재 로컬 테스트는
     * 고정 사번 {@code K140024}를 사용해 "SSO 인증 성공" 상태를 재현합니다.</p>
     *
     * <p>이 메서드는 쿠키를 직접 발급하지 않습니다. 인증 결과(테스트 사번)와 원본 경로(next)를
     * {@code /api/auth/sso/complete}로 넘겨 실제 애플리케이션 JWT 발급 책임을 한곳에 모읍니다.</p>
     *
     * @param next     SSO 시작 시 전달받은 원본 프론트엔드 경로
     * @param response complete 엔드포인트로 이동시키는 Servlet 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @GetMapping("/sso/agentProc.jsp")
    public void agentProcJsp(@RequestParam(value = "next", required = false) String next,
                             @RequestParam(value = "origin", required = false) String origin,
                             HttpServletResponse response) throws IOException {
        String redirect = UriComponentsBuilder.fromPath("/api/auth/sso/complete")
                .queryParam("eno", "K140024")
                .queryParamIfPresent("next", java.util.Optional.ofNullable(next))
                .queryParamIfPresent("origin", Optional.ofNullable(origin))
                .toUriString();
        response.sendRedirect(redirect);
    }

    // ─────────────────────────────────────────────────────────────────────

    /**
     * SSO 인증 완료 후 애플리케이션 JWT 쿠키를 발급하고 프론트엔드로 복귀시킵니다.
     *
     * <p>이 메서드는 SSO 브리지의 마지막 단계입니다. agentProc에서 넘어온 사번으로
     * 사용자, 권한, 부서 정보를 조회하고 다음 세 쿠키를 내려보냅니다.</p>
     * <ul>
     *   <li>Access Token 쿠키: API 인증에 사용하는 짧은 수명의 httpOnly JWT</li>
     *   <li>Refresh Token 쿠키: Access Token 재발급에 사용하는 httpOnly 토큰</li>
     *   <li>{@code it-portal-user} 쿠키: Nuxt가 화면 인증 상태를 복원하기 위해 읽는 사용자 정보 쿠키</li>
     * </ul>
     *
     * <p>{@code next}는 반드시 같은 사이트 내부 경로만 허용합니다. 외부 URL을 그대로 허용하면
     * SSO 완료 후 악성 사이트로 이동하는 오픈 리다이렉트가 될 수 있으므로 '/'로 시작하는 값만
     * 사용하고, 값이 없거나 안전하지 않으면 홈('/')으로 보냅니다.</p>
     *
     * <p>토큰 발급 중 사용자 미존재, DB 오류, 쿠키 생성 오류가 발생하면 SSO 실패로 간주하고
     * {@code /login?error=sso}로 이동시켜 수동 로그인 fallback을 보여줍니다.</p>
     *
     * @param eno      SSO 인증 결과로 전달받은 사번
     * @param next     SSO 시작 전 사용자가 요청했던 프론트엔드 내부 경로
     * @param response Set-Cookie 헤더와 302 Location 헤더를 작성할 Servlet 응답
     * @throws IOException 리다이렉트 응답 작성 실패 시
     */
    @GetMapping("/api/auth/sso/complete")
    public void complete(@RequestParam("eno") String eno,
                         @RequestParam(value = "next", required = false) String next,
                         @RequestParam(value = "origin", required = false) String origin,
                         HttpServletResponse response) throws IOException {
        try {
            AuthDto.LoginResponse loginResponse = authService.issueSsoTokens(eno);

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

    /**
     * SSO 완료 후 사용할 프론트엔드 기준 URL을 결정합니다.
     *
     * <p>우선순위는 {@code origin} 쿼리 파라미터가 가장 높습니다. generate 산출물을
     * {@code http://localhost}에서 서빙하는 경우 브라우저가 이 origin을 SSO 시작 시 넘기고,
     * 백엔드는 허용 목록에 있는지 확인한 뒤 그 origin으로 복귀시킵니다.</p>
     *
     * <p>허용되지 않은 origin이면 기존 {@code app.frontend-url}로 fallback합니다.
     * 운영에서 상대 경로 복귀를 쓰려면 {@code app.frontend-url}을 빈 값으로 둘 수 있습니다.</p>
     *
     * @param origin SSO 시작 시 프론트엔드가 전달한 현재 origin
     * @return 검증된 프론트엔드 기준 URL 또는 설정 fallback
     */
    private String resolveFrontendBaseUrl(String origin) {
        return getAllowedOrigin(origin).orElse(frontendUrl);
    }

    /**
     * 전달받은 origin이 설정된 허용 목록에 포함되는지 확인합니다.
     *
     * @param origin 검증할 origin
     * @return 허용된 origin이면 해당 값, 아니면 empty
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
}
