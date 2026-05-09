package com.kdb.it.common.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.entity.CroleI;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.entity.Crtokm;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import com.kdb.it.common.system.repository.RefreshTokenRepository;
import com.kdb.it.common.system.security.JwtUtil;

/**
 * AuthService 단위 테스트
 *
 * <p>
 * Mockito로 Repository, PasswordEncoder, JwtUtil을 Mock 처리하여
 * Oracle DB 연결 없이 비즈니스 로직만 검증합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

        @Mock
        private UserRepository userRepository;
        @Mock
        private RoleRepository roleRepository;
        @Mock
        private RefreshTokenRepository refreshTokenRepository;
        @Mock
        private LoginHistoryRepository loginHistoryRepository;
        @Mock
        private PasswordEncoder passwordEncoder;
        @Mock
        private JwtUtil jwtUtil;

        @InjectMocks
        private AuthService authService;

        // ── 로그인 테스트 ──────────────────────────────────────────────────

        @Test
        @DisplayName("login - 성공 시 LoginResponse (eno, empNm, accessToken) 반환")
        void login_성공_LoginResponse반환() {
                // given
                CuserI user = CuserI.builder()
                                .eno("10001").usrNm("홍길동").usrEcyPwd("encodedPwd").delYn("N").build();

                given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
                given(passwordEncoder.matches("password", "encodedPwd")).willReturn(true);
                given(jwtUtil.generateAccessToken(anyString(), anyList(), any())).willReturn("access-token");
                given(jwtUtil.generateRefreshToken("10001")).willReturn("refresh-token");

                // when
                AuthDto.LoginResponse response = authService.login("10001", "password", "127.0.0.1", "TestAgent");

                // then
                assertThat(response).isNotNull();
                assertThat(response.getEno()).isEqualTo("10001");
                assertThat(response.getEmpNm()).isEqualTo("홍길동");
                assertThat(response.getAccessToken()).isEqualTo("access-token");
        }

        @Test
        @DisplayName("login - 존재하지 않는 사번 → RuntimeException 발생")
        void login_존재하지않는사번_예외발생() {
                // given
                given(userRepository.findByEno("99999")).willReturn(Optional.empty());

                // when & then
                assertThatThrownBy(() -> authService.login("99999", "pwd", "127.0.0.1", "Agent"))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("login - 비밀번호 불일치 → RuntimeException 발생")
        void login_비밀번호불일치_예외발생() {
                // given
                CuserI user = CuserI.builder()
                                .eno("10001").usrNm("홍길동").usrEcyPwd("encodedPwd").delYn("N").build();

                given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
                given(passwordEncoder.matches("wrongPwd", "encodedPwd")).willReturn(false);

                // when & then
                assertThatThrownBy(() -> authService.login("10001", "wrongPwd", "127.0.0.1", "Agent"))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("비밀번호가 일치하지 않습니다");
        }

        @Test
        @DisplayName("login - 비밀번호 불일치 시 로그인 실패 이력 저장")
        void login_비밀번호불일치_실패이력저장() {
                // given
                CuserI user = CuserI.builder()
                                .eno("10001").usrNm("홍길동").usrEcyPwd("encodedPwd").delYn("N").build();

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
                CuserI user = CuserI.builder()
                                .eno("10001").usrNm("홍길동").usrEcyPwd("encodedPwd").delYn("N").build();

                given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
                given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
                given(jwtUtil.generateAccessToken(anyString(), anyList(), any())).willReturn("access-token");
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
                CuserI user = CuserI.builder()
                                .eno("10001").usrNm("홍길동").usrEcyPwd("encodedPwd").delYn("N").build();

                given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
                given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
                given(jwtUtil.generateAccessToken(anyString(), anyList(), any())).willReturn("access");
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
        @DisplayName("refreshAccessToken - 유효한 Refresh Token → 새 Access Token 반환")
        void refreshAccessToken_유효한토큰_새AccessToken반환() {
                // given
                String refreshTokenValue = "valid-refresh-token";
                Crtokm refreshToken = Crtokm.builder()
                                .tok(refreshTokenValue).eno("10001")
                                .endDtm(LocalDateTime.now().plusDays(7))
                                .build();

                given(jwtUtil.validateToken(refreshTokenValue)).willReturn(true);
                given(refreshTokenRepository.findByTok(refreshTokenValue)).willReturn(Optional.of(refreshToken));
                given(userRepository.findByEno("10001")).willReturn(Optional.of(
                                CuserI.builder().eno("10001").usrNm("홍길동").bbrC("BBR001").delYn("N").build()));
                given(roleRepository.findAllByIdEnoAndUseYnAndDelYn("10001", "Y", "N"))
                                .willReturn(Collections.emptyList());
                given(jwtUtil.generateAccessToken(anyString(), anyList(), any())).willReturn("new-access-token");

                // when
                AuthDto.RefreshResponse response = authService.refreshAccessToken(refreshTokenValue);

                // then
                assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        }

        @Test
        @DisplayName("refreshAccessToken - 유효하지 않은 토큰 → RuntimeException 발생")
        void refreshAccessToken_유효하지않은토큰_예외발생() {
                // given
                given(jwtUtil.validateToken("invalid-token")).willReturn(false);

                // when & then
                assertThatThrownBy(() -> authService.refreshAccessToken("invalid-token"))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("유효하지 않은 Refresh Token");
        }

        @Test
        @DisplayName("refreshAccessToken - 만료된 DB 토큰 → delete() 후 RuntimeException 발생")
        void refreshAccessToken_만료된DB토큰_예외발생및삭제() {
                // given
                String tokenValue = "expired-refresh-token";
                Crtokm expiredToken = Crtokm.builder()
                                .tok(tokenValue).eno("10001")
                                .endDtm(LocalDateTime.now().minusDays(1)) // 이미 만료
                                .build();

                given(jwtUtil.validateToken(tokenValue)).willReturn(true);
                given(refreshTokenRepository.findByTok(tokenValue)).willReturn(Optional.of(expiredToken));

                // when & then
                assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("만료된 Refresh Token");

                // 만료 토큰 즉시 삭제 검증
                verify(refreshTokenRepository, times(1)).delete(expiredToken);
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
                CuserI user = CuserI.builder()
                                .eno("10001").usrNm("홍길동").usrEcyPwd("encodedPwd").bbrC("BBR001").temC("TEM001").build();
                CroleI role = org.mockito.Mockito.mock(CroleI.class);
                given(role.getAthId()).willReturn("ITPAD001");
                given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
                given(passwordEncoder.matches("password", "encodedPwd")).willReturn(true);
                given(roleRepository.findAllByIdEnoAndUseYnAndDelYn("10001", "Y", "N"))
                                .willReturn(List.of(role));
                given(jwtUtil.generateAccessToken("10001", List.of("ITPAD001"), "BBR001")).willReturn("access-token");
                given(jwtUtil.generateRefreshToken("10001")).willReturn("refresh-token");

                AuthDto.LoginResponse response = authService.login("10001", "password", "127.0.0.1", "Agent");

                assertThat(response.getAthIds()).containsExactly("ITPAD001");
                assertThat(response.getBbrC()).isEqualTo("BBR001");
                assertThat(response.getTemC()).isEqualTo("TEM001");
        }

        @Test
        @DisplayName("refreshAccessToken - DB에 토큰이 없으면 RuntimeException을 던진다")
        void refreshAccessToken_DB토큰없음_예외발생() {
                given(jwtUtil.validateToken("missing-refresh")).willReturn(true);
                given(refreshTokenRepository.findByTok("missing-refresh")).willReturn(Optional.empty());

                assertThatThrownBy(() -> authService.refreshAccessToken("missing-refresh"))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("Refresh Token을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("refreshAccessToken - 토큰 사용자가 없으면 RuntimeException을 던진다")
        void refreshAccessToken_사용자없음_예외발생() {
                String tokenValue = "valid-refresh-token";
                Crtokm refreshToken = Crtokm.builder()
                                .tok(tokenValue).eno("10001")
                                .endDtm(LocalDateTime.now().plusDays(7))
                                .build();
                given(jwtUtil.validateToken(tokenValue)).willReturn(true);
                given(refreshTokenRepository.findByTok(tokenValue)).willReturn(Optional.of(refreshToken));
                given(userRepository.findByEno("10001")).willReturn(Optional.empty());

                assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("issueSsoTokens - 사용자 존재 시 로그인 응답과 이력을 생성한다")
        void issueSsoTokens_사용자존재_토큰발급() {
                CuserI user = CuserI.builder()
                                .eno("10001").usrNm("홍길동").bbrC("BBR001").temC("TEM001").build();
                given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
                given(roleRepository.findAllByIdEnoAndUseYnAndDelYn("10001", "Y", "N"))
                                .willReturn(Collections.emptyList());
                given(jwtUtil.generateAccessToken(anyString(), anyList(), any())).willReturn("access-token");
                given(jwtUtil.generateRefreshToken("10001")).willReturn("refresh-token");

                AuthDto.LoginResponse response = authService.issueSsoTokens("10001");

                assertThat(response.getEno()).isEqualTo("10001");
                assertThat(response.getAthIds()).containsExactly("ITPZZ001");
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
}
