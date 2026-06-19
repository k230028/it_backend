package com.kdb.it.config;

import com.kdb.it.common.system.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.config.Customizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.kdb.it.common.util.CustomPasswordEncoder;

import java.util.List;

/**
 * Spring Security 보안 설정 클래스
 *
 * <p>
 * JWT 기반 Stateless 인증 방식의 보안 정책을 정의합니다.
 * </p>
 *
 * <p>
 * 주요 보안 설정:
 * </p>
 * <ul>
 * <li>CSRF: 비활성화 (Stateless API + SameSite/CORS 운영 전제)</li>
 * <li>세션: STATELESS (JWT 토큰으로 인증 상태 유지)</li>
 * <li>CORS: {@code cors.allowed-origins}의 명시 Origin만 허용</li>
 * <li>인증 필터: {@link JwtAuthenticationFilter} →
 * {@link UsernamePasswordAuthenticationFilter} 앞에 삽입</li>
 * </ul>
 *
 * <p>
 * 공개 엔드포인트 (인증 불필요):
 * </p>
 * <ul>
 * <li>{@code POST /api/auth/login}: 로그인</li>
 * <li>{@code POST /api/auth/refresh}: 토큰 갱신</li>
 * <li>{@code /swagger-ui/**}: Swagger UI</li>
 * <li>{@code /v3/api-docs/**}: OpenAPI 명세</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize, @PostAuthorize 활성화
@RequiredArgsConstructor
public class SecurityConfig {

        private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

        /** JWT 인증 처리 필터 (매 요청마다 JWT 토큰 검증) */
        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        /**
         * 허용할 CORS Origin 목록
         * 개발 환경: {@code *} (전체 허용)
         * 운영 환경: {@code application.properties}의 {@code cors.allowed-origins}에 도메인 지정
         * 예: {@code cors.allowed-origins=https://itportal.kdb.com}
         */
        @Value("${cors.allowed-origins:*}")
        private String allowedOrigins;

        /**
         * Spring Security 필터 체인 설정
         *
         * <p>
         * HTTP 요청에 대한 보안 정책을 정의합니다.
         * </p>
         *
         * <p>
         * 필터 체인 처리 순서:
         * </p>
         * <ol>
         * <li>CORS 처리</li>
         * <li>CSRF 비활성화</li>
         * <li>세션 STATELESS 설정</li>
         * <li>URL별 인증 권한 검사</li>
         * <li>예외 처리 (인증 실패, 접근 거부)</li>
         * <li>JWT 인증 필터 실행</li>
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
                                .headers(headers -> headers
                                                // MIME 스니핑 방지: 브라우저가 Content-Type을 임의 변경 불가
                                                .contentTypeOptions(Customizer.withDefaults())
                                                // 클릭재킹 방지: iframe 삽입 금지
                                                .frameOptions(frame -> frame.deny())
                                                // HSTS: HTTPS 강제 (운영 환경 대비, max-age=1년)
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000))
                                                // CSP: XSS 2차 방어선
                                                .contentSecurityPolicy(csp -> csp
                                                                .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; object-src 'none'")))
                                // CORS 설정 적용 (corsConfigurationSource 빈 사용)
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                // CSRF 보호 비활성화: httpOnly 쿠키를 쓰므로 운영 SameSite/CORS 설정과 함께 관리
                                .csrf(AbstractHttpConfigurer::disable)
                                // Stateless 세션 설정 (JWT 사용): 서버가 세션을 생성/유지하지 않음
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                // URL별 접근 권한 설정
                                .authorizeHttpRequests(auth -> auth
                                                // 인증 없이 접근 가능한 엔드포인트
                                                .requestMatchers("/api/auth/login",
                                                                "/api/auth/refresh",
                                                                "/swagger-ui/**", "/v3/api-docs/**",
                                                                "/swagger-resources/**", "/webjars/**",
                                                                "/swagger-ui.html", "/error",
                                                                // SSO 흐름 — business/checkauth/loginProc/logout (SsoController)
                                                                "/sso/**",
                                                                // SSO 브리지 — loginProc에서 리다이렉트되는 JWT 발급 엔드포인트
                                                                "/api/auth/sso/complete")
                                                .permitAll()
                                                // 관리자 전용 엔드포인트 (ITPAD001만 접근 가능)
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                                // 회원가입 — 관리자만 신규 계정 생성 가능 (임직원 포털 특성상 자유 가입 금지)
                                                .requestMatchers("/api/auth/signup").hasRole("ADMIN")
                                                // 정보기술부문계획 — 관리자 전용
                                                .requestMatchers("/api/plan/**").hasRole("ADMIN")
                                                // 나머지는 인증 필요 (유효한 JWT 토큰 필수)
                                                .anyRequest().authenticated())
                                // 인증/접근 예외 처리 핸들러 설정
                                .exceptionHandling(exception -> exception
                                                // 인증 실패 시 처리 (401 Unauthorized)
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        log.warn("인증 실패 - URI: {}, 메서드: {}, IP: {}, 오류: {}",
                                                                        request.getRequestURI(), request.getMethod(),
                                                                        request.getRemoteAddr(),
                                                                        authException.getMessage());
                                                        // HTTP 401 응답 반환
                                                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                                                                        "Unauthorized");
                                                })
                                                // 권한 부족 시 처리 (403 Forbidden)
                                                .accessDeniedHandler((request, response, accessDeniedException) -> {
                                                        log.warn("접근 거부 - URI: {}, 메서드: {}, IP: {}, 오류: {}",
                                                                        request.getRequestURI(), request.getMethod(),
                                                                        request.getRemoteAddr(),
                                                                        accessDeniedException.getMessage());
                                                        // HTTP 403 응답 반환
                                                        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                                                        "Forbidden");
                                                }))
                                // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 삽입
                                // → 모든 요청에서 JWT 토큰 먼저 검증
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        /**
         * CORS(Cross-Origin Resource Sharing) 설정
         *
         * <p>
         * 프론트엔드와 백엔드가 다른 도메인/포트에서 실행될 때 브라우저의
         * CORS 정책을 허용하도록 설정합니다.
         * </p>
         *
         * <p>
         * 현재 설정 (개발 환경):
         * </p>
         * <ul>
         * <li>허용 Origin: {@code cors.allowed-origins}에 지정된 명시 Origin</li>
         * <li>허용 메서드: GET, POST, PUT, DELETE, OPTIONS, PATCH</li>
         * <li>허용 헤더: 전체 ({@code *})</li>
         * <li>자격증명(쿠키 등) 포함 허용: true</li>
         * </ul>
         *
         * @return 모든 경로({@code /**})에 적용되는 {@link CorsConfigurationSource}
         */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                // 허용 Origin: application.properties의 cors.allowed-origins 값 사용
                // allowCredentials=true 사용 시 와일드카드(*) 불가 → 명시적 도메인 필요
                configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
                // 허용할 HTTP 메서드 목록
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                // 모든 요청 헤더 허용 (Authorization, Content-Type 등)
                configuration.setAllowedHeaders(List.of("*"));
                // 쿠키, Authorization 헤더 등 자격증명 포함 허용
                configuration.setAllowCredentials(true);
                // 브라우저가 읽을 수 있도록 노출할 응답 헤더 (201 Created 시 신규 리소스 경로 추출용)
                configuration.setExposedHeaders(List.of("Location"));

                // 모든 URL 경로에 CORS 설정 적용
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        /**
         * 비밀번호 인코더 빈 등록
         *
         * <p>
         * SHA-256 기반의 {@link CustomPasswordEncoder}를 Spring Security의
         * {@link PasswordEncoder}로 등록합니다. 회원가입 시 비밀번호 암호화,
         * 로그인 시 비밀번호 검증에 사용됩니다.
         * </p>
         *
         * @return {@link CustomPasswordEncoder} 인스턴스
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new CustomPasswordEncoder();
        }

}
