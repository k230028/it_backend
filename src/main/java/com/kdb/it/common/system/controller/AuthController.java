package com.kdb.it.common.system.controller;

import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.util.CookieUtil;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 인증(Authentication) REST 컨트롤러
 *
 * <p>
 * 관리자 계정 생성, 로그인, 로그아웃, JWT 토큰 갱신 기능을 담당합니다.
 * </p>
 *
 * <p>
 * 기본 URL: {@code /api/auth}
 * </p>
 *
 * <p>
 * JWT 토큰은 httpOnly 쿠키로 전달됩니다 (XSS 토큰 탈취 방지).
 * </p>
 *
 * <p>
 * 인증 불필요 공개 엔드포인트 (SecurityConfig에서 permitAll 설정):
 * </p>
 * <ul>
 * <li>{@code POST /api/auth/login}: 로그인</li>
 * <li>{@code POST /api/auth/refresh}: 토큰 갱신</li>
 * </ul>
 *
 * <p>
 * 인증 필요 엔드포인트:
 * </p>
 * <ul>
 * <li>{@code POST /api/auth/logout}: 로그아웃 (JWT 토큰 필요)</li>
 * <li>{@code POST /api/auth/signup}: 관리자 권한으로 신규 사용자 생성</li>
 * </ul>
 */
@RestController // REST API 컨트롤러로 등록
@RequestMapping("/api/auth") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Tag(name = "Auth", description = "인증 API") // Swagger UI 그룹 태그
public class AuthController {

    /** 인증 비즈니스 로직 서비스 */
    private final AuthService authService;

    /** JWT 토큰 쿠키 관리 유틸리티 */
    private final CookieUtil cookieUtil;

    /**
     * 회원가입
     *
     * <p>
     * 새로운 사용자 계정을 생성합니다.
     * 비밀번호는 SHA-256으로 암호화하여 DB에 저장됩니다.
     * </p>
     *
     * @param request 회원가입 요청 (사번, 이름, 비밀번호)
     * @return HTTP 200 + "회원가입 성공" 메시지
     */
    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "회원가입을 합니다.")
    public ResponseEntity<String> signup(@Valid @RequestBody AuthDto.SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok("회원가입 성공");
    }

    /**
     * 로그인 및 JWT 토큰 발급
     *
     * <p>
     * 사번과 비밀번호로 인증 후 Access Token과 Refresh Token을
     * httpOnly 쿠키로 발급합니다.
     * </p>
     *
     * <p>
     * 처리 흐름:
     * </p>
     * <ol>
     * <li>클라이언트 IP 주소 및 User-Agent 추출</li>
     * <li>AuthService에서 사용자 인증 수행</li>
     * <li>Access Token → httpOnly 쿠키 (Set-Cookie 헤더)</li>
     * <li>Refresh Token → httpOnly 쿠키 (Set-Cookie 헤더)</li>
     * <li>응답 body에는 사번, 이름만 포함 (토큰 미포함)</li>
     * </ol>
     *
     * @param request     로그인 요청 (사번, 비밀번호)
     * @param httpRequest HTTP 요청 객체 (IP, User-Agent 추출용)
     * @return HTTP 200 + Set-Cookie(accessToken, refreshToken) + body(eno, empNm)
     */
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "로그인하여 JWT 토큰을 httpOnly 쿠키로 발급받습니다.")
    public ResponseEntity<AuthDto.LoginResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest request,
            HttpServletRequest httpRequest) {
        // 클라이언트의 실제 IP 주소 추출 (프록시 환경 고려)
        String ipAddress = getClientIp(httpRequest);
        // 클라이언트 브라우저/기기 정보
        String userAgent = httpRequest.getHeader("User-Agent");

        // 인증 처리 및 토큰 발급 (LoginResponse에 토큰 포함, body 직렬화 시 @JsonIgnore)
        AuthDto.LoginResponse response = authService.login(
                request.getEno(), request.getPassword(), ipAddress, userAgent);

        // Access Token, Refresh Token을 httpOnly 쿠키로 설정
        ResponseCookie accessCookie = cookieUtil.createAccessTokenCookie(response.getAccessToken());
        ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(response.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response); // body에는 eno, empNm만 포함 (@JsonIgnore로 토큰 제외)
    }

    /**
     * Access Token 갱신
     *
     * <p>
     * 만료된 Access Token 대신 유효한 Refresh Token(쿠키)을 사용하여
     * 새로운 Access Token을 httpOnly 쿠키로 발급받습니다.
     * </p>
     *
     * <p>
     * 처리 흐름:
     * </p>
     * <ol>
     * <li>요청 쿠키에서 Refresh Token 추출</li>
     * <li>JwtUtil로 토큰 서명 검증</li>
     * <li>DB(TAAABB_CRTOKM 테이블)에서 토큰 조회</li>
     * <li>토큰 만료 여부 확인</li>
     * <li>새 Access Token 생성 → httpOnly 쿠키로 전달</li>
     * </ol>
     *
     * @param httpRequest HTTP 요청 객체 (쿠키에서 Refresh Token 추출)
     * @return HTTP 200 + Set-Cookie(새 accessToken) + "토큰 갱신 성공"
     */
    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "Refresh Token 쿠키를 사용하여 새로운 Access Token을 발급받습니다.")
    public ResponseEntity<String> refresh(HttpServletRequest httpRequest) {
        // 쿠키에서 Refresh Token 추출
        String refreshToken = extractCookieValue(httpRequest, CookieUtil.REFRESH_TOKEN_COOKIE);

        if (refreshToken == null) {
            return ResponseEntity.status(401).body("Refresh Token 쿠키가 없습니다.");
        }

        // Refresh Token 검증 및 새 Access Token 발급
        AuthDto.RefreshResponse response = authService.refreshAccessToken(refreshToken);

        // 새 Access Token을 httpOnly 쿠키로 설정
        ResponseCookie accessCookie = cookieUtil.createAccessTokenCookie(response.getAccessToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .body("토큰 갱신 성공");
    }

    /**
     * 로그아웃
     *
     * <p>
     * 현재 로그인한 사용자의 Refresh Token을 DB에서 삭제하여 무효화하고,
     * Access Token과 Refresh Token 쿠키를 즉시 만료시킵니다.
     * </p>
     *
     * @param httpRequest HTTP 요청 객체 (IP, User-Agent 추출 및 이력 기록용)
     * @return HTTP 200 + Set-Cookie(삭제) + "로그아웃 성공"
     */
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "쿠키의 JWT 토큰을 삭제하고 Refresh Token을 무효화합니다.")
    public ResponseEntity<String> logout(HttpServletRequest httpRequest) {
        // SecurityContextHolder에서 현재 인증된 사용자 정보 조회
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            // JWT에서 추출된 사번
            String eno = authentication.getName();
            String ipAddress = getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            // Refresh Token 삭제 및 로그아웃 이력 기록
            authService.logout(eno, ipAddress, userAgent);
        }

        // Access Token, Refresh Token 쿠키를 즉시 만료시켜 삭제
        ResponseCookie deleteAccess = cookieUtil.deleteAccessTokenCookie();
        ResponseCookie deleteRefresh = cookieUtil.deleteRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteAccess.toString())
                .header(HttpHeaders.SET_COOKIE, deleteRefresh.toString())
                .body("로그아웃 성공");
    }

    /**
     * HTTP 요청 쿠키에서 특정 쿠키 값 추출
     *
     * @param request    HTTP 요청 객체
     * @param cookieName 추출할 쿠키 이름
     * @return 쿠키 값 (없으면 null)
     */
    private String extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 클라이언트 IP 주소 추출
     *
     * <p>
     * 로드밸런서, 리버스 프록시(Nginx, Apache), CDN 등을 경유한
     * 요청에서 실제 클라이언트 IP를 추출합니다. 아래 헤더는 프록시가 신뢰 가능한 방식으로
     * 덮어쓴다는 전제에서만 안전하며, 직접 인터넷에 노출된 환경에서는 위조될 수 있습니다.
     * </p>
     *
     * <p>
     * 헤더 우선순위 (앞에서부터 순서대로 확인):
     * </p>
     * <ol>
     * <li>{@code X-Forwarded-For}: 표준 프록시 헤더 (여러 IP가 있으면 첫 번째가 원래 IP)</li>
     * <li>{@code Proxy-Client-IP}: 일부 프록시에서 사용</li>
     * <li>{@code WL-Proxy-Client-IP}: WebLogic 프록시에서 사용</li>
     * <li>{@code HTTP_CLIENT_IP}: 일부 환경에서 사용</li>
     * <li>{@code HTTP_X_FORWARDED_FOR}: 비표준 헤더</li>
     * <li>{@code request.getRemoteAddr()}: 직접 연결 IP (프록시 없는 경우)</li>
     * </ol>
     *
     * @param request HTTP 요청 객체
     * @return 클라이언트의 실제 IP 주소 문자열
     */
    private String getClientIp(HttpServletRequest request) {
        // X-Forwarded-For 헤더: 표준 프록시/로드밸런서 클라이언트 IP 헤더
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 프록시 없이 직접 연결된 경우의 IP
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
