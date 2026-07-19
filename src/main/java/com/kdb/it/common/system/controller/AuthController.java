package com.kdb.it.common.system.controller;

import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.exception.InvalidRefreshTokenException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
     * @param authService      인증 서비스
     * @param cookieUtil       토큰 쿠키 유틸리티
     * @param trustedProxiesCsv 신뢰 프록시 IP 목록(CSV). 비어 있으면 XFF를 신뢰하지 않음.
     */
    public AuthController(
            AuthService authService,
            CookieUtil cookieUtil,
            @org.springframework.beans.factory.annotation.Value("${app.trusted-proxies:}") String trustedProxiesCsv) {
        this.authService = authService;
        this.cookieUtil = cookieUtil;
        this.trustedProxies = (trustedProxiesCsv == null || trustedProxiesCsv.isBlank())
                ? java.util.Set.of()
                : java.util.Arrays.stream(trustedProxiesCsv.split(","))
                        .map(value -> value.trim()).filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

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
     * <li>응답 body에는 eno, empNm, athIds, bbrC, temC 포함 (accessToken/refreshToken은 @JsonIgnore로 제외)</li>
     * </ol>
     *
     * @param request     로그인 요청 (사번, 비밀번호)
     * @param httpRequest HTTP 요청 객체 (IP, User-Agent 추출용)
     * @return HTTP 200 + Set-Cookie(accessToken, refreshToken) + body(eno, empNm, athIds, bbrC, temC)
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
                .body(response); // body에는 eno, empNm, athIds, bbrC, temC 포함 (accessToken/refreshToken만 @JsonIgnore로 제외)
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
     * <li>DB(TPRMPP_CRTOKM 테이블)에서 토큰 조회</li>
     * <li>토큰 만료 여부 확인</li>
     * <li>새 Access Token 생성 → httpOnly 쿠키로 전달</li>
     * </ol>
     *
     * <p>
     * Refresh 쿠키가 없거나 검증에 실패({@link InvalidRefreshTokenException})하면 HTTP 401과 함께
     * Access·Refresh 쿠키를 모두 삭제({@link #unauthorizedRefreshResponse()})하여 재로그인을 유도합니다.
     * </p>
     *
     * @param httpRequest HTTP 요청 객체 (쿠키에서 Refresh Token 추출)
     * @return 성공 시 HTTP 200 + Set-Cookie(새 accessToken, 회전 시 refreshToken) + "토큰 갱신 성공";
     *         실패 시 HTTP 401 + Access/Refresh 삭제 Set-Cookie 2개 + "다시 로그인해 주세요."
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

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString());

        // 회전된 Refresh Token이 있으면 로그인과 동일 정책으로 쿠키 재설정 (탈취 재사용 방어)
        if (response.getRefreshToken() != null) {
            ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(response.getRefreshToken());
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }

        return builder.body("토큰 갱신 성공");
    }

    /**
     * 잘못된 Refresh 요청을 HTTP 401과 인증 쿠키 삭제로 응답합니다.
     *
     * <p>Refresh 쿠키가 없거나 {@link InvalidRefreshTokenException}이 발생한 경우 호출됩니다.
     * Access·Refresh 쿠키를 모두 만료시켜(Max-Age=0) 브라우저에서 제거하고 재로그인을 유도합니다.
     * 응답 본문에는 재로그인 안내 문구만 노출하고 토큰 값이나 내부 원인은 남기지 않습니다.
     * 쿠키 속성은 로그아웃과 동일하게 {@code CookieUtil}의 삭제 헬퍼를 재사용합니다.</p>
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
     * 요청에서 실제 클라이언트 IP를 추출합니다. {@code X-Forwarded-For}는 위조 가능하므로,
     * 직접 연결한 프록시({@code remoteAddr})가 {@code app.trusted-proxies} allowlist에
     * 포함된 경우에만 신뢰하고, 멀티 IP는 최좌측(원 클라이언트)만 사용합니다.
     * </p>
     *
     * @param request HTTP 요청 객체
     * @return 클라이언트의 실제 IP 주소 문자열
     */
    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request, trustedProxies);
    }
}
