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

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.entity.Crtokm;
import com.kdb.it.common.system.exception.ConcurrentRefreshException;
import com.kdb.it.common.system.exception.FamilyRevocationRequiredException;
import com.kdb.it.common.system.repository.RefreshTokenRepository;
import com.kdb.it.common.system.security.JwtUtil;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * RefreshTokenRotator 단위 테스트 (SEC-08 Phase A, Task 4).
 *
 * <p>비관적 쓰기 잠금 하 회전 트랜잭션의 4가지 분기(정상 회전 / grace 내 재제출 / grace 밖 재사용 / 만료)와, 사용자 미존재·저장 오류 같은 예기치 못한
 * 오류가 마커 예외로 둔갑하지 않고 원본 그대로 전파되는지를 Mockito로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenRotatorTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks private RefreshTokenRotator rotator;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rotator, "refreshTokenValidityMs", 604_800_000L);
        ReflectionTestUtils.setField(rotator, "rotationGraceSeconds", 30L);
    }

    @Test
    @DisplayName("rotate - 활성 토큰 → 구 토큰 회전 표식 + 신규 토큰 저장 + RefreshRotationResult 반환")
    void rotate_활성토큰_정상회전_결과반환() {
        // given
        String oldRefresh = "active-token";
        Crtokm stored =
                Crtokm.builder()
                        .ecyRnwPubTokCone(AuthService.sha256HexForToken(oldRefresh))
                        .eno("10001")
                        .famNm("FAM-1")
                        .avlYn("Y")
                        .endDtm(LocalDateTime.now().plusDays(7))
                        .build();
        given(
                        refreshTokenRepository.findByEcyRnwPubTokCone(
                                AuthService.sha256HexForToken(oldRefresh)))
                .willReturn(Optional.of(stored));
        given(userRepository.findByEno("10001"))
                .willReturn(
                        Optional.of(
                                CuserI.builder()
                                        .eno("10001")
                                        .usrNm("홍길동")
                                        .bbrC("BBR001")
                                        .delYn("N")
                                        .build()));
        given(roleRepository.findAllByIdEnoAndUseYnAndDelYn("10001", "Y", "N"))
                .willReturn(Collections.emptyList());
        given(jwtUtil.generateAccessToken(anyString(), anyList(), any())).willReturn("new-access");
        given(jwtUtil.generateRefreshToken("10001")).willReturn("new-refresh");

        // when
        RefreshRotationResult result = rotator.rotate(oldRefresh);

        // then
        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.eno()).isEqualTo("10001");
        assertThat(stored.isRotated()).isTrue();

        ArgumentCaptor<Crtokm> captor = ArgumentCaptor.forClass(Crtokm.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture()); // 구 표식 + 신규
        Crtokm savedNew = captor.getAllValues().get(1);
        // 새 토큰은 Crtokm.create(...)로 생성되어 소유자 eno가 감사자로 기록되어야 한다.
        assertThat(savedNew.getFstEnrUsid()).isEqualTo("10001");
        assertThat(savedNew.getLstChgUsid()).isEqualTo("10001");
        assertThat(savedNew.getEcyRnwPubTokCone())
                .isEqualTo(AuthService.sha256HexForToken("new-refresh"));
        // 갓 회전된 신규 토큰은 활성(AVL_YN='Y') 상태여야 한다 — 여기가 깨지면 다음 refresh 요청이 거부된다(SEC-01 단일활성 불변식).
        assertThat(savedNew.getAvlYn()).isEqualTo("Y");
        assertThat(savedNew.isRotated()).isFalse();
    }

    @Test
    @DisplayName("rotate - 회전 후 패밀리 활성 토큰이 2개 이상 남으면 IllegalStateException을 던진다")
    void rotate_회전후활성토큰2개이상_IllegalStateException() {
        // given: 회전 자체는 정상 진행되지만 검증 시점에 활성 토큰이 2개 조회되는 이상 상태
        // (Mockito 기본값인 빈 리스트로는 이 분기가 절대 실행되지 않으므로 명시적으로 2개 이상을 스텁해야 한다).
        String oldRefresh = "dup-family-token";
        Crtokm stored =
                Crtokm.builder()
                        .ecyRnwPubTokCone(AuthService.sha256HexForToken(oldRefresh))
                        .eno("10001")
                        .famNm("FAM-1")
                        .avlYn("Y")
                        .endDtm(LocalDateTime.now().plusDays(7))
                        .build();
        Crtokm extraActive =
                Crtokm.builder()
                        .ecyRnwPubTokCone("other-hash")
                        .eno("10001")
                        .famNm("FAM-1")
                        .avlYn("Y")
                        .endDtm(LocalDateTime.now().plusDays(7))
                        .build();
        given(
                        refreshTokenRepository.findByEcyRnwPubTokCone(
                                AuthService.sha256HexForToken(oldRefresh)))
                .willReturn(Optional.of(stored));
        given(userRepository.findByEno("10001"))
                .willReturn(
                        Optional.of(
                                CuserI.builder()
                                        .eno("10001")
                                        .usrNm("홍길동")
                                        .bbrC("BBR001")
                                        .delYn("N")
                                        .build()));
        given(roleRepository.findAllByIdEnoAndUseYnAndDelYn("10001", "Y", "N"))
                .willReturn(Collections.emptyList());
        given(jwtUtil.generateAccessToken(anyString(), anyList(), any()))
                .willReturn("new-access-token");
        given(jwtUtil.generateRefreshToken("10001")).willReturn("new-refresh-token");
        given(refreshTokenRepository.findByFamNmAndAvlYn("FAM-1", "Y"))
                .willReturn(List.of(stored, extraActive));

        // when & then: 패밀리 단일 활성 토큰 불변식(SEC-01) 위반은 IllegalStateException
        assertThatThrownBy(() -> rotator.rotate(oldRefresh))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("활성 Refresh Token은 패밀리당 1개만 허용됩니다.");
    }

    @Test
    @DisplayName("rotate - grace 내 회전 토큰 재제출 → ConcurrentRefreshException, 삭제·저장 없음")
    void rotate_grace내_재제출_ConcurrentRefreshException() {
        // given
        String recent = "just-rotated-token";
        Crtokm rotated =
                Crtokm.builder()
                        .ecyRnwPubTokCone(AuthService.sha256HexForToken(recent))
                        .eno("10001")
                        .famNm("FAM-1")
                        .avlYn("N")
                        .endDtm(LocalDateTime.now().plusDays(7))
                        .lstChgDtm(LocalDateTime.now().minusSeconds(3))
                        .build();
        given(refreshTokenRepository.findByEcyRnwPubTokCone(AuthService.sha256HexForToken(recent)))
                .willReturn(Optional.of(rotated));

        // when & then
        assertThatThrownBy(() -> rotator.rotate(recent))
                .isInstanceOf(ConcurrentRefreshException.class);

        verify(refreshTokenRepository, never()).deleteByEno(anyString());
        verify(refreshTokenRepository, never()).save(any(Crtokm.class));
    }

    @Test
    @DisplayName("rotate - grace 밖 회전 토큰 재제출 → FamilyRevocationRequiredException(REUSED), 삭제 없음")
    void rotate_grace밖_재사용_FamilyRevocationRequiredException_REUSED() {
        // given
        String reused = "rotated-old-token";
        Crtokm rotated =
                Crtokm.builder()
                        .ecyRnwPubTokCone(AuthService.sha256HexForToken(reused))
                        .eno("10001")
                        .famNm("FAM-1")
                        .avlYn("N")
                        .endDtm(LocalDateTime.now().plusDays(7))
                        .lstChgDtm(LocalDateTime.now().minusMinutes(5)) // grace(30s) 경과
                        .build();
        given(refreshTokenRepository.findByEcyRnwPubTokCone(AuthService.sha256HexForToken(reused)))
                .willReturn(Optional.of(rotated));

        // when & then
        assertThatThrownBy(() -> rotator.rotate(reused))
                .isInstanceOf(FamilyRevocationRequiredException.class)
                .satisfies(
                        ex -> {
                            FamilyRevocationRequiredException familyEx =
                                    (FamilyRevocationRequiredException) ex;
                            assertThat(familyEx.getReason())
                                    .isEqualTo(FamilyRevocationRequiredException.Reason.REUSED);
                            assertThat(familyEx.getEno()).isEqualTo("10001");
                        });

        // 삭제는 Revoker(별도 트랜잭션)의 책임 — Rotator는 삭제하지 않는다.
        verify(refreshTokenRepository, never()).deleteByEno(anyString());
        verify(refreshTokenRepository, never()).delete(any(Crtokm.class));
        verify(refreshTokenRepository, never()).save(any(Crtokm.class));
    }

    @Test
    @DisplayName("rotate - 만료된 토큰 → FamilyRevocationRequiredException(EXPIRED), 삭제 없음")
    void rotate_만료토큰_FamilyRevocationRequiredException_EXPIRED() {
        // given
        String tokenValue = "expired-refresh-token";
        Crtokm expiredToken =
                Crtokm.builder()
                        .ecyRnwPubTokCone(AuthService.sha256HexForToken(tokenValue))
                        .eno("10001")
                        .famNm("FAM-1")
                        .avlYn("Y")
                        .endDtm(LocalDateTime.now().minusDays(1)) // 이미 만료
                        .build();
        given(
                        refreshTokenRepository.findByEcyRnwPubTokCone(
                                AuthService.sha256HexForToken(tokenValue)))
                .willReturn(Optional.of(expiredToken));

        // when & then
        assertThatThrownBy(() -> rotator.rotate(tokenValue))
                .isInstanceOf(FamilyRevocationRequiredException.class)
                .satisfies(
                        ex -> {
                            FamilyRevocationRequiredException familyEx =
                                    (FamilyRevocationRequiredException) ex;
                            assertThat(familyEx.getReason())
                                    .isEqualTo(FamilyRevocationRequiredException.Reason.EXPIRED);
                            assertThat(familyEx.getEno()).isEqualTo("10001");
                        });

        // 만료 토큰도 이 트랜잭션 안에서는 삭제하지 않는다(패밀리 폐기는 Revoker 책임).
        verify(refreshTokenRepository, never()).delete(any(Crtokm.class));
        verify(refreshTokenRepository, never()).deleteByEno(anyString());
    }

    @Test
    @DisplayName("rotate - 토큰 사용자가 없으면 원본 RuntimeException이 그대로 전파된다 (마커 예외로 변환되지 않음)")
    void rotate_사용자없음_원본예외전파() {
        // given
        String tokenValue = "valid-refresh-token";
        Crtokm refreshToken =
                Crtokm.builder()
                        .ecyRnwPubTokCone(AuthService.sha256HexForToken(tokenValue))
                        .eno("10001")
                        .famNm("FAM-1")
                        .avlYn("Y")
                        .endDtm(LocalDateTime.now().plusDays(7))
                        .build();
        given(
                        refreshTokenRepository.findByEcyRnwPubTokCone(
                                AuthService.sha256HexForToken(tokenValue)))
                .willReturn(Optional.of(refreshToken));
        given(userRepository.findByEno("10001")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> rotator.rotate(tokenValue))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(ConcurrentRefreshException.class)
                .isNotInstanceOf(FamilyRevocationRequiredException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("rotate - 저장 중 DB 오류는 원본 그대로 전파된다 (마커 예외로 변환되지 않음)")
    void rotate_저장중DB오류_원본예외전파() {
        // given
        String oldRefresh = "active-token";
        Crtokm stored =
                Crtokm.builder()
                        .ecyRnwPubTokCone(AuthService.sha256HexForToken(oldRefresh))
                        .eno("10001")
                        .famNm("FAM-1")
                        .avlYn("Y")
                        .endDtm(LocalDateTime.now().plusDays(7))
                        .build();
        given(
                        refreshTokenRepository.findByEcyRnwPubTokCone(
                                AuthService.sha256HexForToken(oldRefresh)))
                .willReturn(Optional.of(stored));
        given(userRepository.findByEno("10001"))
                .willReturn(
                        Optional.of(
                                CuserI.builder()
                                        .eno("10001")
                                        .usrNm("홍길동")
                                        .bbrC("BBR001")
                                        .delYn("N")
                                        .build()));
        given(roleRepository.findAllByIdEnoAndUseYnAndDelYn("10001", "Y", "N"))
                .willReturn(Collections.emptyList());
        given(jwtUtil.generateAccessToken(anyString(), anyList(), any())).willReturn("new-access");
        RuntimeException dbError = new RuntimeException("DB 저장 오류");
        given(refreshTokenRepository.save(any(Crtokm.class))).willThrow(dbError);

        // when & then
        assertThatThrownBy(() -> rotator.rotate(oldRefresh))
                .isSameAs(dbError)
                .isNotInstanceOf(ConcurrentRefreshException.class)
                .isNotInstanceOf(FamilyRevocationRequiredException.class);
    }
}
