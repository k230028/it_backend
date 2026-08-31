package com.kdb.it.config;

import com.kdb.it.common.sso.SsoController;
import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import com.kdb.it.common.system.security.SimpleRequestCsrfFilter;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.common.util.CustomPasswordEncoder;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 보안 설정 클래스
 *
 * <p>JWT 기반 Stateless 인증 방식의 보안 정책을 정의합니다.
 *
 * <p>주요 보안 설정:
 *
 * <ul>
 *   <li>CSRF 토큰: 비활성화 (Stateless API + SameSite/CORS 운영 전제)
 *   <li>CSRF 보완: {@link SimpleRequestCsrfFilter} — preflight를 우회하는 단순 요청 Content-Type 변경 요청 차단
 *   <li>세션: STATELESS (JWT 토큰으로 인증 상태 유지)
 *   <li>CORS: {@code cors.allowed-origins}의 명시 Origin만 허용
 *   <li>인증 필터: {@link JwtAuthenticationFilter} → {@link UsernamePasswordAuthenticationFilter} 앞에 삽입
 * </ul>
 *
 * <p>공개 엔드포인트 (인증 불필요):
 *
 * <ul>
 *   <li>{@code POST /api/auth/login/start}: 수동 로그인 시작
 *   <li>{@code POST /api/auth/login/complete}: MFA 검증 뒤 수동 로그인 완료
 *   <li>{@code POST /api/auth/refresh}: 토큰 갱신
 *   <li>{@code POST /api/auth/logout}: 로그아웃 — Refresh 쿠키로 소유자를 확인하므로 Access 인증이 만료된 뒤에도 폐기가 가능해야 한다
 *   <li>OpenAPI가 활성화된 프로파일의 {@code /swagger-ui/**}, {@code /v3/api-docs/**}: API 문서
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize, @PostAuthorize 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String[] OPEN_API_PATHS = {
        "/swagger-ui/**",
        "/swagger-resources/**",
        "/webjars/**",
        "/swagger-ui.html",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/v3/api-docs.yaml"
    };

    /** JWT 인증 처리 필터 (매 요청마다 JWT 토큰 검증) */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** preflight를 우회하는 단순 요청 Content-Type의 변경 요청을 차단하는 CSRF 보완 필터 */
    private final SimpleRequestCsrfFilter simpleRequestCsrfFilter;

    /** 인증 실패 응답에서도 브라우저에 남은 MFA 증표를 정리하는 쿠키 유틸리티 */
    private final CookieUtil cookieUtil;

    /**
     * 허용할 CORS Origin 목록 모든 환경에서 {@code application.properties}의 {@code cors.allowed-origins}에 명시한
     * Origin만 허용합니다. 운영 환경은 {@link com.kdb.it.common.system.EnvironmentValidator}가 빈 값과 와일드카드를
     * 거부합니다. 예: {@code cors.allowed-origins=https://itportal.kdb.com}
     */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    /** OpenAPI 문서 공개 여부 (미설정 시 springdoc 기본값인 활성 상태) */
    @Value("${springdoc.api-docs.enabled:true}")
    private boolean apiDocsEnabled;

    /**
     * Spring Security 필터 체인 설정
     *
     * <p>HTTP 요청에 대한 보안 정책을 정의합니다.
     *
     * <p>필터 체인 처리 순서:
     *
     * <ol>
     *   <li>CORS 처리
     *   <li>CSRF 비활성화
     *   <li>세션 STATELESS 설정
     *   <li>URL별 인증 권한 검사
     *   <li>예외 처리 (인증 실패, 접근 거부)
     *   <li>JWT 인증 필터 실행
     * </ol>
     *
     * @param http {@link HttpSecurity} 설정 빌더
     * @return 설정된 {@link SecurityFilterChain}
     * @throws Exception 설정 중 오류 발생 시
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // HTTP 보안 헤더 명시적 설정 (Snyk SNYK-JAVA-ORGSPRINGFRAMEWORK-* 대응)
                .headers(
                        headers ->
                                headers
                                        // MIME 스니핑 방지: 브라우저가 Content-Type을 임의 변경 불가
                                        .contentTypeOptions(Customizer.withDefaults())
                                        // 클릭재킹 방지: iframe 삽입 금지
                                        .frameOptions(frame -> frame.deny())
                                        // HSTS: HTTPS 강제 (운영 환경 대비, max-age=1년)
                                        .httpStrictTransportSecurity(
                                                hsts ->
                                                        hsts.includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000))
                                        // CSP: XSS 2차 방어선
                                        // script-src의 sha256 해시는 SSO CS 모드 saveToken POST 브리지 페이지의
                                        // 고정 인라인 스크립트(SsoController.CS_MODE_SUBMIT_SCRIPT)만 허용한다.
                                        // 해시는 그 스크립트 본문에서 런타임 계산하므로 스크립트 변경 시 자동 정합된다.
                                        .contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                contentSecurityPolicy())))
                // CORS 설정 적용 (corsConfigurationSource 빈 사용)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF 토큰 비활성화: httpOnly 쿠키를 쓰므로 운영 SameSite/CORS 설정과 함께 관리한다.
                // 단 CORS 안전 목록 Content-Type은 preflight를 우회하므로 SimpleRequestCsrfFilter가 보완한다.
                .csrf(value -> value.disable())
                // Stateless 세션 설정 (JWT 사용): 서버가 세션을 생성/유지하지 않음
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // URL별 접근 권한 설정
                .authorizeHttpRequests(
                        auth -> {
                            auth
                                    // StreamingResponseBody 완료 시 같은 요청이 ASYNC로 재디스패치된다.
                                    // 최초 REQUEST는 아래 URL 규칙과 메서드 보안으로 이미 인가됐으므로,
                                    // stateless JWT 컨텍스트가 없는 내부 재디스패치만 다시 허용한다.
                                    .dispatcherTypeMatchers(DispatcherType.ASYNC)
                                    .permitAll()
                                    // 인증 없이 접근 가능한 엔드포인트
                                    .requestMatchers(
                                            "/api/auth/login/start",
                                            "/api/auth/login/complete",
                                            "/api/auth/refresh",
                                            // 로그아웃 — Access Token이 이미 만료·삭제된 상태에서만 호출되는
                                            // 정리 API다. 인증을 요구하면 정작 필요한 순간에 401이 되어
                                            // 서버 패밀리가 남고 401 로그만 반복된다. 소유자 확인은
                                            // AuthController#logout이 Refresh 쿠키 값으로 수행하며,
                                            // 쿠키가 없으면 아무것도 폐기하지 않는다(무자격 폐기 불가).
                                            // 쿠키는 SameSite=Lax이므로 교차 사이트 POST에는 실리지 않는다.
                                            "/api/auth/logout",
                                            // MFA 용도는 요청 본문과 서버 거래에서 판정한다. 로그인 거래만 JWT 없이
                                            // 통과시키며, 결재 거래는 MfaService가 JWT 소유권 없이는 즉시 거부한다.
                                            "/api/mfa/challenges",
                                            "/error",
                                            // 브라우저 기본 요청 — 인증 불필요(인증 실패 WARN 로그 노이즈 제거)
                                            "/favicon.ico",
                                            // SSO 흐름 — business/checkauth/loginProc/logout
                                            // (SsoController)
                                            "/sso/**",
                                            // SSO 브리지 — loginProc에서 리다이렉트되는 JWT 발급 엔드포인트
                                            "/api/auth/sso/complete")
                                    .permitAll();
                            auth.requestMatchers(HttpMethod.POST, "/api/mfa/challenges/*/verify")
                                    .permitAll();
                            auth.requestMatchers(HttpMethod.DELETE, "/api/mfa/challenges/*")
                                    .permitAll();
                            // API 문서는 활성 프로파일에서만 익명 접근을 허용한다.
                            if (apiDocsEnabled) {
                                auth.requestMatchers(OPEN_API_PATHS).permitAll();
                            }
                            auth
                                    // 피어 인스턴스 전용 경로. 컨트롤러가 X-Internal-Token으로 직접 인증하고,
                                    // 비밀값이 비면 컨트롤러 자체가 등록되지 않아 404가 된다.
                                    .requestMatchers("/internal/was-logs/**")
                                    .permitAll()
                                    // 관리자 전용 엔드포인트 (ITPAD001만 접근 가능)
                                    .requestMatchers("/api/admin/**")
                                    .hasRole("ADMIN")
                                    // 회원가입 — 관리자만 신규 계정 생성 가능 (임직원 포털 특성상 자유 가입 금지)
                                    .requestMatchers("/api/auth/signup")
                                    .hasRole("ADMIN")
                                    // 정보기술부문계획 — 관리자 전용 (컨트롤러 실제 경로 /api/plans 와 정합)
                                    .requestMatchers("/api/plans/**")
                                    .hasRole("ADMIN")
                                    // Actuator: health 는 공개(로드밸런서/모니터링 헬스체크),
                                    // 나머지(metrics 등)는 관리자 전용 — 감사 실패 메트릭 보호 (ERR-06)
                                    .requestMatchers("/actuator/health")
                                    .permitAll()
                                    .requestMatchers("/actuator/**")
                                    .hasRole("ADMIN")
                                    // 나머지는 인증 필요 (유효한 JWT 토큰 필수)
                                    .anyRequest()
                                    .authenticated();
                        })
                // 인증/접근 예외 처리 핸들러 설정
                .exceptionHandling(
                        exception ->
                                exception
                                        // 인증 실패 시 처리 (401 Unauthorized)
                                        .authenticationEntryPoint(
                                                (request, response, authException) -> {
                                                    log.warn(
                                                            "인증 실패 - URI: {}, 메서드: {}, IP: {}, 오류: {}",
                                                            request.getRequestURI(),
                                                            request.getMethod(),
                                                            request.getRemoteAddr(),
                                                            authException.getMessage());
                                                    response.addHeader(
                                                            org.springframework.http.HttpHeaders
                                                                    .SET_COOKIE,
                                                            cookieUtil
                                                                    .deleteMfaProofCookie()
                                                                    .toString());
                                                    // HTTP 401 응답 반환
                                                    response.sendError(
                                                            HttpServletResponse.SC_UNAUTHORIZED,
                                                            "Unauthorized");
                                                })
                                        // 권한 부족 시 처리 (403 Forbidden)
                                        .accessDeniedHandler(
                                                (request, response, accessDeniedException) -> {
                                                    log.warn(
                                                            "접근 거부 - URI: {}, 메서드: {}, IP: {}, 오류: {}",
                                                            request.getRequestURI(),
                                                            request.getMethod(),
                                                            request.getRemoteAddr(),
                                                            accessDeniedException.getMessage());
                                                    // HTTP 403 응답 반환
                                                    response.sendError(
                                                            HttpServletResponse.SC_FORBIDDEN,
                                                            "Forbidden");
                                                }))
                // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 삽입
                // → 모든 요청에서 JWT 토큰 먼저 검증
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // CSRF 보완 필터는 인증보다 먼저 판정해 단순 요청 변경 시도를 즉시 403으로 끊는다.
                .addFilterBefore(simpleRequestCsrfFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS(Cross-Origin Resource Sharing) 설정
     *
     * <p>프론트엔드와 백엔드가 다른 도메인/포트에서 실행될 때 브라우저의 CORS 정책을 허용하도록 설정합니다.
     *
     * <p>일반 API 설정:
     *
     * <ul>
     *   <li>허용 Origin: {@code cors.allowed-origins}에 지정된 명시 Origin
     *   <li>허용 메서드: GET, POST, PUT, DELETE, OPTIONS, PATCH
     *   <li>허용 헤더: 명시 목록(Content-Type/Authorization/X-Requested-With)
     *   <li>자격증명(쿠키 등) 포함 허용: true
     * </ul>
     *
     * <p>{@code /sso/**}는 브라우저 전체 페이지 콜백 경로이므로 별도 설정을 사용합니다. 모든 Origin 패턴과 GET/POST/OPTIONS를 허용하되
     * 자격증명 공유는 허용하지 않습니다.
     *
     * @return {@code /sso/**}와 일반 API 경로를 구분해 적용하는 {@link CorsConfigurationSource}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 허용 Origin: application.properties의 cors.allowed-origins 값 사용
        // allowCredentials=true 사용 시 와일드카드(*) 불가 → 명시적 도메인 필요
        // split 후 공백 제거 + 빈 항목 필터링: 빈/콤마-공백 입력이 [""]로 해석돼
        // 모든 교차 출처를 조용히 차단하는 footgun 방지.
        List<String> origins =
                java.util.Arrays.stream(allowedOrigins.split(","))
                        .map(value -> value.trim())
                        .filter(s -> !s.isEmpty())
                        .toList();
        if (origins.isEmpty()) {
            // 미설정 시 빈 목록을 명시적으로 설정 — 동작이 의도적이고 가시적이도록 WARN 로깅.
            log.warn("[CORS] cors.allowed-origins 미설정 — 교차 출처 요청이 차단됩니다");
        }
        configuration.setAllowedOrigins(origins);
        // 허용할 HTTP 메서드 목록
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // 허용 헤더 명시화: 운영에서 필요한 표준 헤더만 허용 (와일드카드 제거)
        configuration.setAllowedHeaders(
                List.of("Content-Type", "Authorization", "X-Requested-With"));
        // 쿠키, Authorization 헤더 등 자격증명 포함 허용
        configuration.setAllowCredentials(true);
        // 브라우저가 읽을 수 있도록 노출할 응답 헤더
        // - Location: 201 Created 시 신규 리소스 경로 추출용
        // - X-Total-Count: 페이지 조회(page·size) 응답의 전체 건수. 노출하지 않으면 다른 출처의 스크립트가 읽지 못한다.
        configuration.setExposedHeaders(
                List.of("Location", com.kdb.it.common.util.ListPageParams.TOTAL_COUNT_HEADER));

        // SSO 콜백 전용 CORS 설정.
        // /sso/** 는 SPA의 XHR 대상이 아니라 ESSO(외부 인증서버)가 브라우저를 통해 교차 출처로
        // 콜백/리다이렉트하는 전체 페이지 내비게이션 엔드포인트다. SPA용 allowlist(localhost 등)로
        // 게이트하면 ESSO origin(예: http://intesso.kdb.co.kr:20080)이나 IP 기반 접근의 Origin이
        // 목록에 없어 CorsFilter가 "Invalid CORS request"(403)로 콜백을 차단한다. 콜백 응답은
        // 스크립트가 교차 출처에서 읽는 대상이 아니므로(자격증명 불필요) origin을 제한하지 않는다.
        CorsConfiguration ssoConfiguration = new CorsConfiguration();
        ssoConfiguration.setAllowedOriginPatterns(List.of("*"));
        ssoConfiguration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        ssoConfiguration.setAllowedHeaders(List.of("*"));
        ssoConfiguration.setAllowCredentials(false);

        // URL 경로별 CORS 설정. 더 구체적인 /sso/** 를 /** 보다 먼저 등록해 우선 매칭시킨다.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/sso/**", ssoConfiguration);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Content-Security-Policy 지시문을 조립합니다.
     *
     * <p>{@code script-src}에는 {@code 'self'}와, SSO CS 모드 saveToken POST 브리지 페이지의 고정 인라인 스크립트({@link
     * SsoController#CS_MODE_SUBMIT_SCRIPT})에 대한 sha256 해시만 허용합니다. 해시는 스크립트 본문에서 런타임 계산하므로 스크립트가
     * 바뀌어도 자동 정합됩니다.
     *
     * @return CSP policy-directives 문자열
     */
    private static String contentSecurityPolicy() {
        return "default-src 'self'; "
                + "script-src 'self' "
                + cspHash(SsoController.CS_MODE_SUBMIT_SCRIPT)
                + "; "
                + "style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; "
                + "connect-src 'self'; "
                + "frame-ancestors 'none'; "
                + "object-src 'none'";
    }

    /**
     * 인라인 스크립트 본문의 CSP sha256 소스 표현({@code 'sha256-...'})을 계산합니다.
     *
     * @param script 인라인 스크립트 본문(태그 사이 정확한 텍스트)
     * @return CSP에 넣을 {@code 'sha256-<base64>'} 토큰
     */
    private static String cspHash(String script) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(script.getBytes(StandardCharsets.UTF_8));
            return "'sha256-" + Base64.getEncoder().encodeToString(digest) + "'";
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 표준 JDK에 항상 존재하므로 정상 환경에서는 도달하지 않음.
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    /**
     * 비밀번호 인코더 빈 등록
     *
     * <p>SHA-256 기반의 {@link CustomPasswordEncoder}를 Spring Security의 {@link PasswordEncoder}로
     * 등록합니다. 회원가입 시 비밀번호 암호화, 로그인 시 비밀번호 검증에 사용됩니다.
     *
     * @return {@link CustomPasswordEncoder} 인스턴스
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new CustomPasswordEncoder();
    }
}
