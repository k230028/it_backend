package com.kdb.it.common.system.service;

import com.kdb.it.common.iam.entity.CroleI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.service.LoginAttemptService;
import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.entity.Crtokm;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import com.kdb.it.common.system.repository.RefreshTokenRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 인증(Authentication) 서비스
 *
 * <p>사용자 회원가입, 로그인, 토큰 갱신, 로그아웃 비즈니스 로직을 처리합니다.</p>
 *
 * <p>인증 방식: JWT 기반 Stateless 인증</p>
 * <ul>
 *   <li>Access Token: 단기 유효 (기본 15분), httpOnly 쿠키로 자동 전송</li>
 *   <li>Refresh Token: 장기 유효 (기본 7일), DB에 저장, Access Token 갱신에 사용</li>
 * </ul>
 *
 * <p>로그인 이력: 로그인 성공/실패, 로그아웃 시 {@link Clognh}에 자동 기록됩니다.</p>
 *
 * <p>비밀번호 처리: {@link PasswordEncoder} (SHA-256 + Base64 방식)로 암호화합니다.</p>
 */
@Service             // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
public class AuthService {

    /** 사용자 정보 데이터 접근 리포지토리 (TPRMPP_CUSERI) */
    private final UserRepository userRepository;

    /** Refresh Token 데이터 접근 리포지토리 (TPRMPP_CRTOKM) */
    private final RefreshTokenRepository refreshTokenRepository;

    /** 로그인 이력 데이터 접근 리포지토리 (LOGIN_HISTORY) */
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

    /**
     * 회원가입 (사용자 등록)
     *
     * <p>사번(eno) 중복 여부를 확인하고, 비밀번호를 암호화하여 사용자 정보를 저장합니다.</p>
     *
     * <p>Self-registration: 최초 등록자(fstEnrUsid)와 최종 수정자(lstChgUsid)를
     * 본인 사번으로 설정합니다.</p>
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
        CuserI user = CuserI.builder()
                .eno(request.getEno())                            // 사번 (PK)
                .usrNm(request.getEmpNm())                        // 사용자명
                .usrEcyPwd(passwordEncoder.encode(request.getPassword())) // 암호화된 비밀번호
                .delYn("N")                                       // 삭제여부: 미삭제
                .fstEnrDtm(LocalDateTime.now())                   // 최초 등록 일시
                .lstChgDtm(LocalDateTime.now())                   // 최종 변경 일시
                .fstEnrUsid(request.getEno())                     // 최초 등록자: 본인 (Self-registration)
                .lstChgUsid(request.getEno())                     // 최종 변경자: 본인
                .build();

        userRepository.save(user);
    }

    /**
     * 사용자 이름 조회
     *
     * <p>사번으로 사용자 이름을 조회합니다. 사용자가 없으면 "Unknown"을 반환합니다.</p>
     *
     * @param eno 조회할 사번
     * @return 사용자 이름 (없으면 "Unknown")
     */
    @Transactional(readOnly = true)
    public String getUserName(String eno) {
        return userRepository.findByEno(eno)
                .map(CuserI::getUsrNm) // Optional에서 사용자명 추출
                .orElse("Unknown");    // 사용자가 없으면 기본값 반환
    }

    /**
     * 로그인 및 JWT 토큰 발급
     *
     * <p>사번과 비밀번호를 검증하고, 성공 시 Access Token과 Refresh Token을 발급합니다.
     * 실패 이력은 로그인 이력에 남기고, 10분 내 5회 실패한 사번은 잠금 처리합니다.</p>
     *
     * <p>처리 흐름:</p>
     * <ol>
     *   <li>DB에서 사번으로 사용자 조회 (없으면 로그인 실패 이력 기록 후 예외)</li>
     *   <li>비밀번호 검증 (불일치 시 로그인 실패 이력 기록 후 예외)</li>
     *   <li>Access Token 생성 (단기 유효)</li>
     *   <li>Refresh Token 생성 및 DB 저장 (사번 기준 기존 토큰 삭제 후 신규 저장)</li>
     *   <li>로그인 성공 이력 기록</li>
     *   <li>토큰 및 사용자 정보 반환 (컨트롤러에서 httpOnly 쿠키로 변환)</li>
     * </ol>
     *
     * @param eno       로그인할 사번
     * @param password  입력한 비밀번호 (평문)
     * @param ipAddress 클라이언트 IP 주소 (이력 기록용)
     * @param userAgent 클라이언트 User-Agent 문자열 (이력 기록용)
     * @return 로그인 응답 DTO (쿠키 생성에 사용할 토큰, 사번, 사용자명, 자격등급)
     * @throws RuntimeException 사용자 미존재, 비밀번호 불일치, 실패 횟수 초과 잠금 시
     */
    @Transactional
    public AuthDto.LoginResponse login(String eno, String password, String ipAddress, String userAgent) {
        // Brute-force 차단 — 10분 내 5회 이상 실패 시 계정 잠금 (SEC-03)
        loginAttemptService.checkLocked(eno);

        // 사용자 조회 — 없으면 실패 이력 기록 후 예외 (메시지 문자열 매칭 없이 타입으로 분기)
        Optional<CuserI> userOpt = userRepository.findByEno(eno);
        if (userOpt.isEmpty()) {
            recordLoginFailure(eno, ipAddress, userAgent, "존재하지 않는 사번");
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        CuserI user = userOpt.get();

        // 비밀번호 검증 (SHA-256 + Base64 방식)
        if (!passwordEncoder.matches(password, user.getUsrEcyPwd())) {
            recordLoginFailure(eno, ipAddress, userAgent, "비밀번호 불일치");
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        // 사용자의 모든 활성 자격등급 조회 (다중 자격등급 지원)
        List<String> athIds = loadAthIds(eno);

        String accessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());
        String refreshTokenValue = jwtUtil.generateRefreshToken(eno);

        // 기존 Refresh Token 삭제 후 새 토큰 저장 (1인 1토큰 정책)
        refreshTokenRepository.deleteByEno(eno);
        Crtokm refreshToken = Crtokm.builder()
                .tokCone(refreshTokenValue)
                .eno(eno)
                .endDtm(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenValidityMs)))
                .build();
        refreshTokenRepository.save(refreshToken);

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
     * <p>만료된 Access Token 대신 유효한 Refresh Token을 사용하여
     * 새로운 Access Token을 발급합니다.</p>
     *
     * <p>처리 흐름:</p>
     * <ol>
     *   <li>Refresh Token JWT 서명/만료 검증</li>
     *   <li>DB에서 Refresh Token 존재 여부 확인</li>
     *   <li>DB 저장 만료일 기준 만료 여부 재확인 (보안 이중 검증)</li>
     *   <li>새로운 Access Token 생성 및 반환</li>
     * </ol>
     *
     * @param refreshTokenValue 클라이언트가 제출한 Refresh Token 문자열
     * @return 토큰 갱신 응답 DTO (새로운 Access Token)
     * @throws RuntimeException Refresh Token이 유효하지 않거나 만료된 경우
     */
    @Transactional
    public AuthDto.RefreshResponse refreshAccessToken(String refreshTokenValue) {
        // JWT 서명/만료 검증 (1차 검증: JwtUtil)
        if (!jwtUtil.validateToken(refreshTokenValue)) {
            throw new RuntimeException("유효하지 않은 Refresh Token입니다.");
        }

        // DB에서 Refresh Token 조회 (2차 검증: DB 존재 여부)
        Crtokm refreshToken = refreshTokenRepository.findByTokCone(refreshTokenValue)
                .orElseThrow(() -> new RuntimeException("Refresh Token을 찾을 수 없습니다."));

        // DB 저장 만료일 기준 만료 여부 확인 (3차 검증: endDtm 필드)
        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken); // 만료된 토큰 즉시 삭제
            throw new RuntimeException("만료된 Refresh Token입니다.");
        }

        // Refresh 시에도 최신 자격등급 반영 (자격등급 변경 시 즉시 적용)
        String eno = refreshToken.getEno();
        CuserI user = userRepository.findByEno(eno)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        List<String> athIds = loadAthIds(eno);

        // 새로운 Access Token 생성 (최신 자격등급 및 부서코드 반영)
        String newAccessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());

        return AuthDto.RefreshResponse.builder()
                .accessToken(newAccessToken) // 새로 발급된 Access Token
                .build();
    }

    /**
     * 로그아웃 (Refresh Token 삭제)
     *
     * <p>DB에 저장된 Refresh Token을 삭제하여 세션을 무효화합니다.
     * Access Token은 stateless이므로 서버에서 직접 무효화할 수 없으며,
     * 클라이언트가 토큰을 삭제하는 방식으로 처리합니다.</p>
     *
     * @param eno       로그아웃할 사용자의 사번
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
     * 개발 편의용 사용자 전환 토큰 발급
     *
     * <p>비밀번호 검증 없이 지정 사번으로 Access/Refresh 토큰을 재발급합니다.
     * {@code DevAuthController}의 사용자 전환 팝업에서만 호출되며, 운영 환경에서는
     * {@code app.dev.user-switch.enabled=false}로 컨트롤러 자체를 비활성화해야 합니다.</p>
     *
     * <p>로그인 이력에는 IP/User-Agent를 {@code "DEV-SWITCH"} 값으로 남겨 일반 로그인,
     * SSO 로그인과 구분합니다.</p>
     *
     * @param eno 전환 대상 사번
     * @return 쿠키 발급에 사용할 로그인 응답 DTO
     * @throws RuntimeException 사번에 해당하는 사용자가 없는 경우
     */
    @Transactional
    public AuthDto.LoginResponse issueDevSwitchTokens(String eno) {
        CuserI user = userRepository.findByEno(eno)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + eno));

        List<String> athIds = loadAthIds(eno);

        String accessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());
        String refreshTokenValue = jwtUtil.generateRefreshToken(eno);

        refreshTokenRepository.deleteByEno(eno);
        Crtokm refreshToken = Crtokm.builder()
                .tokCone(refreshTokenValue)
                .eno(eno)
                .endDtm(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenValidityMs)))
                .build();
        refreshTokenRepository.save(refreshToken);

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
     * <p>일반 로그인과 달리 비밀번호 검증을 하지 않습니다. 이 메서드는 반드시
     * SSO Agent 또는 {@code SsoController}가 "이미 외부 SSO 인증이 끝났다"고 판단한 뒤에만
     * 호출되어야 합니다. 따라서 운영 전환 시에는 사번 파라미터를 그대로 믿지 말고
     * SSO 서명/세션 검증이 끝난 사용자 식별자만 전달해야 합니다.</p>
     *
     * <p>동작 순서:</p>
     * <ol>
     *   <li>사번으로 사용자 기본 정보 조회</li>
     *   <li>사용자 자격등급 목록을 조회해 Access Token 클레임에 포함</li>
     *   <li>Access Token과 Refresh Token 생성</li>
     *   <li>기존 Refresh Token을 삭제하고 새 Refresh Token을 저장해 중복 세션을 정리</li>
     *   <li>SSO 로그인 성공 이력을 남기고 프론트 쿠키 생성에 필요한 응답 DTO 반환</li>
     * </ol>
     *
     * @param eno SSO 인증 결과로 확인된 사번
     * @return 쿠키 발급에 사용할 로그인 응답 DTO
     * @throws RuntimeException 사번에 해당하는 사용자가 없거나 토큰 발급/저장에 실패한 경우
     */
    @Transactional
    public AuthDto.LoginResponse issueSsoTokens(String eno) {
        CuserI user = userRepository.findByEno(eno)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + eno));

        List<String> athIds = loadAthIds(eno);

        String accessToken = jwtUtil.generateAccessToken(eno, athIds, user.getBbrC());
        String refreshTokenValue = jwtUtil.generateRefreshToken(eno);

        refreshTokenRepository.deleteByEno(eno);
        Crtokm refreshToken = Crtokm.builder()
                .tokCone(refreshTokenValue)
                .eno(eno)
                .endDtm(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenValidityMs)))
                .build();
        refreshTokenRepository.save(refreshToken);

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

    private List<String> loadAthIds(String eno) {
        List<String> athIds = roleRepository
                .findAllByIdEnoAndUseYnAndDelYn(eno, "Y", "N")
                .stream()
                .map(CroleI::getAthId)
                .collect(Collectors.toList());
        return athIds.isEmpty() ? List.of(CustomUserDetails.ATH_USER) : athIds;
    }

    /**
     * 로그인 성공 이력을 저장합니다.
     *
     * <p>일반 로그인과 SSO 로그인 모두 이 메서드를 사용합니다. SSO 로그인은 실제 IP/User-Agent를
     * 알 수 없는 테스트 흐름에서 {@code "SSO"} 값을 전달해 로그인 이력 화면에서 인증 방식을
     * 구분할 수 있게 합니다.</p>
     *
     * @param eno       로그인 성공한 사번
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
     * LOGIN_FAILURE 타입의 이력을 생성하고 저장합니다.</p>
     *
     * @param eno           로그인 시도한 사번
     * @param ipAddress     접속 IP 주소
     * @param userAgent     접속 User-Agent
     * @param failureReason 실패 사유 (예: "비밀번호 불일치", "존재하지 않는 사번")
     */
    private void recordLoginFailure(String eno, String ipAddress, String userAgent, String failureReason) {
        Clognh loginHistory = Clognh.createLoginFailure(eno, ipAddress, userAgent, failureReason);
        loginHistoryRepository.save(loginHistory);
    }

    /**
     * 로그아웃 이력 기록 (내부 헬퍼 메서드)
     *
     * <p>{@link Clognh#createLogout(String, String, String)} 팩토리 메서드를 사용하여
     * LOGOUT 타입의 이력을 생성하고 저장합니다.</p>
     *
     * @param eno       로그아웃한 사번
     * @param ipAddress 접속 IP 주소
     * @param userAgent 접속 User-Agent
     */
    private void recordLogout(String eno, String ipAddress, String userAgent) {
        Clognh loginHistory = Clognh.createLogout(eno, ipAddress, userAgent);
        loginHistoryRepository.save(loginHistory);
    }
}
