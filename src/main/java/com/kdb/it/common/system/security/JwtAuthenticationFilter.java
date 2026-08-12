package com.kdb.it.common.system.security;

import com.kdb.it.common.util.CookieUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 인증 필터
 *
 * <p>HTTP 요청마다 JWT Access Token을 추출하고 검증하여 Spring Security의 SecurityContext에 인증 정보를 설정합니다.
 *
 * <p>{@link OncePerRequestFilter}를 상속하여 요청당 정확히 한 번만 실행되도록 보장합니다.
 *
 * <p>토큰 추출 우선순위:
 *
 * <ol>
 *   <li>{@code accessToken} httpOnly 쿠키에서 추출 (브라우저 기본 방식)
 *   <li>{@code Authorization: Bearer {token}} 헤더에서 추출 (API 테스트 도구 호환 폴백)
 * </ol>
 *
 * <p>필터 처리 흐름:
 *
 * <pre>
 *   HTTP 요청 수신
 *     ↓
 *   쿠키 또는 Authorization 헤더에서 JWT 토큰 추출
 *     ↓
 *   JwtUtil.validateToken(jwt, TOKEN_USE_ACCESS, true) → 서명·만료·Access 용도(allowlist) 검증
 *     ↓ (유효한 경우)
 *   JwtUtil.getEnoFromToken() → 사번 추출
 *     ↓
 *   CustomUserDetails 생성 (JWT 클레임 직접 사용, DB 재조회 없음)
 *     ↓
 *   UsernamePasswordAuthenticationToken 생성
 *     ↓
 *   SecurityContextHolder에 인증 객체 설정
 *     ↓
 *   다음 필터 체인으로 전달
 * </pre>
 *
 * <p>실행 위치: {@code SecurityConfig}에서 {@link
 * org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter} 앞에 삽입됩니다.
 */
@Component // Spring 컴포넌트 빈으로 등록 (자동 감지)
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 토큰 생성/검증 유틸리티 */
    private final JwtUtil jwtUtil;

    /** Authorization: Bearer 헤더 폴백 허용 여부 (운영 기본 false, dev/swagger만 true). XSS 2차 탈취 경로 차단 */
    @org.springframework.beans.factory.annotation.Value("${app.auth.allow-bearer-header:false}")
    private boolean allowBearerHeader;

    /**
     * JWT 인증 처리 핵심 메서드
     *
     * <p>각 HTTP 요청마다 실행되어 JWT 토큰을 검증하고 인증 정보를 설정합니다. 토큰이 없거나 유효하지 않은 경우 인증 설정 없이 다음 필터로 넘어갑니다. (이후
     * 인증이 필요한 URL은 authenticationEntryPoint에서 401 처리)
     *
     * @param request HTTP 요청 객체 (쿠키 또는 Authorization 헤더 포함)
     * @param response HTTP 응답 객체
     * @param filterChain 다음 필터로 요청을 전달하는 필터 체인
     * @throws ServletException 서블릿 처리 중 예외
     * @throws IOException I/O 처리 중 예외
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 쿠키 우선, Authorization 헤더 폴백으로 JWT 토큰 추출
            String jwt = getJwtFromRequest(request);

            // 토큰이 있고 서명·만료·Access 용도(allowlist) 검증을 통과한 경우에만 인증 설정.
            // 쿠키·Bearer 두 경로 모두 동일한 오버로드를 추출 이후 한 번만 호출한다.
            // allowLegacy=true: 용도 클레임이 없는 배포 전 레거시 Access 토큰을 만료 시점까지 한시 허용.
            if (StringUtils.hasText(jwt)
                    && jwtUtil.validateToken(jwt, JwtUtil.TOKEN_USE_ACCESS, true)) {
                // 토큰의 Payload에서 사번, 자격등급 목록, 부서코드 추출
                String eno = jwtUtil.getEnoFromToken(jwt);
                List<String> athIds = jwtUtil.getAthIdsFromToken(jwt);
                String bbrC = jwtUtil.getBbrCFromToken(jwt);

                // JWT 클레임으로 CustomUserDetails 생성 (DB 재조회 없음 - 성능 최적화)
                CustomUserDetails userDetails = new CustomUserDetails(eno, athIds, bbrC);

                // Spring Security 인증 객체 생성 (credentials=null: 이미 토큰으로 인증됨)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                // 요청 상세 정보(IP, 세션 등)를 인증 객체에 추가
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                // SecurityContextHolder에 인증 정보 설정 → 이후 컨트롤러에서
                // SecurityContextHolder.getContext().getAuthentication()으로 접근 가능
                SecurityContextHolder.getContext().setAuthentication(authentication);

                logger.debug("JWT 인증 성공: " + eno + " - " + request.getRequestURI());
            } else if (StringUtils.hasText(jwt)) {
                // 토큰은 있지만 서명·만료 또는 Access 용도 검증 실패.
                // 보안상 토큰 본문·접두부는 로그에 남기지 않고 요청 URI만 기록한다.
                logger.warn("JWT 서명·만료 또는 Access 용도 검증에 실패했습니다: " + request.getRequestURI());
            }
        } catch (Exception ex) {
            // 예외 발생 시 로그만 기록하고 필터 체인은 계속 진행 (인증 실패로 처리)
            logger.error("=== JWT 인증 처리 중 오류 ===");
            logger.error("요청 URI: " + request.getRequestURI());
            logger.error("오류 메시지: " + ex.getMessage());
            logger.error("=======================", ex);
        }

        // 인증 성공/실패에 관계없이 다음 필터로 진행
        filterChain.doFilter(request, response);
    }

    /**
     * HTTP 요청에서 JWT 토큰 추출 (쿠키 우선 → Authorization 헤더 폴백)
     *
     * <p>토큰 추출 우선순위:
     *
     * <ol>
     *   <li>httpOnly 쿠키 ({@code accessToken}): 브라우저 기반 요청 (프론트엔드)
     *   <li>Authorization 헤더 ({@code Bearer {token}}): API 테스트 도구 (Swagger, Postman)
     * </ol>
     *
     * @param request HTTP 요청 객체
     * @return JWT 토큰 문자열 (쿠키/헤더 모두 없으면 null)
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        // 1. httpOnly 쿠키에서 accessToken 추출 (우선)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (CookieUtil.ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // 2. Authorization 헤더에서 Bearer 토큰 추출 (폴백) — 운영 비활성화 가능 (app.auth.allow-bearer-header)
        if (allowBearerHeader) {
            String bearerToken = request.getHeader("Authorization");
            // getHeader는 헤더가 없으면 null을 반환한다. hasText가 이미 걸러내지만 널 검사를
            // 명시해 두어야 정적분석이 역참조 안전성을 인식한다.
            if (bearerToken != null
                    && StringUtils.hasText(bearerToken)
                    && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7); // "Bearer ".length() == 7
            }
        }

        return null; // 쿠키/헤더 모두 없음 (또는 헤더 폴백 비활성)
    }
}
