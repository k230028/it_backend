package com.kdb.it.common.system.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JwtUtil 단위 테스트
 *
 * <p>
 * Spring Context 없이 직접 생성자를 호출하여 Oracle DB 연결 없이 테스트합니다.
 * 
 * @Value 의존성은 테스트용 고정 값으로 직접 주입합니다.
 *        </p>
 */
class JwtUtilTest {

    /** 테스트용 고정 시크릿 키 (HMAC-SHA256 최소 256bit 요구) */
    private static final String TEST_SECRET = "test-secret-key-for-junit-test-minimum-256-bits-length-ok";
    private static final long ACCESS_VALIDITY_MS = 900_000L; // 15분
    private static final long REFRESH_VALIDITY_MS = 604_800_000L; // 7일
    private static final long EXPIRED_VALIDITY_MS = 1L; // 즉시 만료 (1ms)

    /** 테스트용 기본 자격등급 목록 */
    private static final List<String> TEST_ATH_IDS = List.of("ITPZZ001");
    /** 테스트용 기본 부서코드 */
    private static final String TEST_BBR_C = "BBR001";

    private JwtUtil jwtUtil;
    private JwtUtil expiredJwtUtil; // 만료 토큰 생성 전용

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, ACCESS_VALIDITY_MS, REFRESH_VALIDITY_MS);
        expiredJwtUtil = new JwtUtil(TEST_SECRET, EXPIRED_VALIDITY_MS, EXPIRED_VALIDITY_MS);
    }

    @Test
    @DisplayName("Access Token 생성 - 사번 입력 시 유효한 JWT 형식 토큰 반환")
    void generateAccessToken_사번입력_유효한토큰반환() {
        // given
        String eno = "10001";

        // when
        String token = jwtUtil.generateAccessToken(eno, TEST_ATH_IDS, TEST_BBR_C);

        // then
        assertThat(token).isNotNull().isNotEmpty();
        // JWT 형식 검증: header.payload.signature 3부분
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Access Token 생성 후 사번 추출 - 입력 사번과 동일")
    void generateAccessToken_토큰에서사번추출() {
        // given
        String eno = "10001";
        String token = jwtUtil.generateAccessToken(eno, TEST_ATH_IDS, TEST_BBR_C);

        // when
        String extractedEno = jwtUtil.getEnoFromToken(token);

        // then
        assertThat(extractedEno).isEqualTo(eno);
    }

    @Test
    @DisplayName("Access Token 생성 후 자격등급과 부서코드 클레임 추출")
    void generateAccessToken_권한부서클레임추출() {
        // given
        List<String> athIds = List.of("ITPAD001", "ITPZZ002");
        String token = jwtUtil.generateAccessToken("10001", athIds, TEST_BBR_C);

        // when & then
        assertThat(jwtUtil.getAthIdsFromToken(token)).containsExactlyElementsOf(athIds);
        assertThat(jwtUtil.getBbrCFromToken(token)).isEqualTo(TEST_BBR_C);
    }

    @Test
    @DisplayName("Access Token 생성 - 자격등급이 비어 있으면 일반사용자 기본값 적용")
    void generateAccessToken_빈자격등급_일반사용자기본값() {
        // given
        String token = jwtUtil.generateAccessToken("10001", List.of(), TEST_BBR_C);

        // when & then
        assertThat(jwtUtil.getAthIdsFromToken(token))
            .containsExactly(CustomUserDetails.ATH_USER);
    }

    @Test
    @DisplayName("Access Token 생성 - 자격등급이 null이면 일반사용자 기본값 적용")
    void generateAccessToken_null자격등급_일반사용자기본값() {
        String token = jwtUtil.generateAccessToken("10001", null, TEST_BBR_C);

        assertThat(jwtUtil.getAthIdsFromToken(token))
            .containsExactly(CustomUserDetails.ATH_USER);
    }

    @Test
    @DisplayName("Refresh Token 생성 - 유효한 토큰 반환 및 사번 추출 가능")
    void generateRefreshToken_사번입력_유효한토큰반환() {
        // given
        String eno = "10001";

        // when
        String token = jwtUtil.generateRefreshToken(eno);

        // then
        assertThat(token).isNotNull().isNotEmpty();
        assertThat(jwtUtil.getEnoFromToken(token)).isEqualTo(eno);
    }

    @Test
    @DisplayName("Refresh Token 생성 - 같은 초에 연속 발급해도 매번 고유한 토큰 반환")
    void generateRefreshToken_같은초연속발급_매번고유토큰() {
        // given
        String eno = "10001";
        int issueCount = 10;

        // when: 같은 초 안에서 연속 발급 (루프 전체가 수 ms 내 완료됨)
        java.util.Set<String> tokens = new java.util.HashSet<>();
        for (int i = 0; i < issueCount; i++) {
            tokens.add(jwtUtil.generateRefreshToken(eno));
        }

        // then: 모든 토큰이 서로 달라야 함 — 동일 토큰이 발급되면 TPRMPP_CRTOKM의
        // SHA-256 조회값 유니크 인덱스(UX_CRTOKM_ECY_RNW_PUB_TOK)와 충돌해 ORA-00001이 발생한다
        assertThat(tokens).hasSize(issueCount);
    }

    @Test
    @DisplayName("토큰 유효성 검증 - 유효한 토큰은 true 반환")
    void validateToken_유효한토큰_true반환() {
        // given
        String token = jwtUtil.generateAccessToken("10001", TEST_ATH_IDS, TEST_BBR_C);

        // when & then
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("토큰 유효성 검증 - 만료된 토큰은 false 반환")
    void validateToken_만료된토큰_false반환() throws InterruptedException {
        // given: 유효시간 1ms 토큰 생성 후 만료 대기
        String expiredToken = expiredJwtUtil.generateAccessToken("10001", TEST_ATH_IDS, TEST_BBR_C);
        Thread.sleep(10);

        // when & then
        assertThat(jwtUtil.validateToken(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("토큰 유효성 검증 - 위변조 토큰은 false 반환")
    void validateToken_위변조토큰_false반환() {
        // given
        String tamperedToken = "eyJhbGciOiJIUzI1NiJ9.tampered.invalid_signature";

        // when & then
        assertThat(jwtUtil.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("토큰 유효성 검증 - 빈 문자열은 false 반환")
    void validateToken_빈문자열_false반환() {
        assertThat(jwtUtil.validateToken("")).isFalse();
    }

    @Test
    @DisplayName("토큰 유효성 검증 - null 토큰은 false 반환")
    void validateToken_null_false반환() {
        assertThat(jwtUtil.validateToken(null)).isFalse();
    }

    @Test
    @DisplayName("Refresh Token에는 자격등급 클레임이 없으므로 빈 목록을 반환한다")
    void getAthIdsFromToken_클레임없음_빈목록반환() {
        String refreshToken = jwtUtil.generateRefreshToken("10001");

        assertThat(jwtUtil.getAthIdsFromToken(refreshToken)).isEmpty();
    }

    @Test
    @DisplayName("Access Token과 Refresh Token은 서로 다른 값 (유효시간 차이)")
    void accessToken과RefreshToken_서로다른값() {
        // given
        String eno = "10001";

        // when
        String accessToken = jwtUtil.generateAccessToken(eno, TEST_ATH_IDS, TEST_BBR_C);
        String refreshToken = jwtUtil.generateRefreshToken(eno);

        // then: 발급 시각이 같아도 exp 클레임이 다르므로 토큰 값도 다름
        assertThat(accessToken).isNotEqualTo(refreshToken);
    }
}
