package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 결재 처리와 결재선 변경이 실제 Oracle 행 잠금을 공유하는지 검증합니다. */
@Tag("it")
@SpringBootTest(
        properties = {"jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"})
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
@Import(MfaTestSupportConfig.class)
class ApprovalLineConcurrencyIT {

    private static final String APPROVER_ENO = "ITEST15";

    @Autowired private ApplicationService applicationService;
    @Autowired private ApprovalLineManagementService approvalLineManagementService;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private ApproverRepository approverRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String apfMngNo;

    @BeforeEach
    void setUp() {
        apfMngNo = "APF-LOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        runAs(
                APPROVER_ENO,
                () ->
                        new TransactionTemplate(transactionManager)
                                .executeWithoutResult(
                                        status -> {
                                            applicationRepository.save(
                                                    Capplm.builder()
                                                            .apfMngNo(apfMngNo)
                                                            .dcdReqInf(
                                                                    "{\"approvalLine\":{\"teamLead\":{\"id\":\""
                                                                            + APPROVER_ENO
                                                                            + "\"}}}")
                                                            .itPtlApfPrgStsC(
                                                                    ApprovalStatus.IN_PROGRESS
                                                                            .code())
                                                            .build());
                                            approverRepository.save(
                                                    Cdecim.builder()
                                                            .dcdMngNo(apfMngNo)
                                                            .dcrSqnSno(1)
                                                            .dcrEno(APPROVER_ENO)
                                                            .itPtlDcdStsC(
                                                                    DecisionStatus.PENDING.code())
                                                            .lstDcdYn("Y")
                                                            .dcdTpC(Cdecim.DECISION_TYPE_REQUEST)
                                                            .build());
                                        }));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM TPRMPP_CDECIM WHERE APF_DCM_NO = ?", apfMngNo);
        jdbcTemplate.update("DELETE FROM TPRMPP_CAPPLM WHERE APF_DCM_NO = ?", apfMngNo);
        jdbcTemplate.update("DELETE FROM TPRMPP_CAPPLL WHERE APF_DCM_NO = ?", apfMngNo);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("승인 트랜잭션이 잠금을 보유하면 결재선 교체는 대기 후 완료 상태를 다시 읽어 거부한다")
    void replacePendingApprovers_waitsForApproveThenRejectsCompletedApplication() throws Exception {
        CountDownLatch approvalLocked = new CountDownLatch(1);
        CountDownLatch replacementStarted = new CountDownLatch(1);
        CountDownLatch approveNow = new CountDownLatch(1);
        CountDownLatch approvalCompletedButUncommitted = new CountDownLatch(1);
        CountDownLatch commitApproval = new CountDownLatch(1);
        AtomicReference<Thread> replacementThread = new AtomicReference<>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> approval =
                    executor.submit(
                            () ->
                                    runAs(
                                            APPROVER_ENO,
                                            () ->
                                                    new TransactionTemplate(transactionManager)
                                                            .executeWithoutResult(
                                                                    status -> {
                                                                        applicationRepository
                                                                                .findByIdForUpdate(
                                                                                        apfMngNo)
                                                                                .orElseThrow();
                                                                        approvalLocked.countDown();
                                                                        await(approveNow, "승인 시작");
                                                                        applicationService.approve(
                                                                                apfMngNo,
                                                                                approveRequest(),
                                                                                APPROVER_ENO);
                                                                        approvalCompletedButUncommitted
                                                                                .countDown();
                                                                        await(
                                                                                commitApproval,
                                                                                "승인 커밋");
                                                                    })));
            assertThat(approvalLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> replacement =
                    executor.submit(
                            () -> {
                                replacementThread.set(Thread.currentThread());
                                replacementStarted.countDown();
                                try {
                                    runAs(
                                            APPROVER_ENO,
                                            () ->
                                                    approvalLineManagementService
                                                            .replacePendingApprovers(
                                                                    apfMngNo,
                                                                    List.of("E-NEW"),
                                                                    APPROVER_ENO,
                                                                    false));
                                    return null;
                                } catch (Throwable failure) {
                                    return failure;
                                }
                            });
            assertThat(replacementStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(hasOracleJdbcCall(replacementThread.get())).isTrue();
            assertThat(completesWithin(replacement, 1, TimeUnit.SECONDS)).isFalse();

            approveNow.countDown();
            assertThat(approvalCompletedButUncommitted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(completesWithin(replacement, 1, TimeUnit.SECONDS)).isFalse();

            commitApproval.countDown();
            approval.get(20, TimeUnit.SECONDS);
            Throwable failure = replacement.get(20, TimeUnit.SECONDS);

            assertThat(failure).isInstanceOf(IllegalStateException.class);
            assertThat(failure).hasMessageContaining("결재중인 신청서만");
        } finally {
            approveNow.countDown();
            commitApproval.countDown();
        }

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT IT_PTL_APF_PRG_STS_C FROM TPRMPP_CAPPLM WHERE APF_DCM_NO = ?",
                                String.class,
                                apfMngNo))
                .isEqualTo(ApprovalStatus.COMPLETED.code());
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT DCR_ENO || ':' || IT_PTL_DCD_STS_C "
                                        + "FROM TPRMPP_CDECIM WHERE APF_DCM_NO = ? "
                                        + "ORDER BY DCR_SQN_SNO",
                                String.class,
                                apfMngNo))
                .containsExactly(APPROVER_ENO + ":" + DecisionStatus.APPROVED.code());
    }

    private ApplicationDto.ApproveRequest approveRequest() {
        ApplicationDto.ApproveRequest request = new ApplicationDto.ApproveRequest();
        request.setDcdOpnn("동시성 승인");
        request.setDcdSts("승인");
        return request;
    }

    @Test
    void corruptV2ApprovalRollsBackMasterAndDecisionRows() throws Exception {
        var root = com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.v2();
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.at("/payload/projects/0"))
                .put("name", "변조된 스냅샷");
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                applicationRepository
                                        .findByIdForUpdate(apfMngNo)
                                        .orElseThrow()
                                        .updateDetailContent(root.toString()));
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                runAs(
                                        APPROVER_ENO,
                                        () ->
                                                applicationService.approve(
                                                        apfMngNo, approveRequest(), APPROVER_ENO)))
                .isInstanceOf(com.kdb.it.exception.DataCorruptionException.class)
                .hasMessageContaining("payloadDigest");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT IT_PTL_APF_PRG_STS_C FROM TPRMPP_CAPPLM WHERE APF_DCM_NO = ?",
                                String.class,
                                apfMngNo))
                .isEqualTo(ApprovalStatus.IN_PROGRESS.code());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT IT_PTL_DCD_STS_C FROM TPRMPP_CDECIM WHERE APF_DCM_NO = ?",
                                String.class,
                                apfMngNo))
                .isEqualTo(DecisionStatus.PENDING.code());
    }

    private void runAs(String eno, Runnable action) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        eno, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void await(CountDownLatch latch, String description) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(description + " 대기 제한시간을 초과했습니다.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private boolean completesWithin(Future<?> future, long timeout, TimeUnit unit)
            throws Exception {
        try {
            future.get(timeout, unit);
            return true;
        } catch (TimeoutException expected) {
            return false;
        }
    }

    /** 첫 트랜잭션이 잠근 행을 얻으려는 실제 Oracle JDBC 호출 진입을 대기합니다. */
    private boolean hasOracleJdbcCall(Thread thread) throws InterruptedException {
        for (int attempt = 0; attempt < 400; attempt++) {
            boolean inOracleJdbc =
                    java.util.Arrays.stream(thread.getStackTrace())
                            .anyMatch(frame -> frame.getClassName().startsWith("oracle.jdbc."));
            if (inOracleJdbc) return true;
            Thread.sleep(25);
        }
        return false;
    }
}
