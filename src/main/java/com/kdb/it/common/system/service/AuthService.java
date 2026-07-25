package com.kdb.it.common.system.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.LoginAttemptService;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.entity.Crtokm;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import com.kdb.it.common.system.repository.RefreshTokenRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.exception.InvalidRefreshTokenException;
import com.kdb.it.exception.LoginRejectedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
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

    /** 역할관리(사용자↔자격등급 매핑) 데이터 접근 리포지토리 (TPRMPP_CROLEI) */
    private final RoleRepository roleRepository;

    /** 로그인 Brute-force 감지 서비스 — SEC-03 */
    private final LoginAttemptService loginAttemptService;

    /** JWT Access/Refresh Token 생성 및 검증 유틸리티 */
    private final JwtUtil jwtUtil;

    @Value("${jwt.refresh-token-validity}")
    private long refreshTokenValidityMs;

    /** Refresh Token 회전 직후 동시 새로고침(다중 탭) 허용 grace 기간(초). 이 기간 내 회전된 토큰 재제출은 패밀리 폐기 없이 거부만 한다. */
    @org.springframework.beans.factory.annotation.Value(
            "${app.auth.refresh-rotation-grace-seconds:30}")
    private long rotationGraceSeconds;

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
     * 사용자 이름 조회
     *
     * <p>사번으로 사용자 이름을 조회합니다. 사용자가 없으면 "Unknown"을 반환합니다.
     *
     * @param eno 조회할 사번
     * @return 사용자 이름 (없으면 "Unknown")
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
    public AuthDto.LoginResponse login(
            String eno, String password, String ipAddress, String userAgent) {
        // Brute-force 차단 — 10분 내 5회 이상 실패 시 계정 잠금 (SEC-03)
        // 잠금 예외는 사용자 조회 이전에 발생하므로 실패 이력을 추가로 남기지 않으며, LoginRejectedException으로도
        // 변환되지 않는다 (잠금 판정 자체를 재시도로 흐리지 않기 위해 트랜잭션은 그대로 롤백된다).
        loginAttemptService.checkLocked(eno);

        // 사용자 조회 — 없으면 실패 이력 기록 후 예외 (메시지 문자열 매칭 없이 타입으로 분기)
        Optional<CuserI> userOpt = userRepository.findByEno(eno);
        if (userOpt.isEmpty()) {
            recordLoginFailure(eno, ipAddress, userAgent, "존재하지 않는 사번");
            throw new LoginRejectedException("사용자를 찾을 수 없습니다.");
        }
        CuserI user = userOpt.get();

        // 비밀번호 검증 (SHA-256 + Base64 방식)
        if (!passwordEncoder.matches(password, user.getUsrEcyPwd())) {
            recordLoginFailure(eno, ipAddress, userAgent, "비밀번호 불일치");
            throw new LoginRejectedException("비밀번호가 일치하지 않습니다.");
        }

        // 사용자의 모든 활성 자격등급 조회 (다중 자격등급 지원)
        List<String> athIds = loadAthIds(eno);

        String accessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());

        // 기존 Refresh Token 삭제 후 새 패밀리로 토큰 저장 (1인 1패밀리 정책)
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
     * Access Token 갱신
     *
     * <p>만료된 Access Token 대신 유효한 Refresh Token을 사용하여 새로운 Access Token을 발급합니다.
     *
     * <p>처리 흐름:
     *
     * <ol>
     *   <li>Refresh Token JWT 서명/만료 검증
     *   <li>DB에서 Refresh Token 존재 여부 확인
     *   <li>DB 저장 만료일 기준 만료 여부 재확인 (보안 이중 검증)
     *   <li>새로운 Access Token 생성
     *   <li>Refresh Token 회전: 제출된 토큰 폐기 후 신규 발급·저장 (탈취 재사용 방어)
     *   <li>새 Access Token + 회전된 Refresh Token 반환
     * </ol>
     *
     * @param refreshTokenValue 클라이언트가 제출한 Refresh Token 문자열
     * @return 토큰 갱신 응답 DTO (새로운 Access Token + 회전된 Refresh Token)
     * @throws InvalidRefreshTokenException 재로그인이 필요한 분기 — 용도·서명·만료 검증 실패, DB 미존재, 저장 만료, 재사용 감지(패밀리
     *     폐기)
     * @throws RuntimeException 일시적 동시 새로고침(grace 내 재제출)이거나 사용자 미존재인 경우
     */
    @Transactional
    public AuthDto.RefreshResponse refreshAccessToken(String refreshTokenValue) {
        // 용도 강제(1차 검증: JwtUtil) — 서명·만료와 함께 tokenUse=refresh만 허용한다.
        // 레거시(용도 클레임 없음) Refresh 토큰은 allowLegacy=false로 거부해 재로그인을 유도한다.
        if (!jwtUtil.validateToken(refreshTokenValue, JwtUtil.TOKEN_USE_REFRESH, false)) {
            throw new InvalidRefreshTokenException();
        }

        // DB에서 Refresh Token 조회 (2차 검증: DB 존재 여부)
        Crtokm refreshToken = findRefreshTokenByValue(refreshTokenValue);

        // 참고: 배포 전 토큰은 FAM_NM='LEGACY'로 백필됨 — 동일 사용자의 LEGACY 행이 한 패밀리명을 공유하나,
        // 폐기는 deleteByEno(사용자 단위)라 보안상 안전(과다 폐기=재로그인 유도). 다음 로그인 시 LEGACY 행 정리됨.
        // 재사용 탐지(AVL_YN='N' 구 토큰 재제출). 단, 회전 직후 grace 기간 내 재제출은
        // 다중 탭 동시 새로고침으로 간주 → 패밀리 유지, 이 요청만 거부(공유 쿠키의 신규 토큰으로 사용자는 유지됨).
        if (refreshToken.isRotated()) {
            java.time.LocalDateTime rotatedAt = refreshToken.getLstChgDtm();
            boolean withinGrace =
                    rotatedAt != null
                            && java.time.Duration.between(rotatedAt, java.time.LocalDateTime.now())
                                            .getSeconds()
                                    <= rotationGraceSeconds;
            if (withinGrace) {
                throw new RuntimeException("토큰이 방금 갱신되었습니다. 잠시 후 다시 시도하세요.");
            }
            log.warn(
                    "Refresh Token 재사용 탐지 — 패밀리 폐기: eno={}, famNm={}",
                    refreshToken.getEno(),
                    refreshToken.getFamNm());
            refreshTokenRepository.deleteByEno(refreshToken.getEno());
            // 재사용 감지는 재로그인 대상 — 전용 예외로 통일(원인은 위 warn 로그로만 구분, 토큰 값 미기록).
            throw new InvalidRefreshTokenException();
        }

        // DB 저장 만료일 기준 만료 여부 확인 (3차 검증: endDtm 필드)
        if (refreshToken.isExpired()) {
            // 만료도 재로그인 대상 — 원인은 서버 로그로만 구분(토큰 값 미기록), 전용 예외로 통일.
            log.warn("만료된 Refresh Token — 삭제 후 재로그인 유도: eno={}", refreshToken.getEno());
            refreshTokenRepository.delete(refreshToken); // 만료된 토큰 즉시 삭제
            throw new InvalidRefreshTokenException();
        }

        // Refresh 시에도 최신 자격등급 반영 (자격등급 변경 시 즉시 적용)
        String eno = refreshToken.getEno();
        CuserI user =
                userRepository
                        .findByEno(eno)
                        .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        List<String> athIds = loadAthIds(eno);

        // 새로운 Access Token 생성 (최신 자격등급 및 부서코드 반영)
        String newAccessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());

        // 회전: 구 토큰을 삭제하지 않고 '회전됨' 표식 유지(재사용 탐지용), 신규 토큰을 동일 패밀리로 저장
        refreshToken.markRotated();
        refreshTokenRepository.save(refreshToken);
        String newRefreshTokenValue = jwtUtil.generateRefreshToken(eno);
        String newRefreshTokenHash = sha256HexForToken(newRefreshTokenValue);
        Crtokm rotated =
                Crtokm.create(
                        newRefreshTokenHash,
                        newRefreshTokenHash,
                        eno,
                        refreshToken.getFamNm(),
                        LocalDateTime.now().plus(Duration.ofMillis(refreshTokenValidityMs)));
        refreshTokenRepository.save(rotated);
        validateSingleActiveToken(refreshToken.getFamNm());

        return AuthDto.RefreshResponse.builder()
                .accessToken(newAccessToken) // 새 Access Token
                .refreshToken(newRefreshTokenValue) // 회전된 Refresh Token (컨트롤러가 쿠키 재설정)
                .build();
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
        List<String> athIds = loadAthIds(eno);

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
                                .findByEcyRnwPubTokCone(sha256HexForToken(refreshTokenValue))
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
     * <p>비밀번호 검증 없이 지정 사번으로 Access/Refresh 토큰을 재발급합니다. {@code DevAuthController}의 사용자 전환 팝업에서만
     * 호출되며, 운영 환경에서는 {@code app.dev.user-switch.enabled=false}로 컨트롤러 자체를 비활성화해야 합니다.
     *
     * <p>로그인 이력에는 IP/User-Agent를 {@code "DEV-SWITCH"} 값으로 남겨 일반 로그인, SSO 로그인과 구분합니다.
     *
     * @param eno 전환 대상 사번
     * @return 쿠키 발급에 사용할 로그인 응답 DTO
     * @throws RuntimeException 사번에 해당하는 사용자가 없는 경우
     */
    @Transactional
    public AuthDto.LoginResponse issueDevSwitchTokens(String eno) {
        CuserI user =
                userRepository
                        .findByEno(eno)
                        .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + eno));

        List<String> athIds = loadAthIds(eno);

        String accessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());
        String refreshTokenValue = issueNewRefreshFamily(eno);

        recordLoginSuccess(eno, "DEV-SWITCH", "DEV-SWITCH");

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

        List<String> athIds = loadAthIds(eno);

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
        String tokenHash = sha256HexForToken(value);
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
     * Refresh Token 원문을 조회용 SHA-256 HEX 값으로 변환합니다.
     *
     * @param token Refresh Token 원문
     * @return 소문자 SHA-256 HEX 문자열
     */
    public static String sha256HexForToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    /** Refresh Token 원문을 SHA-256 조회값으로 변환해 저장 행을 조회합니다. */
    private Crtokm findRefreshTokenByValue(String refreshTokenValue) {
        String lookupValue = sha256HexForToken(refreshTokenValue);
        return refreshTokenRepository
                .findByEcyRnwPubTokCone(lookupValue)
                // DB 미존재도 재로그인 대상 — 원인은 서버 로그로만 구분하고 토큰 값은 기록하지 않습니다.
                .orElseThrow(
                        () -> {
                            log.warn("Refresh Token 조회 실패 — DB에 활성 토큰 없음");
                            return new InvalidRefreshTokenException();
                        });
    }

    /**
     * 회전 완료 후 같은 패밀리에 활성 Refresh Token이 1개만 남았는지 검증합니다.
     *
     * @param famNm 검증할 토큰 패밀리명
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

    private List<String> loadAthIds(String eno) {
        List<String> athIds =
                roleRepository.findAllByIdEnoAndUseYnAndDelYn(eno, "Y", "N").stream()
                        .map(value -> value.getAthId())
                        .toList();
        return athIds.isEmpty() ? List.of(CustomUserDetails.ATH_USER) : athIds;
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
