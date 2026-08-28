package com.kdb.it.common.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.security.TokenFingerprint;
import com.kdb.it.common.system.exception.ConcurrentRefreshException;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.exception.InvalidRefreshTokenException;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Refresh Token 회전의 재사용·만료 패밀리 폐기 커밋과 동시 회전 무교착을 실제 로컬 Oracle에서 검증하는 통합 테스트 (SEC-08 Phase A Task
 * 6).
 *
 * <p>{@link AuthService#refreshAccessToken(String)}은 비-트랜잭션 오케스트레이터이고, 실제 DB 조회·회전은 {@code
 * RefreshTokenRotator#rotate(String)}({@code @Transactional}, {@code PESSIMISTIC_WRITE} 잠금)이, 패밀리
 * 폐기는 {@code RefreshTokenRevoker#revokeByEno(String)}({@code REQUIRES_NEW})가 각각 별도 트랜잭션에서 담당한다. 이
 * 테스트는 그 분리가 실제로 다음을 보장하는지 Mockito가 아닌 진짜 Oracle 커밋/롤백·잠금으로 증명한다.
 *
 * <ul>
 *   <li>재사용(회전된 토큰의 grace 밖 재제출)·만료 토큰은 각각 {@link InvalidRefreshTokenException}으로 거부되고, 패밀리가 실제로 커밋
 *       삭제된다(롤백되지 않음).
 *   <li>같은 활성 토큰을 두 스레드가 동시에 제출해도 교착되지 않고, 정확히 한 스레드만 회전에 성공하며 나머지는 재시도 가능한 {@link
 *       ConcurrentRefreshException}으로 거부된다(패밀리 폐기 아님).
 *   <li>회전 완료 후 패밀리에는 활성({@code AVL_YN='Y')} 토큰이 정확히 1개만 남는다.
 * </ul>
 *
 * <p>잘못된 Refresh Token 제출 시 Access/Refresh 쿠키가 {@code Max-Age=0}으로 삭제되는 HTTP 계약은 이미 {@code
 * AuthControllerTest#refresh_서비스예외_401및쿠키삭제}(Mockito 기반 컨트롤러 슬라이스)에서 검증되므로 여기서 다시 구현하지 않는다. 이 테스트는
 * 서비스·DB 계층의 실제 커밋/잠금 동작에만 집중한다.
 *
 * <p>DB 상태(REUSED/EXPIRED/ACTIVE)를 만들기 위해 {@link JwtUtil#generateRefreshToken(String)}으로 구조적으로 유효한
 * Refresh JWT를 발급하고, 그 HMAC-SHA256 지문을 {@code
 * ECY_RNW_PUB_TOK_CONE}으로 하는 {@code TPRMPP_CRTOKM} 행을 JDBC로 직접 시딩한다. JPA {@code save()}가 아닌 직접
 * INSERT를 쓰는 이유는 {@code AVL_YN}·{@code END_DTM}·{@code LST_CHG_DTM}(회전 grace 판단 기준)을 JPA
 * Auditing({@code @LastModifiedDate})의 개입 없이 원하는 과거/미래 값으로 정확히 고정해야 하기 때문이다.
 */
@Tag("it")
@SpringBootTest(
        properties = {
            // 비-prod 기동 필수값 — application.properties 의 ${JWT_SECRET} 플레이스홀더를
            // 대체(EnvironmentValidator 통과).
            "jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"
        })
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
@Import(MfaTestSupportConfig.class)
class RefreshTokenIsolationIT {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenIsolationIT.class);

    /** 테스트 전용 고정 사번 — FST_ENR_USID/LST_CHG_USID 컬럼 길이(14자, 실 스키마 확인) 이내로 제한한 11자 값. */
    private static final String TEST_ENO = "ZZITRTKISO1";

    /** 시나리오별 패밀리명 접두어 — 같은 사번 안에서도 시나리오 간 상태가 섞이지 않도록 분리한다. */
    private static final String FAM_PREFIX = "SEC08-IT-";

    /** 동시성 테스트의 교착 가드 타임아웃 — 이 시간 내 끝나지 않으면 TIMEOUT으로 실패한다(정상 잠금이면 수 초 내 종료). */
    private static final Duration DEADLOCK_GUARD_TIMEOUT = Duration.ofSeconds(20);

    /** 개별 Future 대기 타임아웃 — 외곽 assertTimeoutPreemptively보다 짧게 잡아 어느 스레드가 멈췄는지 구분한다. */
    private static final long FUTURE_GET_TIMEOUT_SECONDS = 15;

    @Autowired private AuthService authService;
    @Autowired private TokenFingerprint tokenFingerprint;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 고정 사번의 잔여 행을 먼저 정리한 뒤(크래시 복원력) 사용자를 시드한다.
     *
     * <p>{@code CuserI}도 BaseEntity를 상속해 FST_ENR_USID/LST_CHG_USID가 NOT NULL이므로, 시드 저장 한 번에 한해 인증
     * 컨텍스트("ITEST01")를 심어 일반 JPA Auditing 경로로 채운다. 이후 Refresh 갱신은 실제 운영처럼 익명(anonymous) 컨텍스트에서 호출하기
     * 위해 컨텍스트를 비운다 — {@code AuthServiceCrtokmAuditIT}와 동일한 패턴.
     */
    @BeforeEach
    void setUp() {
        deleteFixedEnoRows(); // 이전 실행이 비정상 종료했을 경우를 대비한 방어적 선정리
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "ITEST01",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        userRepository.save(
                CuserI.builder()
                        .eno(TEST_ENO)
                        .usrNm("SEC08 Refresh 동시성 격리 통합테스트")
                        .delYn("N")
                        .build());
        SecurityContextHolder.clearContext();
    }

    /** 커밋된 시드/파생 행을 자신의 고정 사번({@link #TEST_ENO}) 기준으로만 정리한다(전체 테이블 삭제 금지). */
    @AfterEach
    void cleanUp() {
        deleteFixedEnoRows();
        SecurityContextHolder.clearContext();
    }

    private void deleteFixedEnoRows() {
        jdbcTemplate.update("DELETE FROM TPRMPP_CRTOKM WHERE ENO = ?", TEST_ENO);
        jdbcTemplate.update("DELETE FROM TPRMPP_CLOGNH WHERE ENO = ?", TEST_ENO);
        jdbcTemplate.update("DELETE FROM TPRMPP_CUSERI WHERE ENO = ?", TEST_ENO);
    }

    @Test
    @DisplayName("grace 밖에서 재사용된(회전됨) 토큰은 InvalidRefreshTokenException으로 거부되고 패밀리가 커밋 삭제된다")
    void reuse_grace밖재사용토큰_예외및패밀리커밋삭제() {
        String famNm = FAM_PREFIX + "REUSED";
        // grace 판단 기준(LST_CHG_DTM)을 기본 grace(30초)를 넉넉히 벗어난 5분 전으로 고정 — REUSED 분기 확정.
        LocalDateTime rotatedLongAgo = LocalDateTime.now().minusMinutes(5);
        String reusedToken = seedToken(famNm, "N", LocalDateTime.now().plusDays(1), rotatedLongAgo);

        assertThatThrownBy(() -> authService.refreshAccessToken(reusedToken))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(countFamilyRows(TEST_ENO)).isZero();
    }

    @Test
    @DisplayName("DB상 만료된(END_DTM 경과) 토큰은 InvalidRefreshTokenException으로 거부되고 패밀리가 커밋 삭제된다")
    void expired_DB만료토큰_예외및패밀리커밋삭제() {
        String famNm = FAM_PREFIX + "EXPIRED";
        // JWT의 exp 클레임은 발급 시점 기준 7일 뒤(jwt.refresh-token-validity)라 validateToken은 통과하고,
        // DB에 저장된 END_DTM만 과거로 고정해 EXPIRED 분기를 확정한다.
        LocalDateTime pastEndDtm = LocalDateTime.now().minusMinutes(5);
        String expiredToken = seedToken(famNm, "Y", pastEndDtm, LocalDateTime.now());

        assertThatThrownBy(() -> authService.refreshAccessToken(expiredToken))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(countFamilyRows(TEST_ENO)).isZero();
    }

    /**
     * 동일 활성 토큰을 두 스레드가 동시에 제출해도 교착되지 않고, 정확히 한 스레드만 회전에 성공하며 나머지는 재시도 가능한 {@link
     * ConcurrentRefreshException}으로 거부되는지(패밀리 폐기 아님) 검증한다.
     *
     * <p>비관적 쓰기 잠금(PESSIMISTIC_WRITE) 하 SELECT를 두 트랜잭션이 동시에 시도하므로, Oracle 잠금 대기 큐에서 하나가 먼저 통과·커밋(잠금
     * 해제)한 뒤 다른 하나가 재조회한다. 회전 분리 설계가 깨져 있으면(예: 두 트랜잭션이 같은 트랜잭션으로 합류하거나 잠금이 제때 풀리지 않으면) 이 시나리오가 무한
     * 대기(교착)로 이어질 수 있어 {@code assertTimeoutPreemptively}로 감시한다.
     */
    @Test
    @DisplayName("동일 토큰 동시 회전 — 하나만 성공, 나머지는 재시도 가능한 동시성 예외, 패밀리는 유지되고 활성 토큰은 1개만 남는다")
    void concurrentRotation_동일토큰동시제출_단일성공및패밀리유지활성토큰1개() throws InterruptedException {
        String famNm = FAM_PREFIX + "CONCURRENT";
        String activeToken =
                seedToken(famNm, "Y", LocalDateTime.now().plusDays(1), LocalDateTime.now());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<RotationOutcome> outcomes =
                    assertTimeoutPreemptively(
                            DEADLOCK_GUARD_TIMEOUT,
                            () -> runConcurrentRotation(executor, activeToken));

            long successCount =
                    outcomes.stream()
                            .filter(outcome -> outcome.kind() == OutcomeKind.SUCCESS)
                            .count();
            long concurrentFailureCount =
                    outcomes.stream()
                            .filter(outcome -> outcome.kind() == OutcomeKind.CONCURRENT_FAILURE)
                            .count();

            // 진단 편의를 위해 결과 요약을 로그로 남긴다(실패 시 어느 조합이었는지 즉시 확인 가능).
            log.info(
                    "동시 회전 결과 — success={}, concurrentFailure={}, outcomes={}",
                    successCount,
                    concurrentFailureCount,
                    outcomes);

            assertThat(successCount).as("정확히 한 스레드만 회전에 성공해야 한다").isEqualTo(1);
            assertThat(concurrentFailureCount)
                    .as("나머지 한 스레드는 재시도 가능한 ConcurrentRefreshException이어야 한다")
                    .isEqualTo(1);
            assertThat(outcomes)
                    .as("패밀리 폐기(재사용 오탐)로 빠지면 안 된다")
                    .noneMatch(outcome -> outcome.kind() == OutcomeKind.FAMILY_REVOKED);
            assertThat(outcomes)
                    .as("예기치 못한 실패가 있으면 안 된다")
                    .noneMatch(outcome -> outcome.kind() == OutcomeKind.UNEXPECTED_FAILURE);

            assertThat(countActiveTokensInFamily(famNm))
                    .as("회전 완료 후 패밀리에는 활성 토큰이 정확히 1개만 남아야 한다")
                    .isEqualTo(1);
            // 회전된(구) 토큰(AVL_YN='N') + 신규 활성 토큰(AVL_YN='Y') = 2행, 패밀리는 폐기되지 않았다.
            assertThat(countFamilyRows(TEST_ENO)).isEqualTo(2);
        } catch (AssertionError | RuntimeException timeoutOrAssertionFailure) {
            logAllThreadStacks();
            throw timeoutOrAssertionFailure;
        } finally {
            executor.shutdownNow();
            boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                log.warn("ExecutorService가 5초 내 종료되지 않았습니다 — 스레드 누수 가능성이 있습니다.");
            }
        }
    }

    /**
     * 두 스레드를 최대한 겹치게 출발시켜 같은 토큰으로 {@link AuthService#refreshAccessToken(String)}을 동시 호출하고, 각 결과를
     * {@link RotationOutcome}으로 수집한다.
     */
    private List<RotationOutcome> runConcurrentRotation(ExecutorService executor, String tokenValue)
            throws InterruptedException {
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<RotationOutcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tasks.add(
                    () -> {
                        readyLatch.countDown();
                        startLatch.await();
                        try {
                            authService.refreshAccessToken(tokenValue);
                            return new RotationOutcome(OutcomeKind.SUCCESS, null);
                        } catch (ConcurrentRefreshException e) {
                            return new RotationOutcome(OutcomeKind.CONCURRENT_FAILURE, e);
                        } catch (InvalidRefreshTokenException e) {
                            // rotate()가 FamilyRevocationRequiredException을 던지고 revokeByEno()가
                            // 성공하면 오케스트레이터가 이 타입으로 변환한다 — 동시 회전에서 나오면 안 되는
                            // 결과(재사용 오탐)이므로 별도 종류로 구분해 단언한다.
                            return new RotationOutcome(OutcomeKind.FAMILY_REVOKED, e);
                        } catch (RuntimeException e) {
                            log.error("동시 회전 테스트 중 예상치 못한 예외", e);
                            return new RotationOutcome(OutcomeKind.UNEXPECTED_FAILURE, e);
                        }
                    });
        }

        List<Future<RotationOutcome>> futures = new ArrayList<>();
        for (Callable<RotationOutcome> task : tasks) {
            futures.add(executor.submit(task));
        }

        // 두 스레드가 모두 대기 상태에 진입할 때까지 기다린 뒤 동시에 출발시켜 잠금 경합을 최대화한다.
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        List<RotationOutcome> outcomes = new ArrayList<>();
        for (Future<RotationOutcome> future : futures) {
            try {
                outcomes.add(future.get(FUTURE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (ExecutionException | TimeoutException e) {
                // 개별 스레드가 제한 시간 내 끝나지 않으면(진짜 교착) 명확한 실패로 승격한다 — 스레드 덤프는
                // 이 메서드를 감싼 상위 try/finally(catch AssertionError)에서 남긴다.
                throw new IllegalStateException(
                        "동시 회전 Future 대기 중 실패 — 교착 의심(스레드 덤프는 상위 로그 참조)", e);
            }
        }
        return outcomes;
    }

    /** 현재 살아있는 모든 스레드의 스택을 로그로 남긴다 — 교착 의심 시 진단용. */
    private static void logAllThreadStacks() {
        log.error("=== 스레드 스택 덤프 (교착/타임아웃 의심) ===");
        Thread.getAllStackTraces()
                .forEach(
                        (thread, stackTrace) -> {
                            StringBuilder sb = new StringBuilder();
                            sb.append(thread.getName())
                                    .append(" state=")
                                    .append(thread.getState())
                                    .append('\n');
                            for (StackTraceElement element : stackTrace) {
                                sb.append("\tat ").append(element).append('\n');
                            }
                            log.error(sb.toString());
                        });
    }

    /**
     * 구조적으로 유효한 Refresh JWT를 발급하고, 그 SHA-256 조회값으로 {@code TPRMPP_CRTOKM} 행을 원하는 DB 상태로 직접 시딩한다.
     *
     * @param famNm 패밀리명
     * @param avlYn 유효여부('Y'=활성, 'N'=회전됨) — REUSED 재현 시 'N'
     * @param endDtm 종료일시 — EXPIRED 재현 시 과거, 그 외 미래
     * @param lstChgDtm 최종변경일시 — grace 판단 기준(회전 시각 역할), REUSED 재현 시 grace 밖 과거
     * @return 생성된 Refresh Token 원문(JWT)
     */
    private String seedToken(
            String famNm, String avlYn, LocalDateTime endDtm, LocalDateTime lstChgDtm) {
        String tokenValue = jwtUtil.generateRefreshToken(TEST_ENO);
        String tokenHash = fingerprint(tokenValue);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_CRTOKM "
                        + "(LGN_LOG_SNO, API_TOK_CONE, ECY_RNW_PUB_TOK_CONE, ENO, END_DTM, FAM_NM, AVL_YN, "
                        + "FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (SQ_TPRMPP_CRTOKM_1.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, 'N', ?, 1, ?, ?)",
                tokenHash,
                tokenHash,
                TEST_ENO,
                Timestamp.valueOf(endDtm),
                famNm,
                avlYn,
                TEST_ENO,
                Timestamp.valueOf(now),
                UUID.randomUUID().toString(),
                TEST_ENO,
                Timestamp.valueOf(lstChgDtm));
        return tokenValue;
    }

    private int countFamilyRows(String eno) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM TPRMPP_CRTOKM WHERE ENO = ?", Integer.class, eno);
        return count == null ? 0 : count;
    }

    private int countActiveTokensInFamily(String famNm) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM TPRMPP_CRTOKM WHERE FAM_NM = ? AND AVL_YN = 'Y'",
                        Integer.class,
                        famNm);
        return count == null ? 0 : count;
    }

    private String fingerprint(String token) {
        return tokenFingerprint.forRefreshToken(token);
    }

    /** 동시 회전 스레드 하나의 결과 종류. */
    private enum OutcomeKind {
        /** 회전 성공 (새 Access/Refresh Token 반환) */
        SUCCESS,
        /** grace 내 동시 재제출로 재시도 가능한 거부 (패밀리 유지) */
        CONCURRENT_FAILURE,
        /** 재사용/만료로 오판되어 패밀리가 폐기됨 — 동시 회전 시나리오에서는 나오면 안 되는 결과 */
        FAMILY_REVOKED,
        /** 위 세 가지 외의 예기치 못한 예외 */
        UNEXPECTED_FAILURE
    }

    /** 동시 회전 스레드 하나의 결과 — 종류와 원인 예외(성공 시 null)를 함께 담는다. */
    private record RotationOutcome(OutcomeKind kind, Throwable cause) {}
}
