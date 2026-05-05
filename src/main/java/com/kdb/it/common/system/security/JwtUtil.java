package com.kdb.it.common.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 토큰 생성 및 검증 유틸리티 클래스
 *
 * <p>
 * JSON Web Token(JWT) 기반의 Access Token과 Refresh Token을
 * 생성하고 검증하는 유틸리티입니다.
 * </p>
 *
 * <p>
 * JWT 구조: {@code Header}.{@code Payload}.{@code Signature}
 * </p>
 * <ul>
 * <li>Header: 알고리즘 정보 ({@code HS256} 등 HMAC-SHA 계열)</li>
 * <li>Payload(Claims): subject(사번), issuedAt(발급 시각), expiration(만료 시각)</li>
 * <li>Signature: secretKey로 서명한 값 (위변조 방지)</li>
 * </ul>
 *
 * <p>
 * 설정값 ({@code application.properties}):
 * </p>
 * <ul>
 * <li>{@code jwt.secret}: HMAC-SHA 서명 비밀키 (최소 256비트 = 32자 이상 권장)</li>
 * <li>{@code jwt.access-token-validity}: Access Token 유효시간 (밀리초)</li>
 * <li>{@code jwt.refresh-token-validity}: Refresh Token 유효시간 (밀리초)</li>
 * </ul>
 *
 * <p>
 * 사용 라이브러리: {@code io.jsonwebtoken:jjwt} (JJWT)
 * </p>
 */
@Component // Spring 컴포넌트 빈으로 등록
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /**
     * HMAC-SHA256 서명에 사용할 비밀키
     * {@code application.properties}의 {@code jwt.secret} 값을 UTF-8 바이트로 변환하여 생성
     */
    private final SecretKey secretKey;

    /** Access Token 유효시간 (밀리초, 기본 900000 = 15분) */
    private final long accessTokenValidityMs;

    /** Refresh Token 유효시간 (밀리초, 예: 604800000 = 7일) */
    private final long refreshTokenValidityMs;

    /**
     * 생성자: Spring이 설정 파일의 값을 주입하여 유틸리티를 초기화합니다.
     *
     * <p>
     * {@code @Value}로 {@code application.properties}에서 설정값을 읽어옵니다.
     * </p>
     *
     * @param secret                 JWT 서명용 비밀키 문자열
     * @param accessTokenValidityMs  Access Token 유효시간 (밀리초)
     * @param refreshTokenValidityMs Refresh Token 유효시간 (밀리초)
     */
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-validity}") long refreshTokenValidityMs) {
        // 비밀키 문자열을 HMAC-SHA용 SecretKey 객체로 변환
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    /**
     * Access Token 생성 (자격등급 및 부서코드 클레임 포함)
     *
     * <p>
     * 사번을 subject로 하며, 자격등급 목록(athIds)과 부서코드(bbrC)를 클레임에 추가합니다.
     * 한 사용자가 여러 자격등급을 가질 수 있으므로 athIds는 JSON 배열로 직렬화됩니다.
     * </p>
     *
     * <p>
     * Claims 구성:
     * </p>
     * <ul>
     * <li>{@code sub}: 사번 (subject)</li>
     * <li>{@code athIds}: 자격등급 ID 목록 (JSON 배열, 예: ["ITPZZ001", "ITPZZ002"])</li>
     * <li>{@code bbrC}: 소속 부서코드 (권한 범위 결정용)</li>
     * <li>{@code iat}: 발급 시각 (issued at)</li>
     * <li>{@code exp}: 만료 시각 (expiration)</li>
     * </ul>
     *
     * @param eno    토큰의 subject로 사용할 사번
     * @param athIds 자격등급 ID 목록 (null/빈 리스트이면 ITPZZ001 기본값 적용)
     * @param bbrC   소속 부서코드
     * @return 서명된 JWT Access Token 문자열
     */
    public String generateAccessToken(String eno, List<String> athIds, String bbrC) {
        Date now        = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenValidityMs);

        // athIds가 null/빈 리스트이면 기본값 일반사용자 적용
        List<String> effectiveAthIds = (athIds != null && !athIds.isEmpty())
            ? athIds
            : List.of(CustomUserDetails.ATH_USER);

        return Jwts.builder()
                .subject(eno)                          // sub 클레임: 사번
                .claim("athIds", effectiveAthIds)      // 자격등급 목록 클레임 (JSON 배열)
                .claim("bbrC",   bbrC)                 // 소속 부서코드 클레임
                .issuedAt(now)                         // iat 클레임: 발급 시각
                .expiration(expiryDate)                // exp 클레임: 만료 시각
                .signWith(secretKey)                   // HMAC-SHA 알고리즘으로 서명
                .compact();                            // JWT 문자열로 직렬화
    }

    /**
     * Refresh Token 생성
     *
     * <p>
     * 사번을 subject로 하는 장기 유효 JWT Refresh Token을 생성합니다.
     * Access Token 만료 시 새로운 Access Token을 발급받는 데 사용합니다.
     * </p>
     *
     * <p>
     * Access Token과 동일한 구조지만 유효시간이 더 깁니다 (기본 7일).
     * </p>
     *
     * @param eno 토큰의 subject로 사용할 사번
     * @return 서명된 JWT Refresh Token 문자열
     */
    public String generateRefreshToken(String eno) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenValidityMs); // 7일 후 만료

        return Jwts.builder()
                .subject(eno)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * JWT 토큰에서 자격등급 ID 목록 추출
     *
     * <p>
     * JJWT는 JSON 배열 클레임을 {@code List<String>}으로 역직렬화합니다.
     * 클레임이 없거나 파싱 실패 시 빈 리스트를 반환합니다.
     * </p>
     *
     * @param token JWT 토큰 문자열
     * @return 자격등급 ID 목록 (없으면 빈 리스트)
     */
    @SuppressWarnings("unchecked")
    public List<String> getAthIdsFromToken(String token) {
        Object claim = getClaims(token).get("athIds");
        if (claim instanceof List<?>) {
            return (List<String>) claim;
        }
        return List.of();
    }

    /**
     * JWT 토큰에서 소속 부서코드 추출
     *
     * @param token JWT 토큰 문자열
     * @return 소속 부서코드 (클레임 없으면 null)
     */
    public String getBbrCFromToken(String token) {
        return (String) getClaims(token).get("bbrC");
    }

    /**
     * JWT 토큰에서 사번 추출
     *
     * <p>
     * 토큰의 Payload에서 {@code subject} 클레임(사번)을 추출합니다.
     * 토큰 서명 검증 후 Claims를 파싱합니다.
     * </p>
     *
     * <p>
     * 주의: 이 메서드는 토큰 검증 없이 직접 호출하면 안 됩니다.
     * 반드시 {@link #validateToken(String)} 호출 후 사용해야 합니다.
     * </p>
     *
     * @param token JWT 토큰 문자열
     * @return 토큰의 subject 클레임 값 (사번)
     * @throws io.jsonwebtoken.JwtException 토큰이 유효하지 않은 경우
     */
    public String getEnoFromToken(String token) {
        return getClaims(token).getSubject(); // sub 클레임(사번) 반환
    }

    /**
     * 내부 헬퍼: JWT 토큰에서 Claims(Payload) 파싱
     *
     * @param token JWT 토큰 문자열
     * @return 파싱된 {@link Claims} 객체
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * JWT 토큰 유효성 검증
     *
     * <p>
     * 토큰의 서명, 형식, 만료 여부를 검증합니다.
     * {@link JwtAuthenticationFilter}에서 모든 요청의 토큰을 검증하는 데 사용됩니다.
     * </p>
     *
     * <p>
     * 검증 항목:
     * </p>
     * <ul>
     * <li>서명 유효성: secretKey로 서명된 토큰인지 확인</li>
     * <li>토큰 형식: Header.Payload.Signature 구조 확인</li>
     * <li>만료 여부: exp 클레임과 현재 시각 비교</li>
     * <li>지원 여부: JWS(서명된 JWT) 형식인지 확인</li>
     * </ul>
     *
     * @param token 검증할 JWT 토큰 문자열
     * @return true이면 유효한 토큰, false이면 유효하지 않은 토큰
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token); // 파싱 성공 시 토큰 유효
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | io.jsonwebtoken.MalformedJwtException e) {
            // 잘못된 서명 또는 JWT 형식 오류
            log.warn("JWT 토큰 검증 실패 - 잘못된 서명 또는 형식: {}", e.getMessage());
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // 토큰 만료: exp 클레임의 시각이 현재 시각보다 이전
            log.warn("JWT 토큰 검증 실패 - 만료된 토큰: {}", e.getMessage());
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            // JWE(암호화) 등 지원하지 않는 JWT 유형
            log.warn("JWT 토큰 검증 실패 - 지원하지 않는 토큰: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            // 빈 문자열 또는 null 토큰
            log.warn("JWT 토큰 검증 실패 - 빈 토큰: {}", e.getMessage());
        } catch (Exception e) {
            // 기타 예상치 못한 예외
            log.error("JWT 토큰 검증 실패 - 알 수 없는 오류: {}", e.getMessage(), e);
        }
        return false; // 예외 발생 시 유효하지 않은 토큰으로 처리
    }

}
