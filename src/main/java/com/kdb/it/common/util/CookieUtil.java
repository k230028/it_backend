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
