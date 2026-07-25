package com.kdb.it.common.system.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.dto.AuthDto;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * AuthService의 로그인/토큰 회전이 실제 로컬 Oracle에서 갱신토큰(Crtokm) 감사자를 채우는지 검증하는 통합 테스트(SEC-09 Task 1 후속).
 *
 * <p>{@code /api/auth/login}, {@code /api/auth/refresh}는 실제 운영에서 anonymous(비인증) Security 컨텍스트로
 * 실행된다. 이 테스트는 {@code SecurityContextHolder}를 비워둔 채 {@link AuthService#login} / {@link
 * AuthService#refreshAccessToken}을 직접 호출해 그 상태를 재현하고, {@code TPRMPP_CRTOKM.FST_ENR_USID}/{@code
 * LST_CHG_USID}(NOT NULL 컬럼, 실 스키마 확인됨)가 {@code Crtokm.create}/{@code markRotated}를 통해 토큰 소유자 사번으로
 * 채워지는지 — 즉 감사자 미기록으로 인한 ORA-01400 없이 로그인·토큰 회전이 성공하는지 — 확인한다.
 *
 * <p>{@code AuthServiceTest}(순수 Mockito)는 실제 EntityManager/DB 없이 동작하므로 이 결함을 잡아낼 수 없었다. 감사 컬럼
 * 길이 제약(14자, 실 스키마 확인됨) 때문에 테스트 사번({@link #TEST_ENO})도 14자 이하로 고정한다.
 */
@Tag("it")
@SpringBootTest(
        properties = {
            // 비-prod 기동 필수값 — application.properties 의 ${JWT_SECRET} 플레이스홀더를 대체(EnvironmentValidator
            // 통과).
            "jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"
        })
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
class AuthServiceCrtokmAuditIT {

    /** 테스트 전용 고정 사번 — FST_ENR_USID/LST_CHG_USID 컬럼 길이(14자, 실 스키마 확인) 이내로 제한한 13자 값. */
    private static final String TEST_ENO = "ZZITSEC09AUTH";

    private static final String RAW_PASSWORD = "IT-Test-Passw0rd!";

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 테스트 사용자를 시드한다.
     *
     * <p>{@code CuserI}도 BaseEntity를 상속해 FST_ENR_USID/LST_CHG_USID가 NOT NULL이므로, 시드 저장 한 번에 한해 인증
     * 컨텍스트("ITEST01")를 심어 일반 JPA Auditing 경로로 채운다. 이후 로그인 API의 실제 런타임 상태(익명)를 재현하기 위해 컨텍스트를
     * 비운다.
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
                        .usrNm("SEC09 통합테스트")
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
    @DisplayName("익명 컨텍스트의 로그인·토큰 회전 모두 갱신토큰 감사자를 소유자 사번으로 채운다")
    void login그리고회전_갱신토큰감사자기록() {
        // SecurityContext가 비어있는 상태(anonymous)에서 로그인 — 운영 /api/auth/login 흐름 재현.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        AuthDto.LoginResponse loginResponse =
                authService.login(TEST_ENO, RAW_PASSWORD, "127.0.0.1", "IT-Test-Agent");

        List<TokenAuditRow> afterLogin = queryTokenAuditRows();
        assertThat(afterLogin).hasSize(1);
        assertThat(afterLogin.get(0).fstEnrUsid()).isEqualTo(TEST_ENO);
        assertThat(afterLogin.get(0).lstChgUsid()).isEqualTo(TEST_ENO);
        assertThat(afterLogin.get(0).avlYn()).isEqualTo("Y");

        // 회전(refresh) — 여전히 익명 컨텍스트에서 호출됨(Access Token 만료 후 재발급 시나리오 재현).
        authService.refreshAccessToken(loginResponse.getRefreshToken());

        List<TokenAuditRow> afterRefresh = queryTokenAuditRows();
        assertThat(afterRefresh).hasSize(2); // 회전된 구 토큰(AVL_YN=N) + 신규 활성 토큰(AVL_YN=Y)
        assertThat(afterRefresh)
                .allSatisfy(
                        row -> {
                            assertThat(row.fstEnrUsid()).isEqualTo(TEST_ENO);
                            assertThat(row.lstChgUsid()).isEqualTo(TEST_ENO);
                        });
    }

    private List<TokenAuditRow> queryTokenAuditRows() {
        return jdbcTemplate.query(
                "SELECT FST_ENR_USID, LST_CHG_USID, AVL_YN FROM TPRMPP_CRTOKM WHERE ENO = ? ORDER BY"
                        + " LGN_LOG_SNO",
                (rs, rowNum) ->
                        new TokenAuditRow(
                                rs.getString("FST_ENR_USID"),
                                rs.getString("LST_CHG_USID"),
                                rs.getString("AVL_YN")),
                TEST_ENO);
    }

    /** 조회 검증용 최소 레코드 — FST_ENR_USID/LST_CHG_USID/AVL_YN 3개 컬럼만 담는다. */
    private record TokenAuditRow(String fstEnrUsid, String lstChgUsid, String avlYn) {}
}
