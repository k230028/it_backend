package com.kdb.it.common.approval.itbudget.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.config.ItBudgetPreviewProperties;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.approval.mail.ApprovalMailPayloadProvider;
import com.kdb.it.common.approval.notification.ApprovalRequestNotifier;
import com.kdb.it.common.approval.repository.*;
import com.kdb.it.common.approval.service.ApplicationPersistenceService;
import com.kdb.it.common.approval.service.ApplicationPersistenceService.ApplicationDraft;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.*;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.JpaAuditConfig;
import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.service.*;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.*;

/** 실제 Oracle 행을 flush한 뒤 두 번째 문서 실패로 모든 저장과 커밋 후 이벤트가 취소되는지 확인한다. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    ItBudgetSubmissionTransactionIT.Config.class,
    ItBudgetApprovalFacade.class,
    ItBudgetSourceLoader.class,
    ItBudgetSnapshotBuilder.class,
    ItBudgetCanonicalJson.class,
    ApplicationPersistenceService.class,
    ApprovalWriteGuard.class,
    ProjectAmountCalculator.class,
    BprojaSyncService.class,
    ApprovalRequestNotifier.class,
    JpaAuditConfig.class
})
class ItBudgetSubmissionTransactionIT extends AbstractOracleRepositoryTest {
    @Autowired ItBudgetApprovalFacade facade;
    @MockitoSpyBean ApplicationPersistenceService persistence;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired MeterRegistry meterRegistry;
    @Autowired CommitEvents events;
    @MockitoBean UserRepository users;
    @MockitoBean OrganizationRepository organizations;
    @MockitoBean CodeRepository codes;
    @MockitoBean ApprovalMailPayloadProvider mail;
    String id;
    CustomUserDetails actor;
    final List<String> written = new ArrayList<>();

    @BeforeEach
    void createFixture() {
        id = "ITS7" + UUID.randomUUID().toString().substring(0, 8);
        actor = new CustomUserDetails(id, List.of(CustomUserDetails.ATH_USER), "D1");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                actor, null, actor.getAuthorities()));
        events.committed.clear();
        when(users.findByEnoIn(anyCollection()))
                .thenAnswer(
                        i -> {
                            Collection<String> enos = i.getArgument(0);
                            return enos.stream()
                                    .map(
                                            e ->
                                                    CuserI.builder()
                                                            .eno(e)
                                                            .usrNm("테스트 사용자")
                                                            .ptCNm("직급")
                                                            .bbrC("D1")
                                                            .delYn("N")
                                                            .build())
                                    .toList();
                        });
        when(users.findById(id))
                .thenReturn(Optional.of(CuserI.builder().eno(id).bbrC("D1").build()));
        tx().executeWithoutResult(
                        s -> {
                            em.persist(
                                    Bprojm.builder()
                                            .abusMngNo(id)
                                            .sno(3)
                                            .abusTc("0")
                                            .abusNm("원자성 사업")
                                            .svnDpmC("D1")
                                            .usid(id)
                                            .lstYn("N")
                                            .delYn("N")
                                            .build());
                            em.persist(
                                    Bcostm.builder()
                                            .costBgNo(id)
                                            .bgSno(2)
                                            .abusTc("0")
                                            .dfrCleC("0")
                                            .cttNm("원자성 계약")
                                            .costSvnDpmC("D1")
                                            .cgprId(id)
                                            .lstYn("N")
                                            .delYn("N")
                                            .build());
                        });
    }

    @AfterEach
    void cleanup() {
        try {
            tx().executeWithoutResult(
                            s -> {
                                em.createQuery(
                                                "delete from Cdecim c where c.dcdMngNo in (select a.apfMngNo from Capplm a where a.dcdReqUsid = :id)")
                                        .setParameter("id", id)
                                        .executeUpdate();
                                em.createQuery(
                                                "delete from Cappla c where c.apfDcmNo in (select a.apfMngNo from Capplm a where a.dcdReqUsid = :id)")
                                        .setParameter("id", id)
                                        .executeUpdate();
                                em.createQuery("delete from Capplm a where a.dcdReqUsid = :id")
                                        .setParameter("id", id)
                                        .executeUpdate();
                                em.createQuery("delete from Bproja p where p.abusMngNo = :id")
                                        .setParameter("id", id)
                                        .executeUpdate();
                                em.createQuery("delete from Bprojm p where p.abusMngNo = :id")
                                        .setParameter("id", id)
                                        .executeUpdate();
                                em.createQuery("delete from Bcostm c where c.costBgNo = :id")
                                        .setParameter("id", id)
                                        .executeUpdate();
                            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void secondDocumentFailureRollsBackFlushedRowsAndCommitEvents() {
        var request = request();
        tx().executeWithoutResult(
                        stubTransaction ->
                                doAnswer(
                                                i -> {
                                                    assertThat(
                                                                    TransactionSynchronizationManager
                                                                            .isActualTransactionActive())
                                                            .isTrue();
                                                    String number = (String) i.callRealMethod();
                                                    written.add(number);
                                                    em.flush();
                                                    assertRows(
                                                            written.size(),
                                                            written.size(),
                                                            written.size() * 2);
                                                    assertThat(events.committed).isEmpty();
                                                    if (written.size() == 2)
                                                        throw new DataIntegrityViolationException(
                                                                "두 번째 문서 저장 실패 재현");
                                                    return number;
                                                })
                                        .when(persistence)
                                        .persist(any()));
        assertThatThrownBy(() -> facade.submit(actor, request))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(written).hasSize(2);
        tx().executeWithoutResult(
                        s -> {
                            assertRows(0, 0, 0);
                            assertThat(
                                            em.createQuery(
                                                            "select count(p) from Bproja p where p.abusMngNo = :id",
                                                            Long.class)
                                                    .setParameter("id", id)
                                                    .getSingleResult())
                                    .isZero();
                        });
        assertThat(events.committed).isEmpty();
    }

    @Test
    void successCommitsBothDocumentsAndPublishesOnlyAfterCommit() {
        var request = request();
        tx().executeWithoutResult(
                        stubTransaction ->
                                doAnswer(
                                                i -> {
                                                    assertThat(
                                                                    TransactionSynchronizationManager
                                                                            .isActualTransactionActive())
                                                            .isTrue();
                                                    var number = (String) i.callRealMethod();
                                                    written.add(number);
                                                    assertThat(events.committed).isEmpty();
                                                    return number;
                                                })
                                        .when(persistence)
                                        .persist(any()));
        var response = facade.submit(actor, request);
        assertThat(response.applicationNumbers()).hasSize(2).doesNotHaveDuplicates();
        tx().executeWithoutResult(
                        s -> {
                            assertRows(2, 2, 4);
                            var links =
                                    em.createQuery(
                                                    "select c.fntTbNm from Cappla c where c.apfDcmNo = :number",
                                                    String.class)
                                            .setParameter(
                                                    "number",
                                                    response.applicationNumbers().getFirst())
                                            .getResultList();
                            assertThat(links).containsExactly("BCOSTM");
                            for (String number : response.applicationNumbers()) {
                                var stored =
                                        em.find(
                                                com.kdb.it.common.approval.entity.Capplm.class,
                                                number);
                                var parsed =
                                        StoredSnapshotFixture.reader().read(stored.getDcdReqInf());
                                assertThat(
                                                parsed.approvalLine(true)
                                                        .at("/approvers/0/role")
                                                        .asText())
                                        .isEqualTo("TEAM_LEAD");
                                assertThat(
                                                parsed.approvalLine(true)
                                                        .at("/approvers/1/role")
                                                        .asText())
                                        .isEqualTo("DEPT_HEAD");
                            }
                        });
        assertThat(events.committed).hasSize(2);
    }

    @Test
    void commitTimeFailureRecordsOnlyErrorAfterTheTransactionCompletes() {
        var request = request();
        double successBefore = submissionCount("success");
        double errorBefore = submissionCount("error");
        long timerBefore = submissionTimerCount();
        var registered = new AtomicBoolean();
        tx().executeWithoutResult(
                        ignored ->
                                doAnswer(
                                                invocation -> {
                                                    String number =
                                                            (String) invocation.callRealMethod();
                                                    if (registered.compareAndSet(false, true))
                                                        TransactionSynchronizationManager
                                                                .registerSynchronization(
                                                                        new TransactionSynchronization() {
                                                                            @Override
                                                                            public void
                                                                                    beforeCommit(
                                                                                            boolean
                                                                                                    readOnly) {
                                                                                throw new DataIntegrityViolationException(
                                                                                        "커밋 시점 실패 재현");
                                                                            }
                                                                        });
                                                    return number;
                                                })
                                        .when(persistence)
                                        .persist(any()));

        assertThatThrownBy(() -> facade.submit(actor, request))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(submissionCount("success")).isEqualTo(successBefore);
        assertThat(submissionCount("error")).isEqualTo(errorBefore + 1);
        assertThat(submissionTimerCount()).isEqualTo(timerBefore + 1);
        tx().executeWithoutResult(s -> assertRows(0, 0, 0));
        assertThat(events.committed).isEmpty();
    }

    @Test
    void persistenceRequiresCallerTransaction() {
        assertThatThrownBy(
                        () ->
                                persistence.persist(
                                        new ApplicationDraft(
                                                "제목", "{}", id, null, List.of(), List.of(id))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void changedLedgerRejectsAllDocumentsWithNoCommittedRows() {
        var request = request();
        tx().executeWithoutResult(
                        s ->
                                em.createQuery(
                                                "update Bprojm p set p.abusNm = :name where p.abusMngNo = :id")
                                        .setParameter("name", "원장 변경")
                                        .setParameter("id", id)
                                        .executeUpdate());
        assertThatThrownBy(() -> facade.submit(actor, request))
                .isInstanceOfSatisfying(
                        com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException
                                .class,
                        e -> assertThat(e.code()).isEqualTo("IT_BUDGET_SOURCE_CHANGED"));
        tx().executeWithoutResult(s -> assertRows(0, 0, 0));
        assertThat(events.committed).isEmpty();
    }

    @Test
    void actualOracleLockTimeoutIsMappedToConcurrentUpdate() throws Exception {
        var request = request();
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var holder =
                    executor.submit(
                            () ->
                                    tx().executeWithoutResult(
                                                    s -> {
                                                        em.createQuery(
                                                                        "select p from Bprojm p where p.abusMngNo = :id and p.sno = 3",
                                                                        Bprojm.class)
                                                                .setParameter("id", id)
                                                                .setLockMode(
                                                                        jakarta.persistence
                                                                                .LockModeType
                                                                                .PESSIMISTIC_WRITE)
                                                                .getSingleResult();
                                                        locked.countDown();
                                                        try {
                                                            if (!release.await(
                                                                    20,
                                                                    java.util.concurrent.TimeUnit
                                                                            .SECONDS))
                                                                throw new IllegalStateException(
                                                                        "잠금 해제 제한 초과");
                                                        } catch (InterruptedException e) {
                                                            Thread.currentThread().interrupt();
                                                            throw new IllegalStateException(e);
                                                        }
                                                    }));
            assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> facade.submit(actor, request))
                    .isInstanceOfSatisfying(
                            com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException
                                    .class,
                            e -> assertThat(e.code()).isEqualTo("IT_BUDGET_CONCURRENT_UPDATE"));
            release.countDown();
            holder.get(10, java.util.concurrent.TimeUnit.SECONDS);
            tx().executeWithoutResult(s -> assertRows(0, 0, 0));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    private SubmissionRequest request() {
        var input =
                new PreviewRequest(
                        List.of(
                                new ApproverRef(ApproverRole.TEAM_LEAD, "ITSA1"),
                                new ApproverRef(ApproverRole.DEPT_HEAD, "ITSA2")),
                        List.of(
                                new DocumentRequest(
                                        "cost", List.of(new SourceRef(SourceKind.COST, id, 2, 1))),
                                new DocumentRequest(
                                        "project",
                                        List.of(new SourceRef(SourceKind.PROJECT, id, 3, 1)))));
        var preview = facade.preview(actor, input);
        return new SubmissionRequest(
                preview.previewDigest(),
                preview.previewToken(),
                input.approvers(),
                preview.documents().stream()
                        .map(
                                d ->
                                        new SubmissionDocument(
                                                d.clientDocumentKey(),
                                                d.payloadDigest(),
                                                d.sources()))
                        .toList());
    }

    private void assertRows(long masters, long links, long approvals) {
        if (!written.isEmpty()) {
            assertThat(
                            em.createQuery(
                                            "select count(a) from Capplm a where a.apfMngNo in :numbers",
                                            Long.class)
                                    .setParameter("numbers", written)
                                    .getSingleResult())
                    .isEqualTo(masters);
            assertThat(
                            em.createQuery(
                                            "select count(c) from Cappla c where c.apfDcmNo in :numbers",
                                            Long.class)
                                    .setParameter("numbers", written)
                                    .getSingleResult())
                    .isEqualTo(links);
            assertThat(
                            em.createQuery(
                                            "select count(c) from Cdecim c where c.dcdMngNo in :numbers",
                                            Long.class)
                                    .setParameter("numbers", written)
                                    .getSingleResult())
                    .isEqualTo(approvals);
            return;
        }
        assertThat(
                        em.createQuery(
                                        "select count(a) from Capplm a where a.dcdReqUsid = :id",
                                        Long.class)
                                .setParameter("id", id)
                                .getSingleResult())
                .isEqualTo(masters);
        assertThat(
                        em.createQuery(
                                        "select count(c) from Cappla c where c.apfDcmNo in (select a.apfMngNo from Capplm a where a.dcdReqUsid = :id)",
                                        Long.class)
                                .setParameter("id", id)
                                .getSingleResult())
                .isEqualTo(links);
        assertThat(
                        em.createQuery(
                                        "select count(c) from Cdecim c where c.dcdMngNo in (select a.apfMngNo from Capplm a where a.dcdReqUsid = :id)",
                                        Long.class)
                                .setParameter("id", id)
                                .getSingleResult())
                .isEqualTo(approvals);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    private double submissionCount(String outcome) {
        return meterRegistry
                .find("approval.it_budget.submission")
                .tag("outcome", outcome)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private long submissionTimerCount() {
        return meterRegistry.find("approval.it_budget.submission.duration").timers().stream()
                .mapToLong(Timer::count)
                .sum();
    }

    static class CommitEvents {
        final List<NotificationEvent> committed = new ArrayList<>();

        @TransactionalEventListener
        public void onCommit(NotificationEvent event) {
            committed.add(event);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new JacksonConfig().objectMapper();
        }

        @Bean
        ItBudgetPreviewTokenService tokens(ObjectMapper mapper) {
            return new ItBudgetPreviewTokenService(
                    new ItBudgetPreviewProperties(
                            "test",
                            "test-only-preview-key-0123456789012345",
                            null,
                            null,
                            Duration.ofMinutes(30)),
                    mapper,
                    Clock.fixed(Instant.parse("2026-09-06T05:30:00Z"), ZoneOffset.UTC));
        }

        @Bean
        CommitEvents commitEvents() {
            return new CommitEvents();
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
