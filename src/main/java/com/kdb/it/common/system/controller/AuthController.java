package com.kdb.it.common.system.controller;

import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.exception.InvalidRefreshTokenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증(Authentication) REST 컨트롤러
 *
 * <p>관리자 계정 생성, MFA 수동 로그인, 로그아웃, JWT 토큰 갱신 기능을 담당합니다.
 *
 * <p>기본 URL: {@code /api/auth}
 *
 * <p>JWT 토큰은 httpOnly 쿠키로 전달됩니다 (XSS 토큰 탈취 방지).
 *
 * <p>인증 불필요 공개 엔드포인트 (SecurityConfig에서 permitAll 설정):
 *
 * <ul>
 *   <li>{@code POST /api/auth/login/start}: 수동 로그인 시작
 *   <li>{@code POST /api/auth/login/complete}: MFA 검증 뒤 수동 로그인 완료
 *   <li>{@code POST /api/auth/refresh}: 토큰 갱신
 * </ul>
 *
 * <p>인증 필요 엔드포인트:
 *
 * <ul>
 *   <li>{@code POST /api/auth/logout}: 로그아웃 (JWT 토큰 필요)
 *   <li>{@code POST /api/auth/signup}: 관리자 권한으로 신규 사용자 생성
 * </ul>
 */
@RestController // REST API 컨트롤러로 등록
@RequestMapping("/api/auth") // 기본 URL 경로 설정
@Tag(name = "Auth", description = "인증 API") // Swagger UI 그룹 태그
public class AuthController {

    /** 인증 비즈니스 로직 서비스 */
    private final AuthService authService;

    /** JWT 토큰 쿠키 관리 유틸리티 */
    private final CookieUtil cookieUtil;

    /** 신뢰하는 역방향 프록시 IP 집합(운영 Nginx 등). 비어 있으면 XFF 미신뢰. 생성 시 1회 파싱. */
    private final java.util.Set<String> trustedProxies;

    /**
     * 생성자 — 신뢰 프록시 CSV를 {@code app.trusted-proxies}에서 주입받아 1회 파싱합니다.
     *
     * @param authService 인증 서비스
     * @param cookieUtil 토큰 쿠키 유틸리티
     * @param trustedProxiesCsv 신뢰 프록시 IP 목록(CSV). 비어 있으면 XFF를 신뢰하지 않음.
     */
    public AuthController(
            AuthService authService,
            CookieUtil cookieUtil,
            @org.springframework.beans.factory.annotation.Value("${app.trusted-proxies:}")
                    String trustedProxiesCsv) {
        this.authService = authService;
        this.cookieUtil = cookieUtil;
        this.trustedProxies =
                (trustedProxiesCsv == null || trustedProxiesCsv.isBlank())
                        ? java.util.Set.of()
                        : java.util.Arrays.stream(trustedProxiesCsv.split(","))
                                .map(value -> value.trim())
                                .filter(s -> !s.isEmpty())
                                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * 회원가입
     *
     * <p>새로운 사용자 계정을 생성합니다. 비밀번호는 SHA-256으로 암호화하여 DB에 저장됩니다.
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
     * 수동 로그인 자격증명을 검증하고 MFA 대기 쿠키만 발급합니다.
     *
     * @param request 사번과 비밀번호
     * @param httpRequest 클라이언트 IP와 User-Agent를 추출할 현재 요청
     * @return MFA 로그인 대기 거래 식별자와 만료 시각
     */
    @PostMapping("/login/start")
    @Operation(summary = "수동 로그인 시작", description = "자격증명 검증 뒤 MFA 로그인 대기 거래만 등록합니다.")
    public ResponseEntity<AuthDto.LoginStartResponse> startLogin(
            @Valid @RequestBody AuthDto.LoginRequest request, HttpServletRequest httpRequest) {
        AuthDto.LoginStartResponse response =
                authService.startLogin(
                        request.getEno(),
                        request.getPassword(),
                        getClientIp(httpRequest),
                        httpRequest.getHeader("User-Agent"));
        ResponseCookie pendingCookie =
                cookieUtil.createLoginPendingCookie(
                        response.getPendingId().toString(), response.getExpiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, pendingCookie.toString())
                .body(response);
    }

    /**
     * MFA 검증을 마친 로그인 대기 거래를 완료하고 JWT 쿠키를 발급합니다.
     *
     * @param httpRequest 로그인 대기 및 MFA 증표 쿠키를 추출할 현재 요청
     * @return 기존 로그인 응답과 Access·Refresh JWT 쿠키
     */
    @PostMapping("/login/complete")
    @Operation(summary = "수동 로그인 완료", description = "검증된 LOGIN MFA 증표를 한 번 소비한 뒤 JWT를 발급합니다.")
    public ResponseEntity<AuthDto.LoginResponse> completeLogin(HttpServletRequest httpRequest) {
        AuthDto.LoginResponse response =
                authService.completeLogin(
                        extractCookieValue(httpRequest, CookieUtil.LOGIN_PENDING_COOKIE),
                        extractCookieValue(httpRequest, CookieUtil.MFA_PROOF_COOKIE),
                        getClientIp(httpRequest),
                        httpRequest.getHeader("User-Agent"));
        ResponseCookie accessCookie = cookieUtil.createAccessTokenCookie(response.getAccessToken());
        ResponseCookie refreshCookie =
                cookieUtil.createRefreshTokenCookie(response.getRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

    /**
     * 현재 Access Token으로 화면 인증 상태를 복원합니다.
     *
     * <p>SSO 완료 직후 화면용 {@code it-portal-user} 쿠키가 Nuxt 상태에 반영되지 않았거나 사용자가 해당 쿠키만 삭제한 경우에도, 서버가 검증한
     * Access Token의 사번으로 최신 사용자 정보를 반환합니다. 인증되지 않은 요청은 Spring Security에서 401로 거부합니다.
     *
     * @param currentUser Access Token 검증으로 생성된 현재 사용자
     * @return 화면 세션 복원에 필요한 사용자·권한·소속 정보
     */
    @GetMapping("/session")
    @Operation(summary = "현재 인증 세션 조회", description = "Access Token으로 화면 인증 상태를 복원합니다.")
    public ResponseEntity<AuthDto.LoginResponse> session(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(authService.getSessionUser(currentUser.getEno()));
    }

    /**
     * Access Token 갱신
     *
     * <p>만료된 Access Token 대신 유효한 Refresh Token(쿠키)을 사용하여 새로운 Access Token을 httpOnly 쿠키로 발급받습니다.
     *
     * <p>처리 흐름:
     *
     * <ol>
     *   <li>요청 쿠키에서 Refresh Token 추출
     *   <li>JwtUtil로 토큰 서명 검증
     *   <li>DB(TPRMPP_CRTOKM 테이블)에서 토큰 조회
     *   <li>토큰 만료 여부 확인
     *   <li>새 Access Token 생성 → httpOnly 쿠키로 전달
     * </ol>
     *
     * <p>Refresh 쿠키가 없거나 검증에 실패({@link InvalidRefreshTokenException})하면 HTTP 401과 함께 Access·Refresh
     * 쿠키를 모두 삭제({@link #unauthorizedRefreshResponse()})하여 재로그인을 유도합니다.
     *
     * @param httpRequest HTTP 요청 객체 (쿠키에서 Refresh Token 추출)
     * @return 성공 시 HTTP 200 + Set-Cookie(새 accessToken, 회전 시 refreshToken) + "토큰 갱신 성공"; 실패 시 HTTP
     *     401 + Access/Refresh 삭제 Set-Cookie 2개 + "다시 로그인해 주세요."
     */
    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "Refresh Token 쿠키를 사용하여 새로운 Access Token을 발급받습니다.")
    public ResponseEntity<String> refresh(HttpServletRequest httpRequest) {
        // 쿠키에서 Refresh Token 추출
        String refreshToken = extractCookieValue(httpRequest, CookieUtil.REFRESH_TOKEN_COOKIE);

        if (refreshToken == null) {
            // Refresh 쿠키가 없으면 재로그인 유도: 401 + Access·Refresh 쿠키 모두 삭제
            return unauthorizedRefreshResponse();
        }

        // Refresh Token 검증 및 새 Access Token 발급 (Refresh Token 회전 포함)
        final AuthDto.RefreshResponse response;
        try {
            response = authService.refreshAccessToken(refreshToken);
        } catch (InvalidRefreshTokenException exception) {
            // 잘못된 Refresh 토큰: 401 + Access·Refresh 쿠키 모두 삭제로 재로그인 유도
            return unauthorizedRefreshResponse();
        }

        // 새 Access Token을 httpOnly 쿠키로 설정
        ResponseCookie accessCookie = cookieUtil.createAccessTokenCookie(response.getAccessToken());

        ResponseEntity.BodyBuilder builder =
                ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessCookie.toString());

        // 회전된 Refresh Token이 있으면 로그인과 동일 정책으로 쿠키 재설정 (탈취 재사용 방어)
        if (response.getRefreshToken() != null) {
            ResponseCookie refreshCookie =
                    cookieUtil.createRefreshTokenCookie(response.getRefreshToken());
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }

        return builder.body("토큰 갱신 성공");
    }

    /**
     * 잘못된 Refresh 요청을 HTTP 401과 인증 쿠키 삭제로 응답합니다.
     *
     * <p>Refresh 쿠키가 없거나 {@link InvalidRefreshTokenException}이 발생한 경우 호출됩니다. Access·Refresh 쿠키를 모두
     * 만료시켜(Max-Age=0) 브라우저에서 제거하고 재로그인을 유도합니다. 응답 본문에는 재로그인 안내 문구만 노출하고 토큰 값이나 내부 원인은 남기지 않습니다. 쿠키
     * 속성은 로그아웃과 동일하게 {@code CookieUtil}의 삭제 헬퍼를 재사용합니다.
     *
     * @return 401 응답 + Access/Refresh 삭제 Set-Cookie 2개 + 재로그인 안내 메시지
     */
    private ResponseEntity<String> unauthorizedRefreshResponse() {
        ResponseCookie deleteAccess = cookieUtil.deleteAccessTokenCookie();
        ResponseCookie deleteRefresh = cookieUtil.deleteRefreshTokenCookie();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, deleteAccess.toString())
                .header(HttpHeaders.SET_COOKIE, deleteRefresh.toString())
                .body("다시 로그인해 주세요.");
    }

    /**
     * 로그아웃
     *
     * <p>현재 로그인한 사용자의 Refresh Token을 DB에서 삭제하여 무효화하고, 서버 세션을 종료한 뒤 Access·Refresh·User 쿠키를 즉시
     * 만료시킵니다. 서버 토큰 폐기가 실패해도 로컬 세션과 쿠키 정리는 완료한 뒤 예외를 기존 전역 오류 계약으로 전파합니다.
     *
     * @param httpRequest HTTP 요청 객체 (IP, User-Agent 추출 및 이력 기록용)
     * @param httpResponse 서비스 처리 결과와 무관하게 로컬 인증 쿠키를 삭제할 응답 객체
     * @return HTTP 200 + Access·Refresh·User 삭제 Set-Cookie + "로그아웃 성공"
     */
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "쿠키의 JWT 토큰과 서버 세션을 삭제하고 Refresh Token을 무효화합니다.")
    public ResponseEntity<String> logout(
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = extractCookieValue(httpRequest, CookieUtil.REFRESH_TOKEN_COOKIE);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedEno =
                authentication != null
                                && authentication.isAuthenticated()
                                && !(authentication instanceof AnonymousAuthenticationToken)
                        ? authentication.getName()
                        : null;
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        try {
            if (refreshToken != null && !refreshToken.isBlank()) {
                authService.logoutByRefreshToken(
                        refreshToken, authenticatedEno, ipAddress, userAgent);
            } else if (authenticatedEno != null) {
                authService.logout(authenticatedEno, ipAddress, userAgent);
            }
        } finally {
            invalidateSession(httpRequest);
            addLogoutCookieHeaders(httpResponse);
        }

        return ResponseEntity.ok("로그아웃 성공");
    }

    /** Access·Refresh·User 쿠키를 서비스 처리 결과와 무관하게 즉시 만료시킵니다. */
    private void addLogoutCookieHeaders(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.deleteAccessTokenCookie().toString());
        response.addHeader(
                HttpHeaders.SET_COOKIE, cookieUtil.deleteRefreshTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.deleteUserInfoCookie().toString());
    }

    /** 현재 요청에 연결된 서버 세션을 새로 만들지 않고 안전하게 무효화합니다. */
    private static void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        try {
            synchronized (session) {
                session.invalidate();
            }
        } catch (IllegalStateException ignored) {
            // 이미 무효화된 세션은 추가 처리가 필요하지 않다.
        }
    }

    /**
     * HTTP 요청 쿠키에서 특정 쿠키 값 추출
     *
     * @param request HTTP 요청 객체
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
     * <p>로드밸런서, 리버스 프록시(Nginx, Apache), CDN 등을 경유한 요청에서 실제 클라이언트 IP를 추출합니다. {@code
     * X-Forwarded-For}는 위조 가능하므로, 직접 연결한 프록시({@code remoteAddr})가 {@code app.trusted-proxies}
     * allowlist에 포함된 경우에만 신뢰하고, 멀티 IP는 최좌측(원 클라이언트)만 사용합니다.
     *
     * @param request HTTP 요청 객체
     * @return 클라이언트의 실제 IP 주소 문자열
     */
    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request, trustedProxies);
    }
}
