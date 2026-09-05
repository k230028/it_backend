package com.kdb.it.common.approval.itbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Oracle의 서로 다른 실제 트랜잭션에서 부모 잠금과 결재 가드의 선후관계를 확인한다. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ItBudgetLedgerLockingIT extends AbstractOracleRepositoryTest {
    @Autowired ProjectRepository projects;
    @Autowired ProjectItemRepository items;
    @Autowired CostRepository costs;
    @Autowired BtermmRepository terminals;
    @Autowired ApplicationMapRepository mappings;
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager em;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void submissionLockBlocksEditAndCommittedApprovalThenRejectsChildWrite(boolean project)
            throws Exception {
        String id = fixtureId();
        createSource(project, id);
        var executor = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempting = new CountDownLatch(1);
        try {
            Future<?> submit =
                    executor.submit(
                            () ->
                                    transaction()
                                            .executeWithoutResult(
                                                    status -> {
                                                        lock(project, id);
                                                        persistApproval(project, id);
                                                        em.flush();
                                                        locked.countDown();
                                                        await(release);
                                                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> edit =
                    executor.submit(
                            () ->
                                    transaction()
                                            .executeWithoutResult(
                                                    status -> {
                                                        attempting.countDown();
                                                        lock(project, id);
                                                        new ApprovalWriteGuard(mappings)
                                                                .verifyWritable(
                                                                        table(project),
                                                                        id,
                                                                        3,
                                                                        "수정");
                                                        deleteChild(project, id);
                                                    }));
            assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> edit.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            submit.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> edit.get(10, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("결재중");
            transaction()
                    .executeWithoutResult(
                            status -> {
                                assertThat(childDeleted(project, id)).isEqualTo("N");
                                assertThat(
                                                new ApprovalWriteGuard(mappings)
                                                        .isBlocked(table(project), id, 3))
                                        .isTrue();
                            });
        } finally {
            release.countDown();
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
            } finally {
                cleanup(project, id);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void editCommitIsVisibleToSubmissionAfterItsParentLockWaits(boolean project) throws Exception {
        String id = fixtureId();
        createSource(project, id);
        var executor = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempting = new CountDownLatch(1);
        try {
            Future<?> edit =
                    executor.submit(
                            () ->
                                    transaction()
                                            .executeWithoutResult(
                                                    status -> {
                                                        lock(project, id);
                                                        new ApprovalWriteGuard(mappings)
                                                                .verifyWritable(
                                                                        table(project),
                                                                        id,
                                                                        3,
                                                                        "수정");
                                                        deleteChild(project, id);
                                                        em.flush();
                                                        locked.countDown();
                                                        await(release);
                                                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> snapshot =
                    executor.submit(
                            () ->
                                    transaction()
                                            .execute(
                                                    status -> {
                                                        attempting.countDown();
                                                        lock(project, id);
                                                        return childDeleted(project, id);
                                                    }));
            assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> snapshot.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            edit.get(10, TimeUnit.SECONDS);
            assertThat(snapshot.get(10, TimeUnit.SECONDS)).isEqualTo("Y");
        } finally {
            release.countDown();
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
            } finally {
                cleanup(project, id);
            }
        }
    }

    private TransactionTemplate transaction() {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(20);
        return template;
    }

    private void lock(boolean project, String id) {
        if (project) projects.findVersionForUpdate(id, 3).orElseThrow();
        else costs.findVersionForUpdate(id, 3).orElseThrow();
    }

    private void deleteChild(boolean project, String id) {
        if (project) items.findByAbusMngNoAndFntTbCrySno(id, 3).getFirst().delete();
        else terminals.findByTermBgNoAndTermBgSno(id, 3).getFirst().delete();
    }

    private String childDeleted(boolean project, String id) {
        return project
                ? items.findByAbusMngNoAndFntTbCrySno(id, 3).getFirst().getDelYn()
                : terminals.findByTermBgNoAndTermBgSno(id, 3).getFirst().getDelYn();
    }

    private void createSource(boolean project, String id) {
        transaction()
                .executeWithoutResult(
                        status -> {
                            var now = LocalDateTime.now();
                            if (project) {
                                em.persist(
                                        Bprojm.builder()
                                                .abusMngNo(id)
                                                .sno(3)
                                                .abusTc("0")
                                                .lstYn("Y")
                                                .delYn("N")
                                                .fstEnrUsid("TEST")
                                                .lstChgUsid("TEST")
                                                .fstEnrDtm(now)
                                                .lstChgDtm(now)
                                                .build());
                                em.persist(
                                        Bitemm.builder()
                                                .gclMngNo(id)
                                                .sno(1)
                                                .abusMngNo(id)
                                                .fntTbCrySno(3)
                                                .dfrCleC("0")
                                                .mplAmt(BigDecimal.ZERO)
                                                .delYn("N")
                                                .fstEnrUsid("TEST")
                                                .lstChgUsid("TEST")
                                                .fstEnrDtm(now)
                                                .lstChgDtm(now)
                                                .build());
                            } else {
                                em.persist(
                                        Bcostm.builder()
                                                .costBgNo(id)
                                                .bgSno(3)
                                                .abusTc("0")
                                                .dfrCleC("0")
                                                .lstYn("Y")
                                                .delYn("N")
                                                .fstEnrUsid("TEST")
                                                .lstChgUsid("TEST")
                                                .fstEnrDtm(now)
                                                .lstChgDtm(now)
                                                .build());
                                em.persist(
                                        Btermm.builder()
                                                .tmnMngNo(id)
                                                .sno(1)
                                                .termBgNo(id)
                                                .termBgSno(3)
                                                .dfrCleC("0")
                                                .delYn("N")
                                                .fstEnrUsid("TEST")
                                                .lstChgUsid("TEST")
                                                .fstEnrDtm(now)
                                                .lstChgDtm(now)
                                                .build());
                            }
                        });
    }

    private void persistApproval(boolean project, String id) {
        var now = LocalDateTime.now();
        em.persist(
                Capplm.builder()
                        .apfMngNo(id)
                        .itPtlApfPrgStsC(ApprovalStatus.IN_PROGRESS.code())
                        .delYn("N")
                        .fstEnrUsid("TEST")
                        .lstChgUsid("TEST")
                        .fstEnrDtm(now)
                        .lstChgDtm(now)
                        .build());
        em.persist(
                Cappla.builder()
                        .apfDcmNo(id)
                        .fntTbNm(table(project))
                        .pkColNm(id)
                        .fntTbCrySno(3)
                        .delYn("N")
                        .fstEnrUsid("TEST")
                        .lstChgUsid("TEST")
                        .fstEnrDtm(now)
                        .lstChgDtm(now)
                        .build());
    }

    private void cleanup(boolean project, String id) {
        transaction()
                .executeWithoutResult(
                        status -> {
                            em.createQuery("delete from Cappla c where c.apfDcmNo = :id")
                                    .setParameter("id", id)
                                    .executeUpdate();
                            em.createQuery("delete from Capplm c where c.apfMngNo = :id")
                                    .setParameter("id", id)
                                    .executeUpdate();
                            em.createQuery(
                                            project
                                                    ? "delete from Bitemm c where c.abusMngNo = :id"
                                                    : "delete from Btermm c where c.termBgNo = :id")
                                    .setParameter("id", id)
                                    .executeUpdate();
                            em.createQuery(
                                            project
                                                    ? "delete from Bprojm p where p.abusMngNo = :id"
                                                    : "delete from Bcostm p where p.costBgNo = :id")
                                    .setParameter("id", id)
                                    .executeUpdate();
                        });
    }

    private static String table(boolean project) {
        return project ? "BPROJM" : "BCOSTM";
    }

    private static String fixtureId() {
        return "ITBL-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("테스트 잠금 해제 제한 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 잠금 대기 중 인터럽트", e);
        }
    }
}
