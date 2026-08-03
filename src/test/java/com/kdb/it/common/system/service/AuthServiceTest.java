package com.kdb.it.common.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.LoginAttemptService;
import com.kdb.it.common.iam.service.UserRoleResolver;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.entity.Crtokm;
import com.kdb.it.common.system.exception.ConcurrentRefreshException;
import com.kdb.it.common.system.exception.FamilyRevocationRequiredException;
import com.kdb.it.common.system.exception.RefreshTokenNotFoundException;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import com.kdb.it.common.system.repository.RefreshTokenRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.exception.InvalidRefreshTokenException;
import com.kdb.it.exception.LoginRejectedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AuthService 단위 테스트
 *
 * <p>Mockito로 Repository, PasswordEncoder, JwtUtil을 Mock 처리하여 Oracle DB 연결 없이 비즈니스 로직만 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleResolver userRoleResolver;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginHistoryRepository loginHistoryRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenRotator refreshTokenRotator;
    @Mock private RefreshTokenRevoker refreshTokenRevoker;

    @Mock private LoginAttemptService loginAttemptService;

    @InjectMocks private AuthService authService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenValidityMs", 604_800_000L);
    }

    // ── 로그인 테스트 ──────────────────────────────────────────────────

    @Test
    @DisplayName("login - 성공 시 LoginResponse (eno, empNm, accessToken) 반환")
    void login_성공_LoginResponse반환() {
        // given
        CuserI user =
                CuserI.builder()
                        .eno("10001")
                        .usrNm("홍길동")
                        .usrEcyPwd("encodedPwd")
                        .delYn("N")
                        .build();

        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password", "encodedPwd")).willReturn(true);
        given(userRoleResolver.resolveAthIds("10001"))
                .willReturn(List.of(CustomUserDetails.ATH_USER));
        given(jwtUtil.generateAccessToken("10001", List.of(CustomUserDetails.ATH_USER), null))
                .willReturn("access-token");
        given(jwtUtil.generateRefreshToken("10001")).willReturn("refresh-token");

        // when
        AuthDto.LoginResponse response =
                authService.login("10001", "password", "127.0.0.1", "TestAgent");

        // then
        assertThat(response).isNotNull();
        assertThat(response.getEno()).isEqualTo("10001");
        assertThat(response.getEmpNm()).isEqualTo("홍길동");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("login - 존재하지 않는 사번 → LoginRejectedException 발생 및 실패 이력 1회 저장")
    void login_존재하지않는사번_예외발생() {
        // given
        given(userRepository.findByEno("99999")).willReturn(Optional.empty());

        // when & then: 커밋 대상 전용 예외(LoginRejectedException)로 통일 — SEC-09
        assertThatThrownBy(() -> authService.login("99999", "pwd", "127.0.0.1", "Agent"))
                .isInstanceOf(LoginRejectedException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        // 실패 이력은 정확히 1회만 저장되어야 한다 (같은 트랜잭션 내 저장 유지)
        verify(loginHistoryRepository, times(1)).save(any(Clognh.class));
    }

    @Test
    @DisplayName("login - 비밀번호 불일치 → LoginRejectedException 발생 및 실패 이력 1회 저장")
    void login_비밀번호불일치_예외발생() {
        // given
        CuserI user =
                CuserI.builder()
                        .eno("10001")
                        .usrNm("홍길동")
                        .usrEcyPwd("encodedPwd")
                        .delYn("N")
                        .build();

        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPwd", "encodedPwd")).willReturn(false);

        // when & then: 커밋 대상 전용 예외(LoginRejectedException)로 통일 — SEC-09
        assertThatThrownBy(() -> authService.login("10001", "wrongPwd", "127.0.0.1", "Agent"))
                .isInstanceOf(LoginRejectedException.class)
                .hasMessageContaining("비밀번호가 일치하지 않습니다");

        verify(loginHistoryRepository, times(1)).save(any(Clognh.class));
    }

    @Test
    @DisplayName("login - 계정 잠금 시 기존 잠금 예외를 유지하고 신규 실패 이력을 추가하지 않는다")
    void login_계정잠금_기존잠금예외유지_이력미추가() {
        // given: LoginAttemptService.checkLocked가 login() 진입 직후(사용자 조회 이전)에 호출되어
        // 잠금 판정 자체는 이번 SEC-09 변경으로 손대지 않는 기존 계약이다.
        CustomGeneralException lockException =
                new CustomGeneralException("계정 잠금: 10분 내 로그인 실패가 5회 이상입니다. 잠시 후 다시 시도하세요.");
        org.mockito.BDDMockito.willThrow(lockException)
                .given(loginAttemptService)
                .checkLocked("10001");

        // when & then: 잠금 예외는 LoginRejectedException으로 변환되지 않고 그대로 전파된다.
        assertThatThrownBy(() -> authService.login("10001", "pwd", "127.0.0.1", "Agent"))
                .isSameAs(lockException)
                .isNotInstanceOf(LoginRejectedException.class);

        // 잠금 시점에는 실패 이력을 추가로 남기지 않는다 (사용자 조회 이전에 차단되므로 DB 접근 없음).
        verifyNoInteractions(loginHistoryRepository, userRepository);
    }

    @Test
    @DisplayName("login - 실패 이력 저장 중 DB 오류 발생 시 원본 예외가 그대로 전파된다")
    void login_실패이력저장중DB오류_원본예외전파() {
        // given: loginHistoryRepository.save()가 실제 DB 오류를 던지는 상황을 시뮬레이션한다.
        given(userRepository.findByEno("99999")).willReturn(Optional.empty());
        RuntimeException dbError = new RuntimeException("DB 저장 오류");
        given(loginHistoryRepository.save(any(Clognh.class))).willThrow(dbError);

        // when & then: DB 오류는 LoginRejectedException으로 삼켜지거나 변환되지 않고 그대로 전파되어야
        // 트랜잭션이 정상적으로 롤백된다.
        assertThatThrownBy(() -> authService.login("99999", "pwd", "127.0.0.1", "Agent"))
                .isSameAs(dbError)
                .isNotInstanceOf(LoginRejectedException.class);
    }

    @Test
    @DisplayName("login - 성공 경로 이후(토큰 발급) 예기치 못한 예외는 LoginRejectedException으로 변환되지 않는다")
    void login_토큰발급중예외_LoginRejectedException으로변환되지않음() {
        // given: 사용자 조회·비밀번호 검증까지 모두 성공한 뒤 토큰 발급 단계에서 예기치 못한 오류가 발생하는 상황.
        CuserI user =
                CuserI.builder()
                        .eno("10001")
                        .usrNm("홍길동")
                        .usrEcyPwd("encodedPwd")
                        .delYn("N")
                        .build();
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password", "encodedPwd")).willReturn(true);
        given(userRoleResolver.resolveAthIds("10001"))
                .willReturn(List.of(CustomUserDetails.ATH_USER));
        IllegalStateException tokenError = new IllegalStateException("토큰 발급 실패");
        given(jwtUtil.generateAccessToken("10001", List.of(CustomUserDetails.ATH_USER), null))
                .willThrow(tokenError);

        // when & then: 성공 경로 이후의 예기치 못한 예외는 원래 타입 그대로 전파되어야 트랜잭션이 롤백된다.
        assertThatThrownBy(() -> authService.login("10001", "password", "127.0.0.1", "Agent"))
                .isSameAs(tokenError)
                .isNotInstanceOf(LoginRejectedException.class);
    }

    @Test
    @DisplayName("login - 비밀번호 불일치 시 로그인 실패 이력 저장")
    void login_비밀번호불일치_실패이력저장() {
        // given
        CuserI user =
                CuserI.builder()
                        .eno("10001")
                        .usrNm("홍길동")
                        .usrEcyPwd("encodedPwd")
                        .delYn("N")
                        .build();

        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        // when: 예외 무시
        try {
            authService.login("10001", "wrong", "127.0.0.1", "Agent");
        } catch (Exception ignored) {
        }

        // then: 실패 이력 1회 저장
        verify(loginHistoryRepository, times(1)).save(any(Clognh.class));
    }

    @Test
    @DisplayName("login - 성공 시 기존 Refresh Token 삭제 후 새 토큰 저장 (1인 1토큰 정책)")
    void login_성공_기존RefreshToken삭제후저장() {
        // given
        CuserI user =
                CuserI.builder()
                        .eno("10001")
                        .usrNm("홍길동")
                        .usrEcyPwd("encodedPwd")
                        .delYn("N")
                        .build();

        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
        given(userRoleResolver.resolveAthIds("10001"))
                .willReturn(List.of(CustomUserDetails.ATH_USER));
        given(jwtUtil.generateAccessToken("10001", List.of(CustomUserDetails.ATH_USER), null))
                .willReturn("access-token");
        given(jwtUtil.generateRefreshToken(anyString())).willReturn("refresh-token");

        // when
        authService.login("10001", "password", "127.0.0.1", "Agent");

        // then
        verify(refreshTokenRepository, times(1)).deleteByEno("10001");
        verify(refreshTokenRepository, times(1)).save(any(Crtokm.class));
    }

    @Test
    @DisplayName("login - 성공 시 로그인 성공 이력 저장")
    void login_성공_성공이력저장() {
        // given
        CuserI user =
                CuserI.builder()
                        .eno("10001")
                        .usrNm("홍길동")
                        .usrEcyPwd("encodedPwd")
                        .delYn("N")
                        .build();

        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
        given(userRoleResolver.resolveAthIds("10001"))
                .willReturn(List.of(CustomUserDetails.ATH_USER));
        given(jwtUtil.generateAccessToken("10001", List.of(CustomUserDetails.ATH_USER), null))
                .willReturn("access");
        given(jwtUtil.generateRefreshToken(anyString())).willReturn("refresh");

        // when
        authService.login("10001", "password", "127.0.0.1", "Agent");

        // then
        verify(loginHistoryRepository, times(1)).save(any(Clognh.class));
    }

    // ── 회원가입 테스트 ──────────────────────────────────────────────────

    @Test
    @DisplayName("signup - 성공 시 사용자 저장")
    void signup_성공_사용자저장() {
        // given
        AuthDto.SignupRequest request = new AuthDto.SignupRequest();
        request.setEno("10002");
        request.setEmpNm("김테스트");
        request.setPassword("password");

        given(userRepository.existsByEno("10002")).willReturn(false);
        given(passwordEncoder.encode("password")).willReturn("encodedPwd");

        // when
        authService.signup(request);

        // then
        verify(userRepository, times(1)).save(any(CuserI.class));
    }

    @Test
    @DisplayName("signup - 중복 사번 → RuntimeException 발생")
    void signup_중복사번_예외발생() {
        // given
        AuthDto.SignupRequest request = new AuthDto.SignupRequest();
        request.setEno("10001");

        given(userRepository.existsByEno("10001")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("이미 존재하는 사번");
    }

    // ── Refresh Token 갱신 테스트 ──────────────────────────────────────

    @Test
    @DisplayName("getSessionUser - 인증 사용자의 최신 화면 복원 정보 반환")
    void getSessionUser_인증사용자_최신정보반환() {
        CuserI user =
                CuserI.builder()
                        .eno("10001")
                        .usrNm("홍길동")
                        .bbrC("BBR001")
                        .temC("TEM001")
                        .delYn("N")
                        .build();
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(userRoleResolver.resolveAthIds("10001")).willReturn(List.of("ITPZZ002"));

        AuthDto.LoginResponse response = authService.getSessionUser("10001");

        assertThat(response.getEno()).isEqualTo("10001");
        assertThat(response.getEmpNm()).isEqualTo("홍길동");
        assertThat(response.getAthIds()).containsExactly("ITPZZ002");
        assertThat(response.getBbrC()).isEqualTo("BBR001");
        assertThat(response.getTemC()).isEqualTo("TEM001");
        assertThat(response.getAccessToken()).isNull();
        assertThat(response.getRefreshToken()).isNull();
    }

    // 아래 refreshAccessToken 테스트들은 SEC-08 Phase A Task 5(오케스트레이터 전환) 기준이다.
    // AuthService는 더 이상 RefreshTokenRepository/UserRepository를 직접 만지지 않고 전부
    // RefreshTokenRotator.rotate()/RefreshTokenRevoker.revokeByEno()로 위임하므로, 여기서는
    // 그 두 협력자만 스텁·검증한다. DB 상태별 회전 분기(정상/grace/재사용/만료/미존재) 자체의
    // 상세 검증은 RefreshTokenRotatorTest 소관이다.

    @Test
    @DisplayName("refreshAccessToken - 정상 회전: Rotator 결과를 그대로 RefreshResponse로 매핑해 반환한다")
    void refreshAccessToken_정상회전_RotatorResult매핑반환() {
        // given: JWT 1차 검증 통과 후 rotate()가 정상 회전 결과를 반환하는 경우
        String refreshTokenValue = "valid-refresh-token";
        given(jwtUtil.validateToken(refreshTokenValue, JwtUtil.TOKEN_USE_REFRESH, false))
                .willReturn(true);
        given(refreshTokenRotator.rotate(refreshTokenValue))
                .willReturn(
                        new RefreshRotationResult(
                                "new-access-token", "new-refresh-token", "10001"));

        // when
        AuthDto.RefreshResponse response = authService.refreshAccessToken(refreshTokenValue);

        // then: rotate() 결과가 그대로 응답에 매핑되고, 폐기 경로(Revoker)는 호출되지 않는다.
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenRotator).rotate(refreshTokenValue);
        verifyNoInteractions(refreshTokenRevoker);
    }

    @Test
    @DisplayName(
            "refreshAccessToken - grace 내 동시 재제출(ConcurrentRefreshException) → 폐기 없이 재시도 가능 예외")
    void refreshAccessToken_grace내_동시재제출_재시도가능예외() {
        // given: rotate()가 grace 기간 내 재제출을 감지해 ConcurrentRefreshException을 던지는 경우
        String recent = "just-rotated-token";
        given(jwtUtil.validateToken(recent, JwtUtil.TOKEN_USE_REFRESH, false)).willReturn(true);
        ConcurrentRefreshException graceException =
                new ConcurrentRefreshException("토큰이 방금 갱신되었습니다. 잠시 후 다시 시도하세요.");
        given(refreshTokenRotator.rotate(recent)).willThrow(graceException);

        // when & then: 패밀리는 유지되므로 InvalidRefreshTokenException이 아닌 재시도 가능 예외로 구분되어야
        // 컨트롤러가 재로그인을 강제하지 않는다. 이미 unchecked이므로 재래핑 없이 그대로 전파되어야 한다
        // (isSameAs) — 원래 grace 계약(메시지)도 그대로 보존한다.
        assertThatThrownBy(() -> authService.refreshAccessToken(recent))
                .isSameAs(graceException)
                .isNotInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("다시 시도");
        verify(refreshTokenRevoker, never()).revokeByEno(any());
    }

    @Test
    @DisplayName(
            "refreshAccessToken - 재사용 감지(REUSED) → revokeByEno 1회 호출 후 InvalidRefreshTokenException")
    void refreshAccessToken_재사용감지_패밀리폐기후예외() {
        // given: rotate()가 grace 밖 재사용을 감지해 FamilyRevocationRequiredException(REUSED)을 던지는 경우
        String reused = "rotated-old-token";
        given(jwtUtil.validateToken(reused, JwtUtil.TOKEN_USE_REFRESH, false)).willReturn(true);
        given(refreshTokenRotator.rotate(reused))
                .willThrow(
                        new FamilyRevocationRequiredException(
                                FamilyRevocationRequiredException.Reason.REUSED, "10001"));

        // when & then: rotate() 트랜잭션(및 비관적 쓰기 잠금) 종료 뒤 Revoker로 패밀리를 폐기하고,
        // 그 다음에야 재로그인 예외로 통일한다 — 타입(Reason enum이 아닌 예외 타입) 기반 분기.
        assertThatThrownBy(() -> authService.refreshAccessToken(reused))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRevoker, times(1)).revokeByEno("10001");
    }

    @Test
    @DisplayName(
            "refreshAccessToken - 만료 감지(EXPIRED) → revokeByEno 1회 호출 후 InvalidRefreshTokenException")
    void refreshAccessToken_만료감지_패밀리폐기후예외() {
        // given: rotate()가 만료를 감지해 FamilyRevocationRequiredException(EXPIRED)을 던지는 경우
        String tokenValue = "expired-refresh-token";
        given(jwtUtil.validateToken(tokenValue, JwtUtil.TOKEN_USE_REFRESH, false)).willReturn(true);
        given(refreshTokenRotator.rotate(tokenValue))
                .willThrow(
                        new FamilyRevocationRequiredException(
                                FamilyRevocationRequiredException.Reason.EXPIRED, "10001"));

        // when & then: REUSED와 동일하게 폐기 후 재로그인 예외로 통일한다.
        assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRevoker, times(1)).revokeByEno("10001");
    }

    @Test
    @DisplayName("refreshAccessToken - 패밀리 폐기(revokeByEno) 실패는 삼키지 않고 원본 예외 그대로 전파한다")
    void refreshAccessToken_패밀리폐기실패_원본예외전파() {
        // given: 재사용이 감지되어 폐기를 시도하지만 Revoker 자체가 DB 오류로 실패하는 경우
        String reused = "rotated-old-token";
        given(jwtUtil.validateToken(reused, JwtUtil.TOKEN_USE_REFRESH, false)).willReturn(true);
        given(refreshTokenRotator.rotate(reused))
                .willThrow(
                        new FamilyRevocationRequiredException(
                                FamilyRevocationRequiredException.Reason.REUSED, "10001"));
        RuntimeException revokeError = new RuntimeException("DB 삭제 오류");
        org.mockito.BDDMockito.willThrow(revokeError)
                .given(refreshTokenRevoker)
                .revokeByEno("10001");

        // when & then: 실패한 폐기가 InvalidRefreshTokenException(성공처럼 보이는 형태)으로 위장되면 안 된다.
        assertThatThrownBy(() -> authService.refreshAccessToken(reused))
                .isSameAs(revokeError)
                .isNotInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName(
            "refreshAccessToken - DB에 활성 토큰이 없으면(RefreshTokenNotFoundException) 폐기 없이"
                    + " InvalidRefreshTokenException")
    void refreshAccessToken_DB토큰없음_폐기없이예외발생() {
        // given: rotate()가 조회 자체에 실패해 RefreshTokenNotFoundException(타입 기반 마커)을 던지는 경우
        String tokenValue = "missing-refresh-token";
        given(jwtUtil.validateToken(tokenValue, JwtUtil.TOKEN_USE_REFRESH, false)).willReturn(true);
        given(refreshTokenRotator.rotate(tokenValue))
                .willThrow(new RefreshTokenNotFoundException());

        // when & then: 폐기할 패밀리 자체가 없으므로 Revoker는 호출되지 않는다.
        assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verifyNoInteractions(refreshTokenRevoker);
    }

    @ParameterizedTest(name = "[{index}] 용도 가드 실패 토큰 → InvalidRefreshTokenException (Rotator 미호출)")
    @ValueSource(
            strings = {
                "access-use-token", // Access 용도 토큰
                "legacy-no-use-token", // 용도 클레임 없는 레거시 토큰
                "unknown-use-token", // 미지원 용도 문자열
                "bad-signature-token" // 서명·형식 오류 토큰
            })
    @DisplayName(
            "refreshAccessToken - Refresh 용도 가드 실패 토큰은 Rotator 호출 없이 InvalidRefreshTokenException")
    void refreshAccessToken_용도가드실패_Rotator미호출전용예외(String token) {
        // given: JwtUtil 용도 검증(3-arg)이 false → Refresh 전용 토큰이 아님
        //   (access/레거시/unknown/서명오류의 구체 판별은 JwtUtilTest 소관이며 여기서는 모두 false로 귀결)
        given(jwtUtil.validateToken(token, JwtUtil.TOKEN_USE_REFRESH, false)).willReturn(false);

        // when & then: rotate() 사전조건(JWT 검증)이 실패했으므로 rotate() 자체를 호출하지 않고 즉시 차단한다.
        assertThatThrownBy(() -> authService.refreshAccessToken(token))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("유효하지 않은 Refresh Token");

        // 가드는 rotate() 호출 이전이므로 Rotator·Revoker 어느 쪽과도 상호작용이 없어야 한다.
        verifyNoInteractions(refreshTokenRotator, refreshTokenRevoker);
    }

    @Test
    @DisplayName("refreshAccessToken - Rotator의 예기치 못한 예외(마커 타입 아님)는 변환 없이 그대로 전파된다")
    void refreshAccessToken_예기치못한예외_원본전파() {
        // given: 마커 예외(ConcurrentRefreshException/FamilyRevocationRequiredException/
        // RefreshTokenNotFoundException) 어디에도 속하지 않는 예기치 못한 오류(예: 활성 토큰 중복 불변식 위반,
        // 사용자 미존재, DB 저장 오류 등 — RefreshTokenRotatorTest에서 원본 전파가 개별 검증됨).
        String tokenValue = "valid-refresh-token";
        given(jwtUtil.validateToken(tokenValue, JwtUtil.TOKEN_USE_REFRESH, false)).willReturn(true);
        IllegalStateException unexpected =
                new IllegalStateException("활성 Refresh Token은 패밀리당 1개만 허용됩니다.");
        given(refreshTokenRotator.rotate(tokenValue)).willThrow(unexpected);

        // when & then: catch-all로 삼키거나 InvalidRefreshTokenException으로 변환하면 안 된다(hard constraint).
        assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                .isSameAs(unexpected)
                .isNotInstanceOf(InvalidRefreshTokenException.class);
        verifyNoInteractions(refreshTokenRevoker);
    }

    // ── 로그아웃 테스트 ──────────────────────────────────────────────────

    @Test
    @DisplayName("logout - 성공 시 Refresh Token 삭제 및 로그아웃 이력 저장")
    void logout_성공_RefreshToken삭제및이력저장() {
        // when
        authService.logout("10001", "127.0.0.1", "Agent");

        // then
        verify(refreshTokenRepository, times(1)).deleteByEno("10001");
        verify(loginHistoryRepository, times(1)).save(any(Clognh.class));
    }

    @Test
    @DisplayName("logoutByRefreshToken - 쿠키 해시로 토큰 소유자 패밀리를 폐기한다")
    void logoutByRefreshToken_해시조회_패밀리폐기() {
        String raw = "refresh-token";
        Crtokm stored =
                Crtokm.builder()
                        .eno("10001")
                        .famNm("FAM-1")
                        .avlYn("Y")
                        .ecyRnwPubTokCone(AuthService.sha256HexForToken(raw))
                        .endDtm(LocalDateTime.now().plusDays(1))
                        .build();
        given(refreshTokenRepository.findByEcyRnwPubTokCone(AuthService.sha256HexForToken(raw)))
                .willReturn(Optional.of(stored));

        authService.logoutByRefreshToken(raw, null, "127.0.0.1", "Agent");

        verify(refreshTokenRepository).deleteByEno("10001");
        verify(loginHistoryRepository).save(any(Clognh.class));
    }

    @Test
    @DisplayName("logoutByRefreshToken - Access 사용자와 쿠키 소유자가 다르면 두 패밀리를 폐기한다")
    void logoutByRefreshToken_사용자불일치_두패밀리폐기() {
        String raw = "refresh-token";
        Crtokm stored =
                Crtokm.builder()
                        .eno("10001")
                        .famNm("FAM-1")
                        .avlYn("Y")
                        .ecyRnwPubTokCone(AuthService.sha256HexForToken(raw))
                        .endDtm(LocalDateTime.now().plusDays(1))
                        .build();
        given(refreshTokenRepository.findByEcyRnwPubTokCone(AuthService.sha256HexForToken(raw)))
                .willReturn(Optional.of(stored));

        authService.logoutByRefreshToken(raw, "20002", "127.0.0.1", "Agent");

        verify(refreshTokenRepository).deleteByEno("10001");
        verify(refreshTokenRepository).deleteByEno("20002");
    }

    @Test
    @DisplayName("getUserName - 사용자가 있으면 이름을 반환하고 없으면 Unknown을 반환한다")
    void getUserName_사용자존재여부에따라반환() {
        given(userRepository.findByEno("10001"))
                .willReturn(Optional.of(CuserI.builder().eno("10001").usrNm("홍길동").build()));
        given(userRepository.findByEno("99999")).willReturn(Optional.empty());

        assertThat(authService.getUserName("10001")).isEqualTo("홍길동");
        assertThat(authService.getUserName("99999")).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("login - 활성 자격등급이 있으면 해당 자격등급으로 토큰을 발급한다")
    void login_활성자격등급있음_토큰클레임반영() {
        CuserI user =
                CuserI.builder()
                        .eno("10001")
                        .usrNm("홍길동")
                        .usrEcyPwd("encodedPwd")
                        .bbrC("BBR001")
                        .temC("TEM001")
                        .build();
        List<String> loginAthIds = List.of("ITPAD001");
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password", "encodedPwd")).willReturn(true);
        given(userRoleResolver.resolveAthIds("10001")).willReturn(loginAthIds);
        given(jwtUtil.generateAccessToken("10001", loginAthIds, "BBR001"))
                .willReturn("access-token");
        given(jwtUtil.generateRefreshToken("10001")).willReturn("refresh-token");

        AuthDto.LoginResponse response =
                authService.login("10001", "password", "127.0.0.1", "Agent");

        assertThat(response.getAthIds()).containsExactlyElementsOf(loginAthIds);
        assertThat(response.getBbrC()).isEqualTo("BBR001");
        assertThat(response.getTemC()).isEqualTo("TEM001");
        verify(jwtUtil).generateAccessToken("10001", loginAthIds, "BBR001");
    }

    @Test
    @DisplayName("issueSsoTokens - 사용자 존재 시 로그인 응답과 이력을 생성한다")
    void issueSsoTokens_사용자존재_토큰발급() {
        CuserI user =
                CuserI.builder().eno("10001").usrNm("홍길동").bbrC("BBR001").temC("TEM001").build();
        List<String> ssoAthIds = List.of("ITPAD001", "ITPZZ002");
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(userRoleResolver.resolveAthIds("10001")).willReturn(ssoAthIds);
        given(jwtUtil.generateAccessToken("10001", ssoAthIds, "BBR001"))
                .willReturn("access-token");
        given(jwtUtil.generateRefreshToken("10001")).willReturn("refresh-token");

        AuthDto.LoginResponse response = authService.issueSsoTokens("10001");

        assertThat(response.getEno()).isEqualTo("10001");
        assertThat(response.getAthIds()).containsExactlyElementsOf(ssoAthIds);
        verify(jwtUtil).generateAccessToken("10001", ssoAthIds, "BBR001");
        verify(refreshTokenRepository).deleteByEno("10001");
        verify(refreshTokenRepository).save(any(Crtokm.class));
        verify(loginHistoryRepository).save(any(Clognh.class));
    }

    @Test
    @DisplayName("issueSsoTokens - 사용자가 없으면 RuntimeException을 던진다")
    void issueSsoTokens_사용자없음_예외발생() {
        given(userRepository.findByEno("99999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.issueSsoTokens("99999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    // ── 개발 편의용 사용자 전환 테스트 ──────────────────────────────────

    @Test
    @DisplayName("issueDevSwitchTokens - 사용자 존재 시 DEV-SWITCH 이력과 함께 토큰을 발급한다")
    void issueDevSwitchTokens_사용자존재_토큰발급() {
        CuserI user =
                CuserI.builder().eno("10001").usrNm("홍길동").bbrC("BBR001").temC("TEM001").build();
        List<String> devAthIds = List.of("ITPZZ002");
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(userRoleResolver.resolveAthIds("10001")).willReturn(devAthIds);
        given(jwtUtil.generateAccessToken("10001", devAthIds, "BBR001"))
                .willReturn("access-token");
        given(jwtUtil.generateRefreshToken("10001")).willReturn("refresh-token");

        AuthDto.LoginResponse response = authService.issueDevSwitchTokens("10001");

        assertThat(response.getEno()).isEqualTo("10001");
        assertThat(response.getEmpNm()).isEqualTo("홍길동");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getAthIds()).containsExactlyElementsOf(devAthIds);
        verify(jwtUtil).generateAccessToken("10001", devAthIds, "BBR001");
        // 개발 전환은 일반 로그인과 동일하게 기존 패밀리를 삭제하고 신규 토큰을 저장한다.
        verify(refreshTokenRepository).deleteByEno("10001");
        verify(refreshTokenRepository).save(any(Crtokm.class));
        // DEV-SWITCH 식별자로 성공 이력을 남겨 일반 로그인·SSO와 구분한다.
        verify(loginHistoryRepository).save(any(Clognh.class));
    }

    @Test
    @DisplayName("issueDevSwitchTokens - 사용자가 없으면 RuntimeException을 던진다")
    void issueDevSwitchTokens_사용자없음_예외발생() {
        given(userRepository.findByEno("99999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.issueDevSwitchTokens("99999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        verifyNoInteractions(loginHistoryRepository);
    }

    // ── getSessionUser 예외 분기 ────────────────────────────────────────

    @Test
    @DisplayName("getSessionUser - 사용자가 없으면 RuntimeException을 던진다")
    void getSessionUser_사용자없음_예외발생() {
        given(userRepository.findByEno("99999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getSessionUser("99999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    // ── logoutByRefreshToken 추가 분기 ──────────────────────────────────

    @Test
    @DisplayName("logoutByRefreshToken - Refresh 쿠키가 없고 Access 인증만 있으면 인증 사번 패밀리만 폐기한다")
    void logoutByRefreshToken_쿠키없음_Access사번만폐기() {
        // given: refreshTokenValue가 blank → 조회 자체를 시도하지 않는다(널 가드 분기).
        authService.logoutByRefreshToken(null, "10001", "127.0.0.1", "Agent");

        verify(refreshTokenRepository, never())
                .findByEcyRnwPubTokCone(org.mockito.ArgumentMatchers.anyString());
        verify(refreshTokenRepository, times(1)).deleteByEno("10001");
        verify(loginHistoryRepository, times(1)).save(any(Clognh.class));
    }

    @Test
    @DisplayName("logoutByRefreshToken - 쿠키 값이 있어도 DB에 없으면 조회 실패로 삭제·이력 모두 생략한다")
    void logoutByRefreshToken_쿠키값DB미존재_삭제및이력생략() {
        // given: 쿠키 해시로 저장 행을 찾지 못하고, 인증 사번도 없는 경우 — 아무 폐기 대상도 없다.
        given(
                        refreshTokenRepository.findByEcyRnwPubTokCone(
                                AuthService.sha256HexForToken("stale-token")))
                .willReturn(Optional.empty());

        authService.logoutByRefreshToken("stale-token", null, "127.0.0.1", "Agent");

        verify(refreshTokenRepository, never())
                .deleteByEno(org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(loginHistoryRepository);
    }

    @Test
    @DisplayName("login - 역할 해석기의 기본 자격등급을 응답과 토큰 발급에 반영한다")
    void login_resolver기본역할_ITPZZ001반영() {
        // Arrange: 역할 해석기가 반환한 기본 자격등급을 로그인 응답과 JWT 발급에 그대로 사용한다.
        CuserI user =
                CuserI.builder()
                        .eno("10001")
                        .usrNm("홍길동")
                        .usrEcyPwd("encodedPwd")
                        .delYn("N")
                        .build();

        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password", "encodedPwd")).willReturn(true);
        // 역할 해석기가 활성 역할 없음을 기본 자격등급으로 보정해 반환한다
        given(userRoleResolver.resolveAthIds("10001"))
                .willReturn(List.of(CustomUserDetails.ATH_USER));
        given(jwtUtil.generateAccessToken("10001", List.of(CustomUserDetails.ATH_USER), null))
                .willReturn("access-token");
        given(jwtUtil.generateRefreshToken("10001")).willReturn("refresh-token");

        // Act
        AuthDto.LoginResponse response =
                authService.login("10001", "password", "127.0.0.1", "Agent");

        // Assert: 기본 자격등급 ITPZZ001이 응답과 토큰 발급에 그대로 반영되어야 한다
        assertThat(response.getAthIds()).containsExactly(CustomUserDetails.ATH_USER);
        verify(jwtUtil).generateAccessToken("10001", List.of(CustomUserDetails.ATH_USER), null);
    }
}
