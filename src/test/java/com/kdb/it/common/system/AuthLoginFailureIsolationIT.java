package com.kdb.it.common.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.exception.LoginRejectedException;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공개 {@code POST /api/auth/login/start}의 로그인 거부 커밋·잠금·롤백 계약을 실제 로컬 Oracle과 실제 HTTP 디스패치로 검증하는 통합
 * 테스트 (SEC-09 Task 3).
 *
 * <p>Task 1(비인증 컨텍스트에서 {@code JpaAuditConfig.auditorProvider()}가 빈 값을 반환하고 {@code Clognh} 팩토리가
 * {@code "SYSTEM"} 감사자를 명시 기록), Task 2({@code AuthService#login}의
 * {@code @Transactional(noRollbackFor = LoginRejectedException.class)}로 로그인 거부 실패 이력이 커밋됨)가 실제 운영
 * 경로 그대로 — 즉 {@code SecurityContext}를 인위적으로 주입하지 않은 anonymous 상태의 진짜 HTTP 요청 — 에서도 성립하는지 증명한다.
 *
 * <p>검증 시나리오:
 *
 * <ul>
 *   <li>SecurityContext가 비어있는 상태에서 {@code POST /api/auth/login/start}를 잘못된 비밀번호로 5회 호출하면 각 응답은 현재
 *       로그인 거부 HTTP 계약(400 + {@link com.kdb.it.exception.GlobalExceptionHandler}의 일반 {@code
 *       RuntimeException} 처리기 메시지)을 반환하고, Oracle에는 정확히 5건의 {@code IT_PTL_LGN_TC='2'} 이력이 {@code
 *       FST_ENR_USID=LST_CHG_USID='SYSTEM'}으로 커밋된다.
 *   <li>6번째 호출은 {@link com.kdb.it.common.iam.service.LoginAttemptService}의 계정 잠금 계약(400 + 잠금 안내
 *       메시지)을 반환하고 추가 실패 이력을 남기지 않는다(카운트 5 유지).
 *   <li>실패 이력 저장 경로({@link LoginHistoryRepository#save})에 예기치 못한 DB 오류를 주입하면 {@link
 *       LoginRejectedException}으로 변환되지 않고 원인 예외 그대로 전파되며, 트랜잭션 전체가 롤백되어 실패 이력이 커밋되지 않는다.
 * </ul>
 *
 * <p>로그인 성공 시 갱신토큰({@code TPRMPP_CRTOKM}) 감사자가 토큰 소유자 사번으로 채워지는지는 이미 {@link
 * com.kdb.it.common.system.service.AuthServiceCrtokmAuditIT}에서 검증되므로 여기서는 재구현하지 않는다.
 *
 * <p>테스트 전용 고정 사번({@link #TEST_ENO})은 {@code FST_ENR_USID}/{@code LST_CHG_USID} 컬럼 길이(14자, 실 스키마
 * 확인) 이내로 제한한 12자 값이다.
 */
@Tag("it")
@SpringBootTest(
        properties = {
            // 비-prod 기동 필수값 — application.properties 의 ${JWT_SECRET} 플레이스홀더를
            // 대체(EnvironmentValidator 통과).
            "jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
@Import(MfaTestSupportConfig.class)
class AuthLoginFailureIsolationIT {

    private static final String LOGIN_URL = "/api/auth/login/start";

    /** 테스트 전용 고정 사번 — FST_ENR_USID/LST_CHG_USID 컬럼 길이(14자, 실 스키마 확인) 이내로 제한한 12자 값. */
    private static final String TEST_ENO = "ZZITLGNFAIL1";

    private static final String RAW_PASSWORD = "IT-Test-Passw0rd!";
    private static final String WRONG_PASSWORD = "IT-Test-WrongPwd!";

    /** {@code LoginAttemptService.MAX_FAILURES}를 미러링(잠금 트리거 실패 횟수). */
    private static final int MAX_FAILURES = 5;

    /**
     * {@code GlobalExceptionHandler#handleRuntimeException}이 반환하는 일반 메시지 — {@link
     * LoginRejectedException}은 전용 핸들러가 없어 이 경로로 처리된다(현재 HTTP 계약 고정 검증용).
     */
    private static final String GENERIC_REJECTION_MESSAGE = "요청을 처리할 수 없습니다.";

    /**
     * {@code LoginAttemptService#checkLocked}의 계정 잠금 메시지 — {@code CustomGeneralException} 전용 핸들러가
     * 그대로 노출한다(현재 HTTP 계약 고정 검증용).
     */
    private static final String LOCK_MESSAGE = "계정 잠금: 10분 내 로그인 실패가 5회 이상입니다. 잠시 후 다시 시도하세요.";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** 실패 이력 저장 경로에 DB 오류를 주입하기 위한 spy — 실제 리포지토리를 감싼다. */
    @MockitoSpyBean private LoginHistoryRepository loginHistoryRepository;

    /**
     * 테스트 사용자를 시드한다.
     *
     * <p>{@code CuserI}도 BaseEntity를 상속해 FST_ENR_USID/LST_CHG_USID가 NOT NULL이므로, 시드 저장 한 번에 한해 인증
     * 컨텍스트("ITEST01")를 심어 일반 JPA Auditing 경로로 채운다. 이후 로그인 API의 실제 런타임 상태(익명)를 재현하기 위해 컨텍스트를 비운다 —
     * {@code AuthServiceCrtokmAuditIT}와 동일한 패턴.
     */
    @BeforeEach
    void seedUser() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "ITEST01",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        userRepository.save(
                CuserI.builder()
                        .eno(TEST_ENO)
                        .usrNm("SEC09 로그인실패격리 통합테스트")
                        .usrEcyPwd(passwordEncoder.encode(RAW_PASSWORD))
                        .delYn("N")
                        .build());
        // 로그인 API는 비인증(anonymous) 컨텍스트에서 호출되므로, 시드 후 컨텍스트를 비워 실제 런타임 상태를 재현한다.
        SecurityContextHolder.clearContext();
    }

    /** 커밋된 시드/파생 행을 자신의 고정 사번({@link #TEST_ENO}) 기준으로만 정리한다(전체 테이블 삭제 금지). */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM TPRMPP_CRTOKM WHERE ENO = ?", TEST_ENO);
        jdbcTemplate.update("DELETE FROM TPRMPP_CLOGNH WHERE ENO = ?", TEST_ENO);
        jdbcTemplate.update("DELETE FROM TPRMPP_CUSERI WHERE ENO = ?", TEST_ENO);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("공개 로그인 실패 5회는 SYSTEM 감사자로 커밋되고 6번째는 계정 잠금으로 거부되며 추가 이력을 남기지 않는다")
    void publicLogin_5회실패는SYSTEM감사자로커밋_6번째는잠금및추가이력없음() throws Exception {
        // Scenario 1 — anonymous 컨텍스트 재현: SecurityContext를 인위적으로 주입하지 않고
        // 실제 운영 /api/auth/login/start 요청 경로를 그대로 탄다.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // Scenario 2·3 — 실제 MockMvc 디스패치로 5회 연속 실패 호출, 매 응답이 현재 로그인 거부 계약을 유지하는지 고정 검증.
        for (int attempt = 1; attempt <= MAX_FAILURES; attempt++) {
            mockMvc.perform(
                            post(LOGIN_URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .header("User-Agent", "IT-Test-Agent")
                                    .content(loginRequestJson(TEST_ENO, WRONG_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(GENERIC_REJECTION_MESSAGE));
        }

        assertThat(countFailureRows()).isEqualTo(MAX_FAILURES);
        // Scenario 4 — 5건 모두 SYSTEM 감사자로 커밋되었는지 확인.
        assertThat(countFailureRowsWithSystemAuditor()).isEqualTo(MAX_FAILURES);

        // Scenario 5 — 6번째 시도는 Brute-force 잠금(SEC-03)으로 거부되고 실패 이력을 추가로 남기지 않는다.
        mockMvc.perform(
                        post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("User-Agent", "IT-Test-Agent")
                                .content(loginRequestJson(TEST_ENO, WRONG_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(LOCK_MESSAGE));

        assertThat(countFailureRows()).isEqualTo(MAX_FAILURES);
    }

    @Test
    @DisplayName("실패 이력 저장 중 예기치 못한 DB 오류는 LoginRejectedException으로 변환되지 않고 트랜잭션 전체가 롤백된다")
    void 로그인_이력저장DB오류_원인예외전파및전체롤백() {
        // Scenario 6 — MockMvc가 아닌 서비스 계층(authService.startLogin)을 직접 호출한다.
        // GlobalExceptionHandler를 거치면 이 예외도 결국 500 응답으로 변환되어 HTTP 계약만으로는
        // "LoginRejectedException으로 변환되지 않았다"는 예외 타입 계약을 직접 확인할 수 없다.
        // 서비스 메서드를 직접 호출해 던져진 예외의 실제 타입을 assertThatThrownBy로 단언하는 편이
        // 이 시나리오의 핵심(원인 예외 보존 + 트랜잭션 롤백)을 가장 직접적으로 증명한다.
        //
        // SEQUENCE 채번 방식이라 실제 INSERT는 flush/commit 시점까지 지연될 수 있으므로,
        // save() 호출 자체에 동기적으로 예외를 던지는 spy가 유일하게 신뢰할 수 있는 주입 지점이다.
        doThrow(new DataIntegrityViolationException("강제 DB 오류 (SEC-09 격리 테스트)"))
                .when(loginHistoryRepository)
                .save(any(Clognh.class));

        assertThatThrownBy(
                        () ->
                                authService.startLogin(
                                        TEST_ENO, WRONG_PASSWORD, "127.0.0.1", "IT-Test-Agent"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(LoginRejectedException.class);

        // 트랜잭션이 전부 롤백되어 실패 이력이 단 한 건도 커밋되지 않아야 한다.
        assertThat(countFailureRows()).isZero();
    }

    private String loginRequestJson(String eno, String password) throws Exception {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setEno(eno);
        request.setPassword(password);
        return objectMapper.writeValueAsString(request);
    }

    private int countFailureRows() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM TPRMPP_CLOGNH WHERE ENO = ? AND IT_PTL_LGN_TC = ?",
                        Integer.class,
                        TEST_ENO,
                        Clognh.LOGIN_FAILURE);
        return count == null ? 0 : count;
    }

    private int countFailureRowsWithSystemAuditor() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM TPRMPP_CLOGNH WHERE ENO = ? AND IT_PTL_LGN_TC = ? AND"
                                + " FST_ENR_USID = ? AND LST_CHG_USID = ?",
                        Integer.class,
                        TEST_ENO,
                        Clognh.LOGIN_FAILURE,
                        Clognh.SYSTEM_AUDITOR,
                        Clognh.SYSTEM_AUDITOR);
        return count == null ? 0 : count;
    }
}
