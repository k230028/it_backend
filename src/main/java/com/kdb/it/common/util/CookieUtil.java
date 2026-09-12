package com.kdb.it.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.dto.AuthDto;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * JWT 토큰 쿠키 관리 유틸리티
 *
 * <p>httpOnly 쿠키를 통해 JWT 토큰을 안전하게 전달/관리합니다. XSS 공격 시 JavaScript로 토큰에 접근할 수 없어 토큰 탈취를 방지합니다.
 *
 * <p>쿠키 보안 속성:
 *
 * <ul>
 *   <li>{@code httpOnly}: true — JavaScript 접근 차단 (XSS 방어)
 *   <li>{@code secure}: 프로파일별 분기 (운영=true, 개발=false)
 *   <li>{@code sameSite}: Lax — CSRF 방어 + 외부 링크 네비게이션 허용
 *   <li>{@code path}: Access Token="/", Refresh Token="/api/auth", SSO 검증 완료="/api/auth/sso"
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final ObjectMapper objectMapper;

    /** Access Token 쿠키 이름 */
    public static final String ACCESS_TOKEN_COOKIE = "accessToken";

    /** Refresh Token 쿠키 이름 */
    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    /** 수동 로그인 MFA 대기 거래 쿠키 이름 */
    public static final String LOGIN_PENDING_COOKIE = "mfa-login-pending";

    /** MFA 검증 완료 증표 쿠키 이름 */
    public static final String MFA_PROOF_COOKIE = "mfa-proof";

    /** Access Token 쿠키 만료 시간 (15분, 초 단위) - jwt.access-token-validity와 동일하게 유지 */
    private static final long ACCESS_TOKEN_MAX_AGE = 15 * 60;

    /** Refresh Token 쿠키 만료 시간 (7일, 초 단위) */
    private static final long REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60;

    /** SSO 복귀 경로 쿠키 이름 — 인증 완료 후 돌아갈 프론트 내부 경로(next) 보관 */
    public static final String SSO_NEXT_COOKIE = "sso-next";

    /** SSO 복귀 origin 쿠키 이름 — 인증 완료 후 돌아갈 프론트 origin 보관 */
    public static final String SSO_ORIGIN_COOKIE = "sso-origin";

    /** SSO 복귀 상태 쿠키 만료 시간 (10분, 초 단위) — SSO 왕복 동안만 유지 */
    private static final long SSO_STATE_MAX_AGE = 10 * 60;

    /** SSO 검증 완료 쿠키 이름 — checkauth가 검증한 사번을 담은 단기 JWT를 complete까지 운반 */
    public static final String SSO_VERIFIED_COOKIE = "sso-verified";

    /** SSO 검증 완료 쿠키 전송 경로 — {@code /api/auth/sso/complete}에만 전송되도록 제한 */
    private static final String SSO_VERIFIED_PATH = "/api/auth/sso";

    /**
     * SSO 검증 완료 토큰 유효시간(ms). {@code JwtUtil}이 토큰 만료에 쓰는 {@code jwt.sso-verified-validity}와 같은 속성을
     * 읽어 쿠키 Max-Age가 토큰 수명과 함께 움직이게 한다. 미설정 시 60초. 테스트가 {@code new CookieUtil(...)}로 생성할 때를 위해 같은
     * 기본값으로 초기화한다.
     */
    @Value("${jwt.sso-verified-validity:60000}")
    private long ssoVerifiedValidityMs = 60_000L;

    /**
     * 쿠키 Secure 플래그. 개발 환경만 프로파일에서 false(HTTP 허용)로 내리고 그 밖에는 true(HTTPS 전용)입니다. 설정이 빠졌을 때 평문 전송으로
     * 떨어지지 않도록 기본값도 true로 둡니다. 운영 프로파일은 {@code EnvironmentValidator}가 값 자체를 다시 강제합니다.
     */
    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    /**
     * Spring 응답 쿠키를 {@code Set-Cookie} 헤더에 출력합니다.
     *
     * <p>{@link ResponseCookie}가 검증·직렬화한 값을 그대로 사용해 Secure, SameSite, Partitioned 같은 속성을 빠짐없이
     * 보존합니다.
     *
     * @param response 쿠키를 추가할 응답
     * @param source 출력할 Spring 응답 쿠키
     * @throws NullPointerException 응답 또는 쿠키가 null인 경우
     */
    public static void addResponseCookie(HttpServletResponse response, ResponseCookie source) {
        Objects.requireNonNull(response, "응답");
        Objects.requireNonNull(source, "응답 쿠키");

        response.addHeader(HttpHeaders.SET_COOKIE, source.toString());
    }

    /**
     * Access Token httpOnly 쿠키 생성
     *
     * <p>모든 API 경로({@code path="/"})에서 전송되며, 15분 후 만료됩니다.
     *
     * @param token JWT Access Token 값
     * @return Set-Cookie 헤더에 사용할 {@link ResponseCookie}
     */
    public ResponseCookie createAccessTokenCookie(String token) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true) // JavaScript 접근 차단
                .secure(secureCookie) // HTTPS 전용 여부 (프로파일별)
                .path("/") // 모든 API 경로에서 전송
                .maxAge(ACCESS_TOKEN_MAX_AGE) // 15분
                .sameSite("Lax") // CSRF 방어 + 네비게이션 허용
                .build();
    }

    /**
     * Refresh Token httpOnly 쿠키 생성
     *
     * <p>인증 경로({@code path="/api/auth"})에서만 전송되며, 7일 후 만료됩니다. Refresh Token은 토큰 갱신/로그아웃 시에만 필요하므로
     * 경로를 제한합니다.
     *
     * @param token JWT Refresh Token 값
     * @return Set-Cookie 헤더에 사용할 {@link ResponseCookie}
     */
    public ResponseCookie createRefreshTokenCookie(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true) // JavaScript 접근 차단
                .secure(secureCookie) // HTTPS 전용 여부 (프로파일별)
                .path("/api/auth") // 인증 경로에서만 전송
                .maxAge(REFRESH_TOKEN_MAX_AGE) // 7일
                .sameSite("Lax") // CSRF 방어 + 네비게이션 허용
                .build();
    }

    /**
     * 수동 로그인 MFA 대기 거래를 브라우저에 보관할 httpOnly 쿠키를 생성합니다.
     *
     * @param pendingToken 서버가 발급한 로그인 대기 거래 원문
     * @param expiresAt 서버 기준 대기 거래 만료 시각
     * @return 만료 시각과 같은 수명으로 제한된 로그인 대기 쿠키
     */
    public ResponseCookie createLoginPendingCookie(String pendingToken, Instant expiresAt) {
        long remainingSeconds =
                Math.max(0L, Duration.between(Instant.now(), expiresAt).toSeconds());
        return ResponseCookie.from(LOGIN_PENDING_COOKIE, pendingToken)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(Duration.ofSeconds(remainingSeconds))
                .sameSite("Lax")
                .build();
    }

    /**
     * Access Token 쿠키 삭제 (로그아웃 시 사용)
     *
     * <p>{@code maxAge=0}으로 설정하여 브라우저에서 즉시 삭제됩니다.
     *
     * @return maxAge=0인 삭제용 {@link ResponseCookie}
     */
    public ResponseCookie deleteAccessTokenCookie() {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0) // 즉시 만료 → 브라우저에서 삭제
                .sameSite("Lax")
                .build();
    }

    /**
     * Refresh Token 쿠키 삭제 (로그아웃 시 사용)
     *
     * <p>{@code maxAge=0}으로 설정하여 브라우저에서 즉시 삭제됩니다.
     *
     * @return maxAge=0인 삭제용 {@link ResponseCookie}
     */
    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/api/auth")
                .maxAge(0) // 즉시 만료 → 브라우저에서 삭제
                .sameSite("Lax")
                .build();
    }

    /** MFA 검증 완료 증표를 모든 API 경로에서 즉시 만료시키는 삭제 쿠키를 만든다. */
    public ResponseCookie deleteMfaProofCookie() {
        return ResponseCookie.from(MFA_PROOF_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    /**
     * SSO 복귀 경로(next) 쿠키를 생성합니다.
     *
     * <p>SSO 시작({@code /sso/business}) 시 원본 요청 경로를 쿠키에 보관해, 인증 완료 ({@code
     * /api/auth/sso/complete})까지 운반합니다. ESSO 교차 출처 왕복(특히 CS 모드 POST 콜백) 중에는 서버 세션이 끊길 수 있으나, 이 쿠키는
     * 마지막 same-site complete 내비게이션(GET)에 전달되므로 세션 유실과 무관하게 원본 URL을 복원할 수 있습니다. 값은 경로에 포함될 수 있는 쿼리
     * 문자({@code ?&=})를 보존하기 위해 URL 인코딩합니다.
     *
     * @param next 프론트 내부 경로 (예: {@code /info/projects/123})
     * @return {@code sso-next} 쿠키
     */
    public ResponseCookie createSsoNextCookie(String next) {
        return ssoStateCookie(SSO_NEXT_COOKIE, next);
    }

    /**
     * SSO 복귀 origin 쿠키를 생성합니다. 상세는 {@link #createSsoNextCookie(String)} 참조.
     *
     * @param origin 프론트 origin (예: {@code http://10.9.16.109:28080})
     * @return {@code sso-origin} 쿠키
     */
    public ResponseCookie createSsoOriginCookie(String origin) {
        return ssoStateCookie(SSO_ORIGIN_COOKIE, origin);
    }

    /** SSO 복귀 상태 쿠키(next/origin) 공통 생성기. 값은 URL 인코딩, Lax/path=/로 발급. */
    private ResponseCookie ssoStateCookie(String name, String value) {
        String encoded = URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
        return ResponseCookie.from(name, encoded)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(SSO_STATE_MAX_AGE)
                .sameSite("Lax")
                .build();
    }

    /**
     * SSO 검증 완료 쿠키를 생성합니다.
     *
     * <p>ESSO 검증을 통과한 사번을 담은 단기 JWT({@code JwtUtil.generateSsoVerifiedToken})를 서버 세션 대신 httpOnly
     * 쿠키로 운반합니다. 경로를 {@code /api/auth/sso}로 제한해 완료 엔드포인트 외에는 전송되지 않게 하고, Max-Age는 {@code
     * jwt.sso-verified-validity}(ms)를 초 단위로 올림한 값(최소 1초)이라 토큰 만료와 함께 사라집니다. 완료 처리 후에는 {@link
     * #deleteSsoVerifiedCookie()}로 즉시 제거합니다.
     *
     * @param token 서명된 SSO 검증 완료 JWT
     * @return {@code sso-verified} 쿠키
     */
    public ResponseCookie createSsoVerifiedCookie(String token) {
        return ResponseCookie.from(SSO_VERIFIED_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path(SSO_VERIFIED_PATH)
                .maxAge(ssoVerifiedMaxAgeSeconds())
                .sameSite("Lax")
                .build();
    }

    /** 토큰 유효시간(ms)을 쿠키 Max-Age(초)로 올림 변환합니다. 쿠키가 토큰보다 먼저 사라지지 않도록 올림하고 최소 1초를 보장합니다. */
    private long ssoVerifiedMaxAgeSeconds() {
        return Math.max(1L, Math.ceilDiv(ssoVerifiedValidityMs, 1000L));
    }

    /** SSO 검증 완료 쿠키 삭제용 쿠키(Max-Age=0)를 생성합니다. 발급 경로와 같은 경로로 내려야 브라우저가 지웁니다. */
    public ResponseCookie deleteSsoVerifiedCookie() {
        return ResponseCookie.from(SSO_VERIFIED_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path(SSO_VERIFIED_PATH)
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    /** SSO 복귀 경로(next) 쿠키 삭제용 쿠키를 생성합니다(인증 완료 후 정리). */
    public ResponseCookie deleteSsoNextCookie() {
        return deleteSsoStateCookie(SSO_NEXT_COOKIE);
    }

    /** SSO 복귀 origin 쿠키 삭제용 쿠키를 생성합니다(인증 완료 후 정리). */
    public ResponseCookie deleteSsoOriginCookie() {
        return deleteSsoStateCookie(SSO_ORIGIN_COOKIE);
    }

    /** SSO 복귀 상태 쿠키 삭제 공통기(maxAge=0). */
    private ResponseCookie deleteSsoStateCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    /**
     * 프론트엔드 인증 상태 복원용 사용자 정보 쿠키를 생성합니다.
     *
     * <p>Access Token과 Refresh Token은 httpOnly 쿠키라서 JavaScript가 읽을 수 없습니다. Nuxt 화면에서는 현재 사용자명, 권한,
     * 부서 코드가 필요하므로 민감 토큰이 아닌 최소 사용자 표시 정보만 {@code it-portal-user} 쿠키에 별도로 담습니다.
     *
     * <p>이 쿠키는 SSO 완료 직후 서버가 직접 내려보내며, 프론트엔드의 {@code useCookie('it-portal-user')}와 {@code
     * stores/auth.ts}가 읽어 Pinia 인증 상태를 복원합니다. 값 형식은 Nuxt 쿠키 저장 방식과 맞추기 위해 JSON 직렬화 후 URL 인코딩합니다.
     *
     * <p>보안상 이 쿠키에는 JWT나 비밀번호 같은 인증 비밀값을 넣으면 안 됩니다. 실제 API 인증은 별도 httpOnly Access Token 쿠키로 수행됩니다.
     *
     * @param loginResponse SSO 토큰 발급 후 사용자/권한 정보가 담긴 응답 DTO
     * @return Nuxt가 읽을 수 있는 {@code it-portal-user} 쿠키
     */
    public ResponseCookie createUserInfoCookie(AuthDto.LoginResponse loginResponse) {
        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("eno", loginResponse.getEno());
        userMap.put("empNm", loginResponse.getEmpNm());
        userMap.put("athIds", loginResponse.getAthIds());
        userMap.put("bbrC", loginResponse.getBbrC());
        userMap.put("temC", loginResponse.getTemC());

        String json;
        try {
            json = objectMapper.writeValueAsString(userMap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("사용자 정보 쿠키 직렬화 실패", e);
        }

        String encoded = URLEncoder.encode(json, StandardCharsets.UTF_8);
        return ResponseCookie.from("it-portal-user", encoded)
                .httpOnly(false) // 프론트엔드 JavaScript에서 읽어야 함
                .secure(secureCookie)
                .path("/")
                .maxAge(REFRESH_TOKEN_MAX_AGE) // refreshToken 수명과 일치 (7일)
                .sameSite("Lax")
                .build();
    }

    /**
     * 프론트엔드 인증 상태 복원용 사용자 정보 쿠키를 삭제합니다.
     *
     * @return 생성 쿠키와 같은 속성에 {@code maxAge=0}을 적용한 삭제용 쿠키
     */
    public ResponseCookie deleteUserInfoCookie() {
        return ResponseCookie.from("it-portal-user", "")
                .httpOnly(false)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }
}
