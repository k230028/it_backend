package com.kdb.it.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.dto.AuthDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 토큰 쿠키 관리 유틸리티
 *
 * <p>
 * httpOnly 쿠키를 통해 JWT 토큰을 안전하게 전달/관리합니다.
 * XSS 공격 시 JavaScript로 토큰에 접근할 수 없어 토큰 탈취를 방지합니다.
 * </p>
 *
 * <p>
 * 쿠키 보안 속성:
 * </p>
 * <ul>
 * <li>{@code httpOnly}: true — JavaScript 접근 차단 (XSS 방어)</li>
 * <li>{@code secure}: 프로파일별 분기 (운영=true, 개발=false)</li>
 * <li>{@code sameSite}: Lax — CSRF 방어 + 외부 링크 네비게이션 허용</li>
 * <li>{@code path}: Access Token="/", Refresh Token="/api/auth"</li>
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

    /**
     * 쿠키 Secure 플래그
     * 개발 환경: false (HTTP 허용), 운영 환경: true (HTTPS만 허용)
     */
    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    /**
     * Access Token httpOnly 쿠키 생성
     *
     * <p>
     * 모든 API 경로({@code path="/"})에서 전송되며, 15분 후 만료됩니다.
     * </p>
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
     * <p>
     * 인증 경로({@code path="/api/auth"})에서만 전송되며, 7일 후 만료됩니다.
     * Refresh Token은 토큰 갱신/로그아웃 시에만 필요하므로 경로를 제한합니다.
     * </p>
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
     * Access Token 쿠키 삭제 (로그아웃 시 사용)
     *
     * <p>
     * {@code maxAge=0}으로 설정하여 브라우저에서 즉시 삭제됩니다.
     * </p>
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
     * <p>
     * {@code maxAge=0}으로 설정하여 브라우저에서 즉시 삭제됩니다.
     * </p>
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

    /**
     * SSO 복귀 경로(next) 쿠키를 생성합니다.
     *
     * <p>SSO 시작({@code /sso/business}) 시 원본 요청 경로를 쿠키에 보관해, 인증 완료
     * ({@code /api/auth/sso/complete})까지 운반합니다. ESSO 교차 출처 왕복(특히 CS 모드 POST 콜백)
     * 중에는 서버 세션이 끊길 수 있으나, 이 쿠키는 마지막 same-site complete 내비게이션(GET)에
     * 전달되므로 세션 유실과 무관하게 원본 URL을 복원할 수 있습니다. 값은 경로에 포함될 수 있는
     * 쿼리 문자({@code ?&=})를 보존하기 위해 URL 인코딩합니다.</p>
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
     * <p>Access Token과 Refresh Token은 httpOnly 쿠키라서 JavaScript가 읽을 수 없습니다.
     * Nuxt 화면에서는 현재 사용자명, 권한, 부서 코드가 필요하므로 민감 토큰이 아닌 최소 사용자
     * 표시 정보만 {@code it-portal-user} 쿠키에 별도로 담습니다.</p>
     *
     * <p>이 쿠키는 SSO 완료 직후 서버가 직접 내려보내며, 프론트엔드의
     * {@code useCookie('it-portal-user')}와 {@code stores/auth.ts}가 읽어 Pinia 인증 상태를
     * 복원합니다. 값 형식은 Nuxt 쿠키 저장 방식과 맞추기 위해 JSON 직렬화 후 URL 인코딩합니다.</p>
     *
     * <p>보안상 이 쿠키에는 JWT나 비밀번호 같은 인증 비밀값을 넣으면 안 됩니다.
     * 실제 API 인증은 별도 httpOnly Access Token 쿠키로 수행됩니다.</p>
     *
     * @param loginResponse SSO 토큰 발급 후 사용자/권한 정보가 담긴 응답 DTO
     * @return Nuxt가 읽을 수 있는 {@code it-portal-user} 쿠키
     */
    public ResponseCookie createUserInfoCookie(AuthDto.LoginResponse loginResponse) {
        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("eno",    loginResponse.getEno());
        userMap.put("empNm",  loginResponse.getEmpNm());
        userMap.put("athIds", loginResponse.getAthIds());
        userMap.put("bbrC",   loginResponse.getBbrC());
        userMap.put("temC",   loginResponse.getTemC());

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
}
