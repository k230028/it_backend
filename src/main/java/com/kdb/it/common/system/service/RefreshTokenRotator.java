package com.kdb.it.common.system.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.UserRoleResolver;
import com.kdb.it.common.system.entity.Crtokm;
import com.kdb.it.common.system.exception.ConcurrentRefreshException;
import com.kdb.it.common.system.exception.FamilyRevocationRequiredException;
import com.kdb.it.common.system.exception.RefreshTokenNotFoundException;
import com.kdb.it.common.system.repository.RefreshTokenRepository;
import com.kdb.it.common.system.security.JwtUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 회전 전용 트랜잭션 컴포넌트 (SEC-08 Phase A, Task 4).
 *
 * <p>{@link com.kdb.it.common.system.repository.RefreshTokenRepository#findByEcyRnwPubTokCone}의 비관적
 * 쓰기 잠금(PESSIMISTIC_WRITE) 하에서 Refresh Token을 조회하고, 정상 상태면 즉시 회전시킨다. 회전 직후 grace 기간 내 재제출(다중 탭 동시
 * 새로고침), grace 기간 밖 재사용(탈취 의심), 만료 감지 시에는 이 트랜잭션 안에서 패밀리를 직접 삭제하지 않고 마커 예외만 던진다 — 실제 삭제는 {@link
 * RefreshTokenRevoker#revokeByEno(String)}가 별도 {@code REQUIRES_NEW} 트랜잭션에서, 이 트랜잭션이 종료되어 비관적 쓰기 잠금이
 * 풀린 뒤 수행한다(동일 행에 대한 락 경합으로 인한 교착·롤백 위험 회피).
 *
 * <p><b>롤백 보장</b>: {@link ConcurrentRefreshException}, {@link FamilyRevocationRequiredException},
 * {@link RefreshTokenNotFoundException} 모두 unchecked({@link RuntimeException})이므로, 이 메서드에서 발생하면 별도
 * {@code rollbackFor} 지정 없이 Spring 기본 정책에 따라 트랜잭션이 롤백된다. 이 롤백이 곧 비관적 쓰기 잠금 해제 시점이므로, 오케스트레이터({@link
 * com.kdb.it.common.system.service.AuthService#refreshAccessToken(String)})는 반드시 이 메서드 호출(및 그 트랜잭션
 * 종료)이 끝난 뒤에 Revoker를 호출한다.
 *
 * <p>사용자 미존재, 리포지토리 저장 오류처럼 위 세 마커에 해당하지 않는 예기치 못한 오류는 변환하지 않고 원본 예외 그대로 전파한다.
 *
 * <p>이 클래스는 {@link com.kdb.it.exception.InvalidRefreshTokenException}을 던지지 않는다 — 그 예외로의 변환은
 * 호출자(오케스트레이터)의 책임이다.
 *
 * <p><b>오케스트레이션 계약</b>: {@link #rotate(String)} 호출 전, 오케스트레이터({@code AuthService})는 반드시 {@code
 * jwtUtil.validateToken(refreshTokenValue, JwtUtil.TOKEN_USE_REFRESH, false)}로 JWT 서명·형식·만료·용도를 먼저
 * 검증해 실패 시 {@link com.kdb.it.exception.InvalidRefreshTokenException}으로 거부한다 — 자세한 사유는 {@link
 * #rotate(String)} Javadoc 참조.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenRotator {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRoleResolver userRoleResolver;
    private final JwtUtil jwtUtil;

    @Value("${jwt.refresh-token-validity}")
    private long refreshTokenValidityMs;

    /**
     * Refresh Token 회전 직후 동시 새로고침(다중 탭) 허용 grace 기간(초). SEC-08 Phase A Task 5부터 오케스트레이터({@code
     * AuthService})는 회전 판단을 전부 이 클래스에 위임하므로, 이 설정값도 이 클래스가 단독으로 소유한다.
     */
    @Value("${app.auth.refresh-rotation-grace-seconds:30}")
    private long rotationGraceSeconds;

    /**
     * Refresh Token을 비관적 쓰기 잠금 하에 조회하고, 정상 상태면 회전시켜 결과를 반환한다.
     *
     * <p><b>사전조건(호출자 책임)</b>: 이 메서드를 호출하기 전에 호출자가 {@code jwtUtil.validateToken(refreshTokenValue,
     * JwtUtil.TOKEN_USE_REFRESH, false)}로 JWT 서명·형식·만료(exp 클레임)와 {@code tokenUse=refresh} 용도를 이미
     * 검증하고, 실패 시 {@link com.kdb.it.exception.InvalidRefreshTokenException}으로 거부했어야 한다. {@code
     * rotate()}는 구조적으로 유효한 Refresh JWT가 들어온다고 가정하며, DB 상태 기반 회전·재사용·만료 판단만 수행한다. 이 검증을 rotate() 안으로
     * 들여오지 않는 이유는 두 가지다: (1) JWT 형식·용도 거부는 오케스트레이터가 소유한 {@code InvalidRefreshTokenException}으로
     * 귀결되어야 하는데, 이 클래스는 그 예외를 던질 수 없다(마커 예외 전용 계약). (2) 이 검증은 트랜잭션·비관적 쓰기 잠금을 시작하기 전에 실패로 빠르게 끝나야
     * 한다(fail-fast) — 오케스트레이터가 non-transactional 경계에서 먼저 걸러낸 뒤에만 이 잠금 구간에 진입해야 락 보유 시간을 최소화할 수 있다.
     *
     * <p>오케스트레이터({@code AuthService#refreshAccessToken(String)})는 이 사전조건을 생략하면 안 된다 — 생략 시
     * 서명·만료·용도가 검증되지 않은 문자열이 그대로 DB 조회(비관적 쓰기 잠금)까지 도달한다.
     *
     * @param refreshTokenValue 클라이언트가 제출한 Refresh Token 원문 — 호출자가 이미 JWT 검증을 통과시킨 값이어야 한다
     * @return 정상 회전 결과 (새 Access/Refresh Token, 소유자 사번)
     * @throws ConcurrentRefreshException grace 기간 내 회전된 토큰이 재제출된 경우 — 재시도 가능, 패밀리는 유지된다
     * @throws FamilyRevocationRequiredException grace 기간 밖 재사용 또는 만료가 감지된 경우 — 호출자가 {@link
     *     RefreshTokenRevoker#revokeByEno(String)}로 패밀리를 폐기해야 한다
     * @throws RefreshTokenNotFoundException 조회값으로 DB에 활성 토큰을 찾지 못한 경우 — 폐기할 패밀리가 없으므로 호출자는 Revoker를
     *     호출하지 않고 재로그인 예외로만 변환해야 한다
     * @throws RuntimeException 그 외 예기치 못한 사용자 미존재·저장 오류 (원인 그대로 전파)
     */
    @Transactional
    public RefreshRotationResult rotate(String refreshTokenValue) {
        // 전제: 호출자가 jwtUtil.validateToken(refreshTokenValue, TOKEN_USE_REFRESH, false)로 이미
        // JWT 서명·형식·만료·용도를 검증했다. 이 메서드는 DB 상태(AVL_YN/END_DTM) 판단만 담당한다.
        Crtokm refreshToken = findRefreshTokenByValue(refreshTokenValue);

        if (refreshToken.isRotated()) {
            LocalDateTime rotatedAt = refreshToken.getLstChgDtm();
            boolean withinGrace =
                    rotatedAt != null
                            && Duration.between(rotatedAt, LocalDateTime.now()).getSeconds()
                                    <= rotationGraceSeconds;
            if (withinGrace) {
                throw new ConcurrentRefreshException("토큰이 방금 갱신되었습니다. 잠시 후 다시 시도하세요.");
            }
            log.warn(
                    "Refresh Token 재사용 탐지 — 패밀리 폐기 필요: eno={}, famNm={}",
                    refreshToken.getEno(),
                    refreshToken.getFamNm());
            throw new FamilyRevocationRequiredException(
                    FamilyRevocationRequiredException.Reason.REUSED, refreshToken.getEno());
        }

        if (refreshToken.isExpired()) {
            log.warn("만료된 Refresh Token 감지 — 패밀리 폐기 필요: eno={}", refreshToken.getEno());
            throw new FamilyRevocationRequiredException(
                    FamilyRevocationRequiredException.Reason.EXPIRED, refreshToken.getEno());
        }

        String eno = refreshToken.getEno();
        CuserI user =
                userRepository
                        .findByEno(eno)
                        .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        List<String> athIds = userRoleResolver.resolveAthIds(eno);
        String newAccessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());

        // 회전: 구 토큰을 삭제하지 않고 '회전됨' 표식만 남기고(재사용 탐지용), 신규 토큰을 동일 패밀리로 저장한다.
        refreshToken.markRotated();
        refreshTokenRepository.save(refreshToken);

        String newRefreshTokenValue = jwtUtil.generateRefreshToken(eno);
        String newRefreshTokenHash = AuthService.sha256HexForToken(newRefreshTokenValue);
        Crtokm rotated =
                Crtokm.create(
                        newRefreshTokenHash,
                        newRefreshTokenHash,
                        eno,
                        refreshToken.getFamNm(),
                        LocalDateTime.now().plus(Duration.ofMillis(refreshTokenValidityMs)));
        refreshTokenRepository.save(rotated);
        validateSingleActiveToken(refreshToken.getFamNm());

        return new RefreshRotationResult(newAccessToken, newRefreshTokenValue, eno);
    }

    /**
     * Refresh Token 원문을 SHA-256 조회값으로 변환해 저장 행을 비관적 쓰기 잠금과 함께 조회한다.
     *
     * @throws RefreshTokenNotFoundException 조회값에 해당하는 저장 행이 없는 경우
     */
    private Crtokm findRefreshTokenByValue(String refreshTokenValue) {
        String lookupValue = AuthService.sha256HexForToken(refreshTokenValue);
        return refreshTokenRepository
                .findByEcyRnwPubTokCone(lookupValue)
                .orElseThrow(
                        () -> {
                            log.warn("Refresh Token 조회 실패 — DB에 활성 토큰 없음");
                            return new RefreshTokenNotFoundException();
                        });
    }

    /**
     * 회전 완료 후 같은 패밀리에 활성 Refresh Token이 1개만 남았는지 검증한다.
     *
     * @throws IllegalStateException 패밀리에 활성 토큰이 2개 이상 남은 경우
     */
    private void validateSingleActiveToken(String famNm) {
        List<Crtokm> activeTokens = refreshTokenRepository.findByFamNmAndAvlYn(famNm, "Y");
        if (activeTokens.size() <= 1) {
            return;
        }
        log.warn(
                "Refresh Token 패밀리 활성 토큰 중복 탐지: famNm={}, activeCount={}",
                famNm,
                activeTokens.size());
        throw new IllegalStateException("활성 Refresh Token은 패밀리당 1개만 허용됩니다.");
    }
}
