package com.kdb.it.common.approval.itbudget.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kdb.it.common.approval.entity.*;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.common.approval.notification.ApprovalRequestNotifier;
import com.kdb.it.common.approval.repository.*;
import com.kdb.it.common.approval.service.ApplicationPersistenceService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.project.entity.*;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ItBudgetSubmissionTest {
    final ItBudgetApprovalFacadeTest f = new ItBudgetApprovalFacadeTest();
    final ApplicationRepository applications = mock(ApplicationRepository.class);
    final ApplicationMapRepository mappings = mock(ApplicationMapRepository.class);
    final ApproverRepository approvers = mock(ApproverRepository.class);
    final BprojaSyncService sync = mock(BprojaSyncService.class);
    final ApprovalRequestNotifier notifier = mock(ApprovalRequestNotifier.class);
    final ApprovalWriteGuard guard = spy(new ApprovalWriteGuard(mappings));
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final ApplicationPersistenceService persistence =
            spy(
                    new ApplicationPersistenceService(
                            applications,
                            approvers,
                            mappings,
                            f.projects,
                            f.costs,
                            f.users,
                            sync,
                            notifier));
    final ItBudgetApprovalFacade facade =
            new ItBudgetApprovalFacade(
                    f.loader,
                    f.builder,
                    f.canonical,
                    f.tokens,
                    f.users,
                    f.mapper,
                    persistence,
                    guard,
                    registry);
    final Bprojm project =
            Bprojm.builder()
                    .abusMngNo("P1")
                    .sno(1)
                    .abusNm("사업 하나")
                    .edrtTc("E1")
                    .svnDpmC("D1")
                    .fstEnrUsid("U1")
                    .usid("U1")
                    .delYn("N")
                    .lstChgUsid("U1")
                    .lstChgDtm(LocalDateTime.parse("2026-09-06T13:00:00"))
                    .build();

    @BeforeEach
    void setUp() {
        when(f.projects.findVersions(anyCollection(), anyCollection()))
                .thenReturn(List.of(project));
        when(f.projects.findVersionsForUpdate(anyCollection(), anyCollection()))
                .thenReturn(List.of(project));
        when(f.costs.findVersionsForUpdate(anyCollection(), anyCollection()))
                .thenAnswer(i -> f.costs.findVersions(i.getArgument(0), i.getArgument(1)));
        when(f.projects.findByAbusMngNoAndSnoAndDelYn("P1", 1, "N"))
                .thenReturn(Optional.of(project));
        when(f.costs.findByCostBgNoAndBgSnoAndDelYn("C1", 2, "N"))
                .thenAnswer(
                        i ->
                                Optional.of(
                                        f.costs
                                                .findVersions(List.of("C1"), List.of(2))
                                                .getFirst()));
        when(applications.getNextVal()).thenReturn(101L, 102L);
    }

    @Test
    void persistsEveryDocumentInInputOrderWithServerSnapshotAndExactLinks() throws Exception {
        var request = submission();
        var response = facade.submit(f.actor, request);
        assertThat(response.applicationNumbers())
                .containsExactly(
                        "APF-" + LocalDate.now().getYear() + "-00000101",
                        "APF-" + LocalDate.now().getYear() + "-00000102");
        var saved = ArgumentCaptor.forClass(Capplm.class);
        verify(applications, times(2)).save(saved.capture());
        var first = f.mapper.readTree(saved.getAllValues().getFirst().getDcdReqInf());
        assertThat(first.at("/form/id").asText()).isEqualTo("it-budget");
        assertThat(first.at("/form/version").asInt()).isEqualTo(2);
        assertThat(first.at("/payload/costs/0/id").asText()).isEqualTo("C1");
        assertThat(first.at("/integrity/capturedAt").asText()).isEqualTo("2026-09-06T05:30:00Z");
        assertThat(first.at("/integrity/payloadDigest").asText())
                .isEqualTo(request.documents().getFirst().payloadDigest());
        assertThat(first.at("/approvalLine/approvers/0/date").isNull()).isTrue();
        assertThat(first.at("/approvalLine/approvers/0/role").asText()).isEqualTo("TEAM_LEAD");
        assertThat(first.at("/approvalLine/approvers/1/role").asText()).isEqualTo("DEPT_HEAD");
        assertThat(saved.getAllValues())
                .allSatisfy(a -> assertThat(a.getDcdReqUsid()).isEqualTo("U1"));
        var links = ArgumentCaptor.forClass(Cappla.class);
        verify(mappings, times(2)).save(links.capture());
        assertThat(links.getAllValues())
                .extracting(Cappla::getFntTbNm)
                .containsExactly("BCOSTM", "BPROJM");
        assertThat(links.getAllValues()).extracting(Cappla::getFntTbCrySno).containsExactly(2, 1);
        var line = ArgumentCaptor.forClass(Cdecim.class);
        verify(approvers, times(4)).save(line.capture());
        assertThat(line.getAllValues())
                .extracting(Cdecim::getDcrEno)
                .containsExactly("A1", "A2", "A1", "A2");
        assertThat(line.getAllValues())
                .extracting(Cdecim::getDcrSqnSno)
                .containsExactly(1, 2, 1, 2);
        assertThat(line.getAllValues())
                .extracting(Cdecim::getLstDcdYn)
                .containsExactly("N", "Y", "N", "Y");
        var order = inOrder(f.projects, f.costs, guard, applications);
        order.verify(f.projects).findVersionsForUpdate(anyCollection(), anyCollection());
        order.verify(f.costs).findVersionsForUpdate(anyCollection(), anyCollection());
        order.verify(guard).verifyWritable("BCOSTM", "C1", 2, "상신");
        order.verify(guard).verifyWritable("BPROJM", "P1", 1, "상신");
        order.verify(applications).getNextVal();
    }

    @Test
    void recordsPreviewAndSubmissionSuccessWithOneTimerAndCounterEach() {
        facade.preview(f.actor, previewRequest());
        assertThat(registry.get("approval.it_budget.preview").timer().count()).isEqualTo(1);
        assertThat(
                        registry.get("approval.it_budget.preview.outcome")
                                .tag("outcome", "success")
                                .counter()
                                .count())
                .isEqualTo(1);

        facade.submit(f.actor, submission());

        assertThat(registry.get("approval.it_budget.submission.duration").timer().count())
                .isEqualTo(1);
        assertThat(
                        registry.get("approval.it_budget.submission")
                                .tag("outcome", "success")
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    @Test
    void previewLogsOnlyFixedOutcomesWithoutUserOrSourceIdentifiers() {
        var logger =
                (ch.qos.logback.classic.Logger)
                        org.slf4j.LoggerFactory.getLogger(ItBudgetApprovalFacade.class);
        var appender =
                new ch.qos.logback.core.read.ListAppender<
                        ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            facade.preview(f.actor, previewRequest());
            error("IT_BUDGET_PREVIEW_INVALID", 400, () -> facade.preview(f.actor, null));
            assertThat(appender.list).hasSize(2);
            assertThat(appender.list.get(0).getFormattedMessage()).contains("outcome=success");
            assertThat(appender.list.get(1).getFormattedMessage()).contains("outcome=invalid");
            assertThat(appender.list)
                    .allSatisfy(
                            event -> {
                                assertThat(event.getFormattedMessage())
                                        .doesNotContain("P1", "C1", "U1", "A1", "APF-");
                                assertThat(event.getThrowableProxy()).isNull();
                            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void recordsInvalidPreviewFailureAndStopsThePreviewTimer() {
        error("IT_BUDGET_PREVIEW_INVALID", 400, () -> facade.preview(f.actor, null));

        assertThat(registry.get("approval.it_budget.preview").timer().count()).isEqualTo(1);
        assertThat(
                        registry.get("approval.it_budget.preview.outcome")
                                .tag("outcome", "invalid")
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    @Test
    void recordsSourceChangedFailureAndStopsTheSubmissionTimer() {
        var request = submission();
        project.delete();

        error("IT_BUDGET_SOURCE_CHANGED", 409, () -> facade.submit(f.actor, request));

        assertThat(registry.get("approval.it_budget.submission.duration").timer().count())
                .isEqualTo(1);
        assertThat(
                        registry.get("approval.it_budget.submission")
                                .tag("outcome", "source_changed")
                                .counter()
                                .count())
                .isEqualTo(1);
        assertThat(
                        registry.get("approval.it_budget.submission.source_changed")
                                .tag("source_kind", "PROJECT")
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    @Test
    void countsEachChangedKindOnceAndDoesNotTagIdentifiers() {
        var request = submission();
        project.delete();
        f.costs.findVersions(List.of("C1"), List.of(2)).getFirst().delete();
        error("IT_BUDGET_SOURCE_CHANGED", 409, () -> facade.submit(f.actor, request));
        for (String kind : List.of("PROJECT", "COST")) {
            var counter =
                    registry.get("approval.it_budget.submission.source_changed")
                            .tag("source_kind", kind)
                            .counter();
            assertThat(counter.count()).isEqualTo(1);
            assertThat(counter.getId().getTags())
                    .containsExactly(io.micrometer.core.instrument.Tag.of("source_kind", kind));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void documentAndLedgerCountsWaitForCommitAndNeverCountRollback(boolean commit) {
        var request = submission();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(facade.submit(f.actor, request).applicationNumbers()).hasSize(2);
            assertThat(registry.find("approval.it_budget.submission.documents").summary()).isNull();
            var completion = TransactionSynchronizationManager.getSynchronizations().getFirst();
            int status =
                    commit
                            ? TransactionSynchronization.STATUS_COMMITTED
                            : TransactionSynchronization.STATUS_ROLLED_BACK;
            completion.afterCompletion(status);
            completion.afterCompletion(status);
            if (commit) {
                assertThat(
                                registry.get("approval.it_budget.submission.documents")
                                        .summary()
                                        .count())
                        .isEqualTo(1);
                assertThat(
                                registry.get("approval.it_budget.submission.documents")
                                        .summary()
                                        .totalAmount())
                        .isEqualTo(2);
                assertThat(
                                registry.get("approval.it_budget.submission.sources")
                                        .summary()
                                        .totalAmount())
                        .isEqualTo(2);
            } else {
                assertThat(registry.find("approval.it_budget.submission.documents").summary())
                        .isNull();
                assertThat(registry.find("approval.it_budget.submission.sources").summary())
                        .isNull();
            }
            assertThat(
                            registry.get("approval.it_budget.submission")
                                    .tag("outcome", commit ? "success" : "error")
                                    .counter()
                                    .count())
                    .isEqualTo(1);
            assertThat(registry.get("approval.it_budget.submission.duration").timer().count())
                    .isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void signatureMismatchHasSeparateMetricButBodyTamperingDoesNot() {
        var request = submission();
        String[] parts = request.previewToken().split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        String token =
                parts[0]
                        + "."
                        + parts[1]
                        + "."
                        + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        var tampered =
                new SubmissionRequest(
                        request.previewDigest(), token, request.approvers(), request.documents());
        error("IT_BUDGET_PREVIEW_INVALID", 400, () -> facade.submit(f.actor, tampered));
        error(
                "IT_BUDGET_PREVIEW_INVALID",
                400,
                () -> facade.submit(f.actor, mutate(request, "payload")));
        assertThat(registry.get("approval.it_budget.preview.signature_failure").counter().count())
                .isEqualTo(1);
        assertThat(
                        registry.get("approval.it_budget.submission")
                                .tag("outcome", "invalid")
                                .counter()
                                .count())
                .isEqualTo(2);
    }

    @Test
    void summaryFailureCannotChangeCommittedResponseOrDuration() {
        MeterRegistry failing = spy(new SimpleMeterRegistry());
        doThrow(new IllegalStateException("registry contains private data"))
                .when(failing)
                .summary(anyString(), any(String[].class));
        var request = submission();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(facadeWith(failing).submit(f.actor, request).applicationNumbers())
                    .hasSize(2);
            assertThatCode(
                            () ->
                                    TransactionSynchronizationManager.getSynchronizations()
                                            .getFirst()
                                            .afterCompletion(
                                                    TransactionSynchronization.STATUS_COMMITTED))
                    .doesNotThrowAnyException();
            assertThat(
                            failing.get("approval.it_budget.submission")
                                    .tag("outcome", "success")
                                    .counter()
                                    .count())
                    .isEqualTo(1);
            assertThat(failing.get("approval.it_budget.submission.duration").timer().count())
                    .isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void timerStartFailureDoesNotChangePreviewOrDirectSubmissionBusinessResults() {
        MeterRegistry failingRegistry = spy(new SimpleMeterRegistry());
        doThrow(new IllegalStateException("metrics unavailable")).when(failingRegistry).config();

        assertBusinessResultsSurviveMetricFailure(failingRegistry);
    }

    @Test
    void counterIncrementFailureDoesNotChangePreviewOrDirectSubmissionBusinessResults() {
        MeterRegistry failingRegistry = spy(new SimpleMeterRegistry());
        Counter failingCounter = mock(Counter.class);
        doThrow(new IllegalStateException("metrics unavailable")).when(failingCounter).increment();
        doReturn(failingCounter).when(failingRegistry).counter(anyString(), any(String[].class));

        assertBusinessResultsSurviveMetricFailure(failingRegistry);
    }

    @Test
    void meterRegistrationFailureDoesNotChangePreviewOrDirectSubmissionBusinessResults() {
        MeterRegistry failingRegistry = spy(new SimpleMeterRegistry());
        doThrow(new IllegalStateException("metrics unavailable"))
                .when(failingRegistry)
                .counter(anyString(), any(String[].class));

        assertBusinessResultsSurviveMetricFailure(failingRegistry);
    }

    @Test
    void timerStopFailureDoesNotChangePreviewOrDirectSubmissionBusinessResults() {
        MeterRegistry failingRegistry = spy(new SimpleMeterRegistry());
        Timer failingTimer = mock(Timer.class);
        doThrow(new IllegalStateException("metrics unavailable"))
                .when(failingTimer)
                .record(anyLong(), any(TimeUnit.class));
        doReturn(failingTimer).when(failingRegistry).timer(anyString(), any(String[].class));

        assertBusinessResultsSurviveMetricFailure(failingRegistry);
    }

    @Test
    void synchronizationRegistrationFailureDoesNotChangeSubmissionResponse() {
        var measured = facadeWith(new SimpleMeterRegistry());
        var request = submission();

        try (MockedStatic<TransactionSynchronizationManager> synchronizations =
                mockStatic(TransactionSynchronizationManager.class)) {
            synchronizations
                    .when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(true);
            synchronizations
                    .when(
                            () ->
                                    TransactionSynchronizationManager.registerSynchronization(
                                            any(TransactionSynchronization.class)))
                    .thenThrow(new IllegalStateException("synchronization unavailable"));

            assertThat(measured.submit(f.actor, request).applicationNumbers()).isNotEmpty();
        }
    }

    @Test
    void afterCompletionCounterFailureStillAttemptsTimerExactlyOnce() {
        MeterRegistry failingRegistry = spy(new SimpleMeterRegistry());
        Counter failingCounter = mock(Counter.class);
        Timer timer = mock(Timer.class);
        doThrow(new IllegalStateException("metrics unavailable")).when(failingCounter).increment();
        doReturn(failingCounter).when(failingRegistry).counter(anyString(), any(String[].class));
        doReturn(timer).when(failingRegistry).timer(anyString(), any(String[].class));
        var measured = facadeWith(failingRegistry);
        var request = submission();

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(measured.submit(f.actor, request).applicationNumbers()).isNotEmpty();
            var synchronization =
                    TransactionSynchronizationManager.getSynchronizations().getFirst();

            assertThatCode(
                            () ->
                                    synchronization.afterCompletion(
                                            TransactionSynchronization.STATUS_COMMITTED))
                    .doesNotThrowAnyException();

            verify(failingRegistry, times(1))
                    .timer(eq("approval.it_budget.submission.duration"), any(String[].class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "token",
                "requester",
                "preview",
                "payload",
                "source",
                "name",
                "key",
                "documentOrder",
                "sourceOrder",
                "role",
                "approver",
                "duplicate"
            })
    void rejectsTokenAndBodyTamperingBeforeAnyDatabaseRead(String mutation) {
        var original = submission();
        var request = mutate(original, mutation);
        clearInvocations(f.projects, f.costs, f.items, f.terminals, f.users, mappings);
        var actor =
                mutation.equals("requester")
                        ? new CustomUserDetails("OTHER", List.of(CustomUserDetails.ATH_USER), "D1")
                        : f.actor;
        error("IT_BUDGET_PREVIEW_INVALID", 400, () -> facade.submit(actor, request));
        verifyNoInteractions(
                f.projects, f.costs, f.items, f.terminals, f.users, mappings, applications);
    }

    @Test
    void cryptoValidExpiredTokenFailsBeforeLocks() {
        var request = submission();
        var expiredTokens =
                new ItBudgetPreviewTokenService(
                        new com.kdb.it.common.approval.itbudget.config.ItBudgetPreviewProperties(
                                "test",
                                "test-only-preview-key-0123456789012345",
                                null,
                                null,
                                Duration.ofMinutes(30)),
                        f.mapper,
                        Clock.offset(f.clock, Duration.ofMinutes(30)));
        var expiredFacade =
                new ItBudgetApprovalFacade(
                        f.loader,
                        f.builder,
                        f.canonical,
                        expiredTokens,
                        f.users,
                        f.mapper,
                        persistence,
                        guard,
                        registry);
        error("IT_BUDGET_PREVIEW_EXPIRED", 409, () -> expiredFacade.submit(f.actor, request));
        assertThat(
                        registry.get("approval.it_budget.submission")
                                .tag("outcome", "expired")
                                .counter()
                                .count())
                .isEqualTo(1);
        verify(f.loader, never()).loadForSubmission(anyList());
        verifyNoInteractions(applications);
    }

    @Test
    void childChangePrecedesDisplayStaleAndUsesLatestParentAuditOnce() {
        var request = submission();
        var latest = LocalDateTime.parse("2026-09-06T15:12:00");
        when(f.items.findSourceVersions(anyCollection(), anyCollection()))
                .thenReturn(
                        List.of(
                                Bitemm.builder()
                                        .gclMngNo("I1")
                                        .sno(3)
                                        .abusMngNo("P1")
                                        .fntTbCrySno(1)
                                        .qty(new java.math.BigDecimal("3"))
                                        .amt(new java.math.BigDecimal("7.125"))
                                        .delYn("N")
                                        .lstChgUsid("EDITOR")
                                        .lstChgDtm(latest)
                                        .build()));
        renameUser("A1");
        assertThatThrownBy(() -> facade.submit(f.actor, request))
                .isInstanceOfSatisfying(
                        ItBudgetApprovalException.class,
                        e -> {
                            assertThat(e.code()).isEqualTo("IT_BUDGET_SOURCE_CHANGED");
                            assertThat(e.changedSources())
                                    .containsExactly(
                                            new ChangedSource(
                                                    SourceKind.PROJECT,
                                                    "P1",
                                                    1,
                                                    "사업 하나",
                                                    "이름 EDITOR",
                                                    latest));
                        });
        verifyNoInteractions(applications, notifier);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "missing"})
    void deletedOrMissingAfterPreviewIsSourceChanged(String change) {
        var request = submission();
        if (change.equals("missing"))
            when(f.projects.findVersionsForUpdate(anyCollection(), anyCollection()))
                    .thenReturn(List.of());
        else project.delete();
        error("IT_BUDGET_SOURCE_CHANGED", 409, () -> facade.submit(f.actor, request));
        verifyNoInteractions(applications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"A1", "U1"})
    void changedIamDisplayOrApprovalLineIsStale(String eno) {
        var request = submission();
        renameUser(eno);
        error("IT_BUDGET_PREVIEW_STALE", 409, () -> facade.submit(f.actor, request));
        assertThat(
                        registry.get("approval.it_budget.submission")
                                .tag("outcome", "stale")
                                .counter()
                                .count())
                .isEqualTo(1);
        verifyNoInteractions(applications);
    }

    @Test
    void changedCurrentDocumentOrderIsStale() {
        var request = submission();
        var built = f.builder.buildDocuments(previewRequest().documents(), f.loader.load(refs()));
        doReturn(built.reversed()).when(f.builder).buildDocuments(anyList(), anyList());
        error("IT_BUDGET_PREVIEW_STALE", 409, () -> facade.submit(f.actor, request));
        verifyNoInteractions(applications);
    }

    @Test
    void codeLabelChangeWithoutLedgerChangeIsStale() {
        var request = submission();
        when(f.codes.findAllByCIdIn(anyCollection()))
                .thenReturn(
                        List.of(
                                com.kdb.it.common.code.entity.Ccodem.builder()
                                        .cId(com.kdb.it.common.code.CommonCodeGroups.EDRT)
                                        .cdva("E1")
                                        .cdvaNm("변경 표시명")
                                        .delYn("N")
                                        .build()));
        error("IT_BUDGET_PREVIEW_STALE", 409, () -> facade.submit(f.actor, request));
        verifyNoInteractions(applications);
    }

    @Test
    void missingParentsKeepSignedPreviewNamesAndUnknownAuditWithoutInventedTime() {
        var request = submission();
        when(f.projects.findVersionsForUpdate(anyCollection(), anyCollection()))
                .thenReturn(List.of());
        doReturn(List.of()).when(f.costs).findVersionsForUpdate(anyCollection(), anyCollection());
        assertThatThrownBy(() -> facade.submit(f.actor, request))
                .isInstanceOfSatisfying(
                        ItBudgetApprovalException.class,
                        e -> {
                            assertThat(e.code()).isEqualTo("IT_BUDGET_SOURCE_CHANGED");
                            assertThat(e.changedSources())
                                    .containsExactly(
                                            new ChangedSource(
                                                    SourceKind.COST,
                                                    "C1",
                                                    2,
                                                    " 계약 ",
                                                    "확인 불가",
                                                    null),
                                            new ChangedSource(
                                                    SourceKind.PROJECT,
                                                    "P1",
                                                    1,
                                                    "사업 하나",
                                                    "확인 불가",
                                                    null));
                        });
        verifyNoInteractions(applications);
    }

    @Test
    void sourceChangesWinOverApprovalStateAndAllChangedRowsFollowDocumentOrder() {
        var request = submission();
        project.delete();
        f.costs.findVersions(List.of("C1"), List.of(2)).getFirst().delete();
        when(mappings.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                        anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(true);
        assertThatThrownBy(() -> facade.submit(f.actor, request))
                .isInstanceOfSatisfying(
                        ItBudgetApprovalException.class,
                        e -> {
                            assertThat(e.code()).isEqualTo("IT_BUDGET_SOURCE_CHANGED");
                            assertThat(e.changedSources())
                                    .extracting(ChangedSource::id)
                                    .containsExactly("C1", "P1");
                        });
        verifyNoInteractions(applications);
    }

    @Test
    void approvalGuardRunsUnderLocksAndBlocksPersistence() {
        var request = submission();
        when(mappings.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                        eq("BPROJM"), eq("P1"), eq(1), anyList()))
                .thenReturn(true);
        error("IT_BUDGET_PREVIEW_STALE", 409, () -> facade.submit(f.actor, request));
        verifyNoInteractions(applications);
    }

    @Test
    void wrapsLockTimeoutCauseButDoesNotMisclassifyOtherDatabaseFailures() {
        var request = submission();
        when(f.projects.findVersionsForUpdate(anyCollection(), anyCollection()))
                .thenThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "lock", new jakarta.persistence.LockTimeoutException()));
        error("IT_BUDGET_CONCURRENT_UPDATE", 409, () -> facade.submit(f.actor, request));
        assertThat(
                        registry.get("approval.it_budget.submission")
                                .tag("outcome", "concurrent_update")
                                .counter()
                                .count())
                .isEqualTo(1);
        var unrelated = new org.springframework.dao.DataIntegrityViolationException("bad column");
        doThrow(unrelated).when(f.projects).findVersionsForUpdate(anyCollection(), anyCollection());
        assertThatThrownBy(() -> facade.submit(f.actor, request)).isSameAs(unrelated);
        verifyNoInteractions(applications);
    }

    @Test
    void secondDocumentPersistenceFailureEscapesOuterBoundary() {
        var request = submission();
        var failure =
                new org.springframework.dao.DataIntegrityViolationException("second document");
        when(applications.save(any())).thenAnswer(i -> i.getArgument(0)).thenThrow(failure);
        assertThatThrownBy(() -> facade.submit(f.actor, request)).isSameAs(failure);
        verify(applications, times(2)).save(any());
    }

    @Test
    void combinedDocumentPersistsEverySourceInNormalizedOrder() {
        var input =
                new PreviewRequest(
                        previewRequest().approvers(),
                        List.of(
                                new DocumentRequest(
                                        "combined",
                                        List.of(
                                                ItBudgetApprovalFacadeTest.projectRef(),
                                                ItBudgetApprovalFacadeTest.costRef()))));
        var preview = facade.preview(f.actor, input);
        var document = preview.documents().getFirst();
        // 응답 원장 배열 순서가 바뀌어도 명시 order가 같은 요청은 같은 정규형이다.
        var request =
                new SubmissionRequest(
                        preview.previewDigest(),
                        preview.previewToken(),
                        input.approvers(),
                        List.of(
                                new SubmissionDocument(
                                        document.clientDocumentKey(),
                                        document.payloadDigest(),
                                        document.sources().reversed())));
        assertThat(facade.submit(f.actor, request).applicationNumbers()).hasSize(1);
        assertThat(registry.get("approval.it_budget.submission.documents").summary().totalAmount())
                .isEqualTo(1);
        assertThat(registry.get("approval.it_budget.submission.sources").summary().totalAmount())
                .isEqualTo(2);
        var links = ArgumentCaptor.forClass(Cappla.class);
        verify(mappings, times(2)).save(links.capture());
        assertThat(links.getAllValues())
                .extracting(Cappla::getFntTbNm)
                .containsExactly("BCOSTM", "BPROJM");
        assertThat(links.getAllValues())
                .extracting(Cappla::getApfDcmNo)
                .containsOnly("APF-" + LocalDate.now().getYear() + "-00000101");
    }

    @Test
    void newlyInvalidLedgerPrecisionIsStillAChangedSource() {
        var request = submission();
        when(f.items.findSourceVersions(anyCollection(), anyCollection()))
                .thenReturn(
                        List.of(
                                Bitemm.builder()
                                        .gclMngNo("I1")
                                        .sno(3)
                                        .abusMngNo("P1")
                                        .fntTbCrySno(1)
                                        .qty(new java.math.BigDecimal("1.5"))
                                        .amt(new java.math.BigDecimal("7.125"))
                                        .delYn("N")
                                        .build()));
        error("IT_BUDGET_SOURCE_CHANGED", 409, () -> facade.submit(f.actor, request));
        verifyNoInteractions(applications);
    }

    PreviewRequest previewRequest() {
        return new PreviewRequest(
                ItBudgetApprovalFacadeTest.request().approvers(),
                List.of(
                        new DocumentRequest(
                                "cost-first", List.of(ItBudgetApprovalFacadeTest.costRef())),
                        new DocumentRequest(
                                "project-second",
                                List.of(ItBudgetApprovalFacadeTest.projectRef()))));
    }

    List<SourceRef> refs() {
        return previewRequest().documents().stream().flatMap(d -> d.sourceRefs().stream()).toList();
    }

    SubmissionRequest submission() {
        var preview = facade.preview(f.actor, previewRequest());
        return new SubmissionRequest(
                preview.previewDigest(),
                preview.previewToken(),
                previewRequest().approvers(),
                preview.documents().stream()
                        .map(
                                d ->
                                        new SubmissionDocument(
                                                d.clientDocumentKey(),
                                                d.payloadDigest(),
                                                d.sources()))
                        .toList());
    }

    private void assertBusinessResultsSurviveMetricFailure(MeterRegistry failingRegistry) {
        var measured = facadeWith(failingRegistry);
        var successfulRequest = submission();
        var typedFailureRequest = submission();
        var unexpectedFailureRequest = submission();

        assertThat(measured.preview(f.actor, previewRequest()).documents()).isNotEmpty();
        error("IT_BUDGET_PREVIEW_INVALID", 400, () -> measured.preview(f.actor, null));

        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        assertThat(measured.submit(f.actor, successfulRequest).applicationNumbers()).isNotEmpty();

        project.delete();
        error("IT_BUDGET_SOURCE_CHANGED", 409, () -> measured.submit(f.actor, typedFailureRequest));

        var original = new IllegalStateException("original business failure");
        doThrow(original).when(f.projects).findVersionsForUpdate(anyCollection(), anyCollection());
        assertThatThrownBy(() -> measured.submit(f.actor, unexpectedFailureRequest))
                .isSameAs(original);
    }

    private ItBudgetApprovalFacade facadeWith(MeterRegistry metricRegistry) {
        return new ItBudgetApprovalFacade(
                f.loader,
                f.builder,
                f.canonical,
                f.tokens,
                f.users,
                f.mapper,
                persistence,
                guard,
                metricRegistry);
    }

    void renameUser(String eno) {
        when(f.users.findByEnoIn(anyCollection()))
                .thenAnswer(
                        i -> {
                            Collection<String> requested = i.getArgument(0);
                            return requested.stream()
                                    .map(
                                            e ->
                                                    ItBudgetApprovalFacadeTest.person(
                                                            e,
                                                            e.equals(eno) ? "변경된 이름" : "이름 " + e))
                                    .toList();
                        });
    }

    SubmissionRequest mutate(SubmissionRequest r, String kind) {
        var docs = new ArrayList<>(r.documents());
        var people = new ArrayList<>(r.approvers());
        var first = docs.getFirst();
        if (kind.equals("documentOrder")) Collections.reverse(docs);
        if (kind.equals("duplicate")) docs.add(first);
        if (kind.equals("key"))
            docs.set(0, new SubmissionDocument("altered", first.payloadDigest(), first.sources()));
        if (kind.equals("payload"))
            docs.set(
                    0,
                    new SubmissionDocument(
                            first.clientDocumentKey(), "f".repeat(64), first.sources()));
        if (kind.equals("source") || kind.equals("sourceOrder") || kind.equals("name")) {
            var s = first.sources().getFirst();
            docs.set(
                    0,
                    new SubmissionDocument(
                            first.clientDocumentKey(),
                            first.payloadDigest(),
                            List.of(
                                    new SourceDigest(
                                            s.kind(),
                                            s.id(),
                                            s.revision(),
                                            kind.equals("sourceOrder") ? 99 : s.order(),
                                            kind.equals("source")
                                                    ? "f".repeat(64)
                                                    : s.sourceDigest(),
                                            kind.equals("name") ? "변조 명칭" : s.displayName()))));
        }
        if (kind.equals("role")) people.set(1, new ApproverRef(ApproverRole.ADDITIONAL, "A2"));
        if (kind.equals("approver")) people.set(0, new ApproverRef(ApproverRole.TEAM_LEAD, "A3"));
        return new SubmissionRequest(
                kind.equals("preview") ? "f".repeat(64) : r.previewDigest(),
                kind.equals("token") ? r.previewToken() + "x" : r.previewToken(),
                people,
                docs);
    }

    static void error(
            String code, int status, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        ItBudgetApprovalException.class,
                        e -> {
                            assertThat(e.code()).isEqualTo(code);
                            assertThat(e.status().value()).isEqualTo(status);
                        });
    }
}
