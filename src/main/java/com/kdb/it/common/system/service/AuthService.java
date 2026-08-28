package com.kdb.it.common.system.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.LoginAttemptService;
import com.kdb.it.common.iam.service.UserRoleResolver;
import com.kdb.it.common.mfa.dto.MfaDto;
import com.kdb.it.common.mfa.service.MfaService;
import com.kdb.it.common.security.TokenFingerprint;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.entity.Crtokm;
import com.kdb.it.common.system.exception.ConcurrentRefreshException;
import com.kdb.it.common.system.exception.FamilyRevocationRequiredException;
import com.kdb.it.common.system.exception.RefreshTokenNotFoundException;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import com.kdb.it.common.system.repository.RefreshTokenRepository;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.exception.InvalidRefreshTokenException;
import com.kdb.it.exception.LoginRejectedException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증(Authentication) 서비스
 *
 * <p>사용자 회원가입, 로그인, 토큰 갱신, 로그아웃 비즈니스 로직을 처리합니다.
 *
 * <p>인증 방식: JWT 기반 Stateless 인증
 *
 * <ul>
 *   <li>Access Token: 단기 유효 (기본 15분), httpOnly 쿠키로 자동 전송
 *   <li>Refresh Token: 장기 유효 (기본 7일), DB에 저장, Access Token 갱신에 사용
 * </ul>
 *
 * <p>로그인 이력: 로그인 성공/실패, 로그아웃 시 {@link Clognh}에 자동 기록됩니다.
 *
 * <p>비밀번호 처리: {@link PasswordEncoder} (SHA-256 + Base64 방식)로 암호화합니다.
 */
@Slf4j // 로깅 (Lombok) — 토큰 재사용 탐지 경고 등
@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
public class AuthService {

    /** 사용자 정보 데이터 접근 리포지토리 (TPRMPP_CUSERI) */
    private final UserRepository userRepository;

    /** Refresh Token 데이터 접근 리포지토리 (TPRMPP_CRTOKM) */
    private final RefreshTokenRepository refreshTokenRepository;

    /** 로그인 이력 데이터 접근 리포지토리 (TPRMPP_CLOGNH) */
    private final LoginHistoryRepository loginHistoryRepository;

    /** 비밀번호 암호화 및 검증 (SHA-256 + Base64) */
    private final PasswordEncoder passwordEncoder;

    /** 사용자 자격등급 조회와 기본 역할 보정을 담당하는 공통 해석기 */
    private final UserRoleResolver userRoleResolver;

    /** 로그인 Brute-force 감지 서비스 — SEC-03 */
    private final LoginAttemptService loginAttemptService;

    /** 수동 로그인 MFA 거래를 관리하는 서비스 */
    private final MfaService mfaService;

    /** JWT Access/Refresh Token 생성 및 검증 유틸리티 */
    private final JwtUtil jwtUtil;

    /** Refresh Token 원문을 DB 조회용 HMAC 지문으로 변환합니다. */
    private final TokenFingerprint tokenFingerprint;

    /** Refresh Token 회전 전용 트랜잭션 컴포넌트 — 비관적 쓰기 잠금 하 조회·회전 담당 (SEC-08 Phase A Task 4/5) */
    private final RefreshTokenRotator refreshTokenRotator;

    /** Refresh Token 패밀리 폐기 전용 트랜잭션 컴포넌트({@code REQUIRES_NEW}) (SEC-08 Phase A Task 4/5) */
    private final RefreshTokenRevoker refreshTokenRevoker;

    @Value("${jwt.refresh-token-validity}")
    private long refreshTokenValidityMs;

    /**
     * 회원가입 (사용자 등록)
     *
     * <p>사번(eno) 중복 여부를 확인하고, 비밀번호를 암호화하여 사용자 정보를 저장합니다.
     *
     * <p>Self-registration: 최초 등록자(fstEnrUsid)와 최종 수정자(lstChgUsid)를 본인 사번으로 설정합니다.
     *
     * @param request 회원가입 요청 DTO (사번, 이름, 비밀번호)
     * @throws RuntimeException 이미 존재하는 사번인 경우
     */
    @Transactional
    public void signup(AuthDto.SignupRequest request) {
        // 사번 중복 확인
        if (userRepository.existsByEno(request.getEno())) {
            throw new RuntimeException("이미 존재하는 사번입니다.");
        }

        // 사용자 엔티티 생성 (비밀번호는 암호화하여 저장)
        CuserI user =
                CuserI.builder()
                        .eno(request.getEno()) // 사번 (PK)
                        .usrNm(request.getEmpNm()) // 사용자명
                        .usrEcyPwd(passwordEncoder.encode(request.getPassword())) // 암호화된 비밀번호
                        .delYn("N") // 삭제여부: 미삭제
                        .fstEnrDtm(LocalDateTime.now()) // 최초 등록 일시
                        .lstChgDtm(LocalDateTime.now()) // 최종 변경 일시
                        .fstEnrUsid(request.getEno()) // 최초 등록자: 본인 (Self-registration)
                        .lstChgUsid(request.getEno()) // 최종 변경자: 본인
                        .build();

        userRepository.save(user);
    }

    /**
     * 수동 로그인 자격증명을 검증하고 MFA 대기 거래를 등록합니다.
     *
     * @param eno 로그인 사번
     * @param password 평문 비밀번호
     * @param ipAddress 로그인 실패 이력에 기록할 클라이언트 IP
     * @param userAgent 로그인 실패 이력에 기록할 User-Agent
     * @return MFA 검증에 사용할 로그인 대기 거래 식별자와 만료 시각
     * @throws LoginRejectedException 사번이 없거나 비밀번호가 일치하지 않는 경우
     */
    @Transactional(noRollbackFor = LoginRejectedException.class)
    public AuthDto.LoginStartResponse startLogin(
            String eno, String password, String ipAddress, String userAgent) {
        verifyCredentials(eno, password, ipAddress, userAgent);
        MfaDto.LoginPendingRegistration pending = mfaService.registerLoginPending(eno);
        return new AuthDto.LoginStartResponse(
                pending.pendingId(), Instant.now().plusSeconds(pending.remainingSeconds()));
    }

    /**
     * 검증된 로그인 MFA 증표를 한 번 소비하고 JWT를 발급합니다.
     *
     * @param pendingCookie 로그인 대기 거래 원문
     * @param proofCookie LOGIN 용도 MFA 증표 원문
     * @param ipAddress 로그인 성공 이력에 기록할 클라이언트 IP
     * @param userAgent 로그인 성공 이력에 기록할 User-Agent
     * @return MFA 소비 뒤 발급된 로그인 응답
     */
    @Transactional
    public AuthDto.LoginResponse completeLogin(
            String pendingCookie, String proofCookie, String ipAddress, String userAgent) {
        String eno = mfaService.consumeLoginProof(pendingCookie, proofCookie);
        CuserI user =
                userRepository
                        .findByEno(eno)
                        .orElseThrow(() -> new LoginRejectedException("사용자를 찾을 수 없습니다."));
        return issueLoginTokens(user, ipAddress, userAgent);
    }

    private CuserI verifyCredentials(
            String eno, String password, String ipAddress, String userAgent) {
        loginAttemptService.checkLocked(eno);
        Optional<CuserI> userOpt = userRepository.findByEno(eno);
        if (userOpt.isEmpty()) {
            recordLoginFailure(eno, ipAddress, userAgent, "존재하지 않는 사번");
            throw new LoginRejectedException("사용자를 찾을 수 없습니다.");
        }
        CuserI user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getUsrEcyPwd())) {
            recordLoginFailure(eno, ipAddress, userAgent, "비밀번호 불일치");
            throw new LoginRejectedException("비밀번호가 일치하지 않습니다.");
        }
        return user;
    }

    /**
     * 사용자 이름을 조회합니다.
     *
     * @param eno 조회할 사번
     * @return 사용자 이름, 없으면 {@code Unknown}
     */
    @Transactional(readOnly = true)
    public String getUserName(String eno) {
        return userRepository
                .findByEno(eno)
                .map(value -> value.getUsrNm()) // Optional에서 사용자명 추출
                .orElse("Unknown"); // 사용자가 없으면 기본값 반환
    }

    /**
     * 로그인 및 JWT 토큰 발급
     *
     * <p>사번과 비밀번호를 검증하고, 성공 시 Access Token과 Refresh Token을 발급합니다. 실패 이력은 로그인 이력에 남기고, 10분 내 5회 실패한
     * 사번은 잠금 처리합니다.
     *
     * <p>처리 흐름:
     *
     * <ol>
     *   <li>DB에서 사번으로 사용자 조회 (없으면 로그인 실패 이력 기록 후 예외)
     *   <li>비밀번호 검증 (불일치 시 로그인 실패 이력 기록 후 예외)
     *   <li>Access Token 생성 (단기 유효)
     *   <li>Refresh Token 생성 및 DB 저장 (사번 기준 기존 토큰 삭제 후 신규 저장)
     *   <li>로그인 성공 이력 기록
     *   <li>토큰 및 사용자 정보 반환 (컨트롤러에서 httpOnly 쿠키로 변환)
     * </ol>
     *
     * <p>SEC-09: 사용자 미존재·비밀번호 불일치는 "예상된 로그인 거부"로 간주해 {@link LoginRejectedException}을 던집니다. {@code
     * login()}은 {@code noRollbackFor = LoginRejectedException.class}로 선언되어 있어, 이 예외가 발생해도 트랜잭션은
     * 롤백되지 않고 직전에 저장한 실패 이력이 그대로 커밋됩니다. 그래야 {@link LoginAttemptService#checkLocked}가 커밋된 이력을 기준으로
     * 잠금 여부를 정확히 판단할 수 있습니다. 반대로 잠금 예외, 이력 저장 중 DB 오류, 토큰 발급 등 성공 경로 이후의 예기치 못한 오류는 이 타입으로 변환되지 않고
     * 원래 예외 그대로 전파되어 트랜잭션이 정상적으로 롤백됩니다.
     *
     * @param eno 로그인할 사번
     * @param password 입력한 비밀번호 (평문)
     * @param ipAddress 클라이언트 IP 주소 (이력 기록용)
     * @param userAgent 클라이언트 User-Agent 문자열 (이력 기록용)
     * @return 로그인 응답 DTO (쿠키 생성에 사용할 토큰, 사번, 사용자명, 자격등급)
     * @throws LoginRejectedException 사용자 미존재, 비밀번호 불일치 시 (실패 이력은 커밋됨)
     * @throws RuntimeException 실패 횟수 초과로 계정이 잠긴 경우({@code CustomGeneralException}), 그 외 예기치 못한 오류 시
     */
    @Transactional(noRollbackFor = LoginRejectedException.class)
    private AuthDto.LoginResponse issueLoginTokens(
            CuserI user, String ipAddress, String userAgent) {
        String eno = user.getEno();
        List<String> athIds = userRoleResolver.resolveAthIds(eno);
        String accessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());
        String refreshTokenValue = issueNewRefreshFamily(eno);
        recordLoginSuccess(eno, ipAddress, userAgent);
        return AuthDto.LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .eno(eno)
                .empNm(user.getUsrNm())
                .athIds(athIds)
                .bbrC(user.getBbrC())
                .temC(user.getTemC())
                .build();
    }

    /**
     * Access Token 갱신 — 비-트랜잭션 오케스트레이터 (SEC-08 Phase A Task 5).
     *
     * <p>이 메서드 자체는 {@code @Transactional}이 아닙니다. 실제 DB 비관적 쓰기 잠금 하 조회·회전은 {@link
     * RefreshTokenRotator#rotate(String)}의 별도 트랜잭션이 담당하고, 패밀리 폐기는 {@link
     * RefreshTokenRevoker#revokeByEno(String)}의 {@code REQUIRES_NEW} 트랜잭션이 그 회전 트랜잭션이 완전히 종료되어 잠금이
     * 풀린 뒤에만 수행합니다. 두 책임을 하나의 트랜잭션으로 묶지 않는 이유는, 같은 행에 대한 폐기 DELETE가 회전 SELECT ... FOR UPDATE의 잠금을
     * 기다리며 불필요한 대기·교착 위험을 만들기 때문입니다.
     *
     * <p><b>호출 제약</b>: 이 메서드는 이미 열려 있는 트랜잭션 안에서 호출되면 안 됩니다. 그렇게 호출되면 {@code rotate()}가 {@code
     * REQUIRED} 전파로 그 트랜잭션에 합류해, {@code rotate()}가 반환된 뒤에도 PESSIMISTIC_WRITE 잠금이 바깥 트랜잭션이 끝날 때까지
     * 유지되어 이 설계로 없애려던 락 경합·교착 위험이 되살아납니다. 현재 유일한 호출자인 {@code
     * AuthController#refresh(HttpServletRequest)}도 트랜잭션 없이 호출합니다.
     *
     * <p>SEC-08 상태 전이(ASCII):
     *
     * <pre>{@code
     * [JWT 용도/서명/만료 검증]
     *      |
     *      +--실패--------------------------------> InvalidRefreshTokenException (rotate() 미호출)
     *      |
     *      v 성공
     * [rotate() 트랜잭션: 비관적 쓰기 잠금 하 조회]
     *      |
     *      +--정상 회전-----------------------------> RefreshResponse 반환
     *      |
     *      +--ConcurrentRefreshException(grace 내)---> 재시도 가능 예외("잠시 후 다시 시도")
     *      |     (패밀리 유지, Revoker 미호출)
     *      |
     *      +--RefreshTokenNotFoundException---------> InvalidRefreshTokenException (Revoker 미호출)
     *      |     (조회 자체가 실패 — 폐기할 패밀리 없음)
     *      |
     *      +--FamilyRevocationRequiredException------> [revokeByEno(eno): REQUIRES_NEW]
     *      |     (REUSED 재사용 | EXPIRED 만료)              |
     *      |                                                  +--성공--> InvalidRefreshTokenException
     *      |                                                  +--실패--> 원본 예외 그대로 전파
     *      |                                                        (실패한 폐기를 성공처럼 감추지 않음)
     *      |
     *      +--그 외 예기치 못한 예외------------------> 원본 타입 그대로 전파 (변환·캐치 금지)
     * }</pre>
     *
     * @param refreshTokenValue 클라이언트가 제출한 Refresh Token 문자열
     * @return 토큰 갱신 응답 DTO (새로운 Access Token + 회전된 Refresh Token)
     * @throws InvalidRefreshTokenException 재로그인이 필요한 분기 — 용도·서명·만료 검증 실패, DB 미존재, 재사용·만료 감지 후 패밀리
     *     폐기 완료
     * @throws ConcurrentRefreshException 일시적 동시 새로고침(grace 내 재제출) — 재시도 가능, 패밀리는 유지된다
     * @throws RuntimeException 패밀리 폐기(revokeByEno) 자체가 실패했거나, 그 외 예기치 못한 오류인 경우 (원본 타입 그대로 전파)
     */
    public AuthDto.RefreshResponse refreshAccessToken(String refreshTokenValue) {
        // 용도 강제(1차 검증: JwtUtil) — 서명·만료와 함께 tokenUse=refresh만 허용한다.
        // 레거시(용도 클레임 없음) Refresh 토큰은 allowLegacy=false로 거부해 재로그인을 유도한다.
        // rotate()의 사전조건이며, 트랜잭션·비관적 쓰기 잠금 진입 전에 fail-fast로 걸러내 락 보유 시간을 최소화한다.
        if (!jwtUtil.validateToken(refreshTokenValue, JwtUtil.TOKEN_USE_REFRESH, false)) {
            throw new InvalidRefreshTokenException();
        }

        try {
            RefreshRotationResult result = refreshTokenRotator.rotate(refreshTokenValue);
            return AuthDto.RefreshResponse.builder()
                    .accessToken(result.accessToken()) // 새 Access Token
                    .refreshToken(result.refreshToken()) // 회전된 Refresh Token (컨트롤러가 쿠키 재설정)
                    .build();
        } catch (ConcurrentRefreshException e) {
            // grace 기간 내 다중 탭 동시 새로고침 — 패밀리는 유지하고 이번 요청만 거부한다(재시도 가능).
            // 이미 unchecked RuntimeException이며 InvalidRefreshTokenException이 아니므로 그대로 던지면
            // 컨트롤러가 재로그인을 강제하지 않는다 — 재래핑은 타입만 잃을 뿐 이득이 없다.
            throw e;
        } catch (FamilyRevocationRequiredException e) {
            // rotate()의 트랜잭션(및 비관적 쓰기 잠금)이 이미 종료된 뒤이므로 여기서 폐기를 확정한다.
            try {
                refreshTokenRevoker.revokeByEno(e.getEno());
            } catch (RuntimeException revokeFailure) {
                // 탈취 의심(재사용)·만료로 확정된 패밀리의 폐기 자체가 실패한 상황 — 삼키지 않고 그대로
                // 전파해야 한다(실패한 폐기가 성공처럼 보이면 안 된다). 일반 400과 구분되도록 원인 사번·사유를
                // 남겨 알림·모니터링에서 그렙 가능하게 한다.
                log.error(
                        "Refresh Token 패밀리 폐기 실패 — eno={}, reason={}",
                        e.getEno(),
                        e.getReason(),
                        revokeFailure);
                throw revokeFailure;
            }
            throw new InvalidRefreshTokenException();
        } catch (RefreshTokenNotFoundException e) {
            // 조회 자체가 실패했으므로 폐기할 패밀리가 없다 — Revoker를 호출하지 않는다.
            throw new InvalidRefreshTokenException();
        }
    }

    /**
     * 유효한 Access Token으로 확인된 사용자의 화면 세션 정보를 조회합니다.
     *
     * <p>브라우저의 {@code it-portal-user} 쿠키가 없거나 아직 Nuxt 상태에 반영되지 않은 경우, 서버가 검증한 사번을 기준으로 최신 사용자·권한
     * 정보를 다시 구성합니다. JWT나 Refresh Token은 응답에 포함하지 않습니다.
     *
     * @param eno Access Token 검증으로 확인된 사용자 사번
     * @return 화면 인증 상태 복원에 필요한 사용자·권한·소속 정보
     * @throws RuntimeException 사번에 해당하는 사용자가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public AuthDto.LoginResponse getSessionUser(String eno) {
        CuserI user =
                userRepository
                        .findByEno(eno)
                        .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + eno));
        List<String> athIds = userRoleResolver.resolveAthIds(eno);

        return AuthDto.LoginResponse.builder()
                .eno(eno)
                .empNm(user.getUsrNm())
                .athIds(athIds)
                .bbrC(user.getBbrC())
                .temC(user.getTemC())
                .build();
    }

    /**
     * 로그아웃 (Refresh Token 삭제)
     *
     * <p>DB에 저장된 Refresh Token을 삭제하여 세션을 무효화합니다. Access Token은 stateless이므로 서버에서 직접 무효화할 수 없으며,
     * 클라이언트가 토큰을 삭제하는 방식으로 처리합니다.
     *
     * @param eno 로그아웃할 사용자의 사번
     * @param ipAddress 클라이언트 IP 주소 (이력 기록용)
     * @param userAgent 클라이언트 User-Agent 문자열 (이력 기록용)
     */
    @Transactional
    public void logout(String eno, String ipAddress, String userAgent) {
        refreshTokenRepository.deleteByEno(eno); // 해당 사번의 Refresh Token 삭제

        // 로그아웃 이력 기록
        recordLogout(eno, ipAddress, userAgent);
    }

    /**
     * Refresh 쿠키의 SHA-256 조회값으로 토큰 소유자 패밀리를 폐기합니다.
     *
     * <p>Access Token이 만료되어 SecurityContext가 비어 있어도 Refresh 쿠키가 유효하면 서버 토큰 패밀리를 삭제합니다. Access 사용자와
     * Refresh 소유자가 다르면 두 사용자의 패밀리를 모두 폐기하며, 토큰 값과 사번은 로그에 남기지 않습니다.
     *
     * @param refreshTokenValue Refresh 쿠키 원문
     * @param authenticatedEno Access Token 인증 사번, 인증 정보가 없으면 {@code null}
     * @param ipAddress 클라이언트 IP 주소
     * @param userAgent 클라이언트 User-Agent
     */
    @Transactional
    public void logoutByRefreshToken(
            String refreshTokenValue, String authenticatedEno, String ipAddress, String userAgent) {
        Crtokm stored =
                refreshTokenValue == null || refreshTokenValue.isBlank()
                        ? null
                        : refreshTokenRepository
                                .findByEcyRnwPubTokCone(
                                        tokenFingerprint.forRefreshToken(refreshTokenValue))
                                .orElse(null);
        String tokenEno = stored == null ? null : stored.getEno();
        if (tokenEno != null) {
            refreshTokenRepository.deleteByEno(tokenEno);
        }
        if (authenticatedEno != null
                && !authenticatedEno.isBlank()
                && !authenticatedEno.equals(tokenEno)) {
            log.warn("로그아웃 Access/Refresh 사용자 불일치 — 두 토큰 패밀리를 폐기합니다.");
            refreshTokenRepository.deleteByEno(authenticatedEno);
        }
        String historyEno = tokenEno != null ? tokenEno : authenticatedEno;
        if (historyEno != null && !historyEno.isBlank()) {
            recordLogout(historyEno, ipAddress, userAgent);
        }
    }

    /**
     * 개발 편의용 사용자 전환 토큰 발급
     *
     * <p>비밀번호 검증 없이 지정 사번으로 Access/Refresh 토큰을 재발급합니다. 호출 컨트롤러가 관리자 권한 또는 개발 전용 기능 플래그를 먼저 검증해야
     * 합니다.
     *
     * <p>로그인 이력에는 IP/User-Agent를 {@code "USER-SWITCH"} 값으로 남겨 일반 로그인, SSO 로그인과 구분합니다.
     *
     * @param eno 전환 대상 사번
     * @return 쿠키 발급에 사용할 로그인 응답 DTO
     * @throws RuntimeException 사번에 해당하는 사용자가 없는 경우
     */
    @Transactional
    public AuthDto.LoginResponse issueUserSwitchTokens(String eno) {
        CuserI user =
                userRepository
                        .findByEno(eno)
                        .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + eno));

        List<String> athIds = userRoleResolver.resolveAthIds(eno);

        String accessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());
        String refreshTokenValue = issueNewRefreshFamily(eno);

        recordLoginSuccess(eno, "USER-SWITCH", "USER-SWITCH");

        return AuthDto.LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .eno(eno)
                .empNm(user.getUsrNm())
                .athIds(athIds)
                .bbrC(user.getBbrC())
                .temC(user.getTemC())
                .build();
    }

    /**
     * SSO 인증 완료 후 애플리케이션 JWT와 화면 복원용 사용자 정보를 발급합니다.
     *
     * <p>일반 로그인과 달리 비밀번호 검증을 하지 않습니다. 이 메서드는 반드시 SSO Agent 또는 {@code SsoController}가 "이미 외부 SSO 인증이
     * 끝났다"고 판단한 뒤에만 호출되어야 합니다. 따라서 운영 전환 시에는 사번 파라미터를 그대로 믿지 말고 SSO 서명/세션 검증이 끝난 사용자 식별자만 전달해야 합니다.
     *
     * <p>동작 순서:
     *
     * <ol>
     *   <li>사번으로 사용자 기본 정보 조회
     *   <li>사용자 자격등급 목록을 조회해 Access Token 클레임에 포함
     *   <li>Access Token과 Refresh Token 생성
     *   <li>기존 Refresh Token을 삭제하고 새 Refresh Token을 저장해 중복 세션을 정리
     *   <li>SSO 로그인 성공 이력을 남기고 프론트 쿠키 생성에 필요한 응답 DTO 반환
     * </ol>
     *
     * @param eno SSO 인증 결과로 확인된 사번
     * @return 쿠키 발급에 사용할 로그인 응답 DTO
     * @throws RuntimeException 사번에 해당하는 사용자가 없거나 토큰 발급/저장에 실패한 경우
     */
    @Transactional
    public AuthDto.LoginResponse issueSsoTokens(String eno) {
        CuserI user =
                userRepository
                        .findByEno(eno)
                        .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + eno));

        List<String> athIds = userRoleResolver.resolveAthIds(eno);

        String accessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());
        String refreshTokenValue = issueNewRefreshFamily(eno);

        recordLoginSuccess(eno, "SSO", "SSO");

        return AuthDto.LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .eno(eno)
                .empNm(user.getUsrNm())
                .athIds(athIds)
                .bbrC(user.getBbrC())
                .temC(user.getTemC())
                .build();
    }

    /**
     * 신규 패밀리로 Refresh Token 발급·저장 (로그인/SSO/개발스위치 진입점).
     *
     * <p>기존 토큰을 모두 삭제(1인 1패밀리)하고 새 패밀리(FAM_NM)로 활성(AVL_YN='Y') 토큰을 저장한다.
     */
    private String issueNewRefreshFamily(String eno) {
        refreshTokenRepository.deleteByEno(eno);
        String value = jwtUtil.generateRefreshToken(eno);
        String tokenHash = tokenFingerprint.forRefreshToken(value);
        Crtokm token =
                Crtokm.create(
                        tokenHash,
                        tokenHash,
                        eno,
                        java.util.UUID.randomUUID().toString(),
                        LocalDateTime.now().plus(Duration.ofMillis(refreshTokenValidityMs)));
        refreshTokenRepository.save(token);
        return value;
    }

    /**
     * 로그인 성공 이력을 저장합니다.
     *
     * <p>일반 로그인과 SSO 로그인 모두 이 메서드를 사용합니다. SSO 로그인은 실제 IP/User-Agent를 알 수 없는 테스트 흐름에서 {@code "SSO"}
     * 값을 전달해 로그인 이력 화면에서 인증 방식을 구분할 수 있게 합니다.
     *
     * @param eno 로그인 성공한 사번
     * @param ipAddress 접속 IP 주소 또는 SSO 식별 문자열
     * @param userAgent 접속 User-Agent 또는 SSO 식별 문자열
     */
    private void recordLoginSuccess(String eno, String ipAddress, String userAgent) {
        Clognh loginHistory = Clognh.createLoginSuccess(eno, ipAddress, userAgent);
        loginHistoryRepository.save(loginHistory);
    }

    /**
     * 로그인 실패 이력 기록 (내부 헬퍼 메서드)
     *
     * <p>{@link Clognh#createLoginFailure(String, String, String, String)} 팩토리 메서드를 사용하여
     * LOGIN_FAILURE 타입의 이력을 생성하고 저장합니다.
     *
     * @param eno 로그인 시도한 사번
     * @param ipAddress 접속 IP 주소
     * @param userAgent 접속 User-Agent
     * @param failureReason 실패 사유 (예: "비밀번호 불일치", "존재하지 않는 사번")
     */
    private void recordLoginFailure(
            String eno, String ipAddress, String userAgent, String failureReason) {
        Clognh loginHistory = Clognh.createLoginFailure(eno, ipAddress, userAgent, failureReason);
        loginHistoryRepository.save(loginHistory);
    }

    /**
     * 로그아웃 이력 기록 (내부 헬퍼 메서드)
     *
     * <p>{@link Clognh#createLogout(String, String, String)} 팩토리 메서드를 사용하여 LOGOUT 타입의 이력을 생성하고
     * 저장합니다.
     *
     * @param eno 로그아웃한 사번
     * @param ipAddress 접속 IP 주소
     * @param userAgent 접속 User-Agent
     */
    private void recordLogout(String eno, String ipAddress, String userAgent) {
        Clognh loginHistory = Clognh.createLogout(eno, ipAddress, userAgent);
        loginHistoryRepository.save(loginHistory);
    }
}
