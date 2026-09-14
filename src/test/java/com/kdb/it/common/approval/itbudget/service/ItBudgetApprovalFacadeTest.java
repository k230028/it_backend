package com.kdb.it.common.approval.itbudget.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kdb.it.common.approval.itbudget.config.ItBudgetPreviewProperties;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.*;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.domain.budget.cost.entity.*;
import com.kdb.it.domain.budget.cost.repository.*;
import com.kdb.it.domain.budget.project.entity.*;
import com.kdb.it.domain.budget.project.repository.*;
import com.kdb.it.domain.budget.project.service.ProjectAmountCalculator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

class ItBudgetApprovalFacadeTest {
    final CustomUserDetails actor =
            new CustomUserDetails("U1", List.of(CustomUserDetails.ATH_USER), "D1");
    final Clock clock = Clock.fixed(Instant.parse("2026-09-06T05:30:00Z"), ZoneId.of("Asia/Seoul"));
    final com.fasterxml.jackson.databind.ObjectMapper mapper = new JacksonConfig().objectMapper();
    final ItBudgetCanonicalJson canonical = new ItBudgetCanonicalJson(mapper);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectItemRepository items = mock(ProjectItemRepository.class);
    final CostRepository costs = mock(CostRepository.class);
    final BtermmRepository terminals = mock(BtermmRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final OrganizationRepository organizations = mock(OrganizationRepository.class);
    final CodeRepository codes = mock(CodeRepository.class);
    final ItBudgetSourceLoader loader =
            spy(new ItBudgetSourceLoader(projects, items, costs, terminals));
    final ItBudgetSnapshotBuilder builder =
            spy(
                    new ItBudgetSnapshotBuilder(
                            canonical,
                            new ItBudgetLedgerCapture(canonical),
                            new ProjectAmountCalculator(),
                            users,
                            organizations,
                            codes));
    final ItBudgetPreviewTokenService tokens =
            spy(
                    new ItBudgetPreviewTokenService(
                            new ItBudgetPreviewProperties(
                                    "test",
                                    "test-only-preview-key-0123456789012345",
                                    null,
                                    null,
                                    Duration.ofMinutes(30)),
                            mapper,
                            clock));
    final ItBudgetApprovalFacade facade =
            new ItBudgetApprovalFacade(
                    loader,
                    builder,
                    canonical,
                    tokens,
                    users,
                    mapper,
                    null,
                    null,
                    new SimpleMeterRegistry());

    ItBudgetApprovalFacadeTest() {
        when(projects.findVersions(anyCollection(), anyCollection()))
                .thenReturn(List.of(project("P1", "D1", "U1")));
        when(costs.findVersions(anyCollection(), anyCollection()))
                .thenReturn(
                        List.of(
                                Bcostm.builder()
                                        .costBgNo("C1")
                                        .bgSno(2)
                                        .bseYy("2027")
                                        .cttNm(" 계약 ")
                                        .costSvnDpmC("D1")
                                        .fstEnrUsid("U1")
                                        .cgprId("U1")
                                        .costTotXpAmt(new BigDecimal("3.125"))
                                        .xcr(new BigDecimal("1.2500"))
                                        .delYn("N")
                                        .build()));
        when(users.findByEnoIn(anyCollection()))
                .thenAnswer(
                        invocation -> {
                            Collection<String> requested = invocation.getArgument(0);
                            return requested.stream().map(e -> person(e, "이름 " + e)).toList();
                        });
        when(terminals.findSourceVersions(anyCollection(), anyCollection()))
                .thenReturn(
                        List.of(
                                Btermm.builder()
                                        .tmnMngNo("T1")
                                        .sno(4)
                                        .termBgNo("C1")
                                        .termBgSno(2)
                                        .xcr(new BigDecimal("1.2500"))
                                        .fcAmt(new BigDecimal("2.500"))
                                        .termRqmBgAmt(new BigDecimal("3.125"))
                                        .delYn("N")
                                        .build()));
        when(items.findSourceVersions(anyCollection(), anyCollection()))
                .thenReturn(
                        List.of(
                                Bitemm.builder()
                                        .gclMngNo("I1")
                                        .sno(3)
                                        .abusMngNo("P1")
                                        .fntTbCrySno(1)
                                        .qty(new BigDecimal("2"))
                                        .amt(new BigDecimal("7.125"))
                                        .fcAmt(new BigDecimal("1000.000"))
                                        .xcr(new BigDecimal("1.0000"))
                                        .curC("USD")
                                        .delYn("N")
                                        .build()));
    }

    @Test
    void previewKeepsExplicitRolesInEnrichedApprovalLine() {
        var snapshot =
                mapper.valueToTree(
                        facade.preview(actor, request()).documents().getFirst().snapshot());
        assertThat(snapshot.at("/approvalLine/approvers/0/role").asText()).isEqualTo("TEAM_LEAD");
        assertThat(snapshot.at("/approvalLine/approvers/1/role").asText()).isEqualTo("DEPT_HEAD");
    }

    @Test
    void previewWithoutApproversKeepsAnEmptyApprovalLine() {
        var response = facade.preview(actor, new PreviewRequest(List.of(), request().documents()));

        assertThat(response.documents().getFirst().snapshot().approvalLine().approvers()).isEmpty();
    }

    @Test
    void previewAllowsTheSameEmployeeInBothApprovalSteps() {
        var input =
                new PreviewRequest(
                        List.of(
                                new ApproverRef(RequestApproverRole.TEAM_LEAD, "A1"),
                                new ApproverRef(RequestApproverRole.DEPT_HEAD, "A1")),
                        request().documents());

        var approvers =
                facade.preview(actor, input)
                        .documents()
                        .getFirst()
                        .snapshot()
                        .approvalLine()
                        .approvers();

        assertThat(approvers).extracting(person -> person.eno()).containsExactly("A1", "A1");
    }

    @Test
    void previewRejectsThirdApproverBeforeReadingLedgers() {
        var input =
                new PreviewRequest(
                        List.of(
                                new ApproverRef(RequestApproverRole.TEAM_LEAD, "A1"),
                                new ApproverRef(RequestApproverRole.DEPT_HEAD, "A2"),
                                new ApproverRef(RequestApproverRole.TEAM_LEAD, "A1")),
                        request().documents());

        assertThatThrownBy(() -> facade.preview(actor, input))
                .isInstanceOfSatisfying(
                        ItBudgetApprovalException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo("IT_BUDGET_PREVIEW_INVALID"));
        verifyNoInteractions(loader, builder, tokens);
    }

    @Test
    void digestViewsBindOrderedBoundariesAndIgnoreCaptureTime() {
        var response = facade.preview(actor, request());
        var claims = tokens.verify(response.previewToken(), "U1");
        var normalized =
                new PreviewRequest(
                        request().approvers(),
                        List.of(
                                new DocumentRequest(
                                        " combined ", List.of(costRef(), projectRef()))));
        assertThat(claims.requestDigest()).isEqualTo(canonical.digest(normalized));
        var sources = response.documents().stream().map(PreviewDocument::sources).toList();
        assertThat(claims.sourceSetDigest()).isEqualTo(canonical.digest(sources));
        assertThat(claims.payloadSetDigest())
                .isEqualTo(
                        canonical.digest(
                                response.documents().stream()
                                        .map(PreviewDocument::payloadDigest)
                                        .toList()));
        assertThat(facade.preview(actor, normalized).previewDigest())
                .isEqualTo(response.previewDigest());
        var futureClock = Clock.offset(clock, Duration.ofDays(1));
        var futureTokens =
                new ItBudgetPreviewTokenService(
                        new ItBudgetPreviewProperties(
                                "test",
                                "test-only-preview-key-0123456789012345",
                                null,
                                null,
                                Duration.ofMinutes(30)),
                        mapper,
                        futureClock);
        var future =
                new ItBudgetApprovalFacade(
                                loader,
                                builder,
                                canonical,
                                futureTokens,
                                users,
                                mapper,
                                null,
                                null,
                                new SimpleMeterRegistry())
                        .preview(actor, normalized);
        assertThat(future.previewDigest()).isEqualTo(response.previewDigest());
        assertThat(future.documents().getFirst().snapshot().integrity().capturedAt())
                .isEqualTo(Instant.parse("2026-09-07T05:30:00Z"));
        assertThat(future.previewToken()).isNotEqualTo(response.previewToken());
        var split =
                new PreviewRequest(
                        normalized.approvers(),
                        List.of(
                                new DocumentRequest("cost", List.of(costRef())),
                                new DocumentRequest("project", List.of(projectRef()))));
        var splitResponse = facade.preview(actor, split);
        var splitClaims = tokens.verify(splitResponse.previewToken(), "U1");
        assertThat(splitClaims.requestDigest()).isNotEqualTo(claims.requestDigest());
        assertThat(splitClaims.sourceSetDigest()).isNotEqualTo(claims.sourceSetDigest());
        assertThat(splitClaims.payloadSetDigest()).isNotEqualTo(claims.payloadSetDigest());
        assertThat(splitClaims.previewDigest()).isNotEqualTo(claims.previewDigest());
    }

    @Test
    void approverDisplayOnlyChangesPreviewDigest() {
        var before = facade.preview(actor, request());
        var first = tokens.verify(before.previewToken(), "U1");
        when(users.findByEnoIn(anyCollection()))
                .thenAnswer(
                        invocation -> {
                            Collection<String> requested = invocation.getArgument(0);
                            return requested.stream()
                                    .map(e -> person(e, e.equals("A1") ? "변경된 결재자" : "이름 " + e))
                                    .toList();
                        });
        var rename = facade.preview(actor, request());
        var renamedClaims = tokens.verify(rename.previewToken(), "U1");
        assertThat(renamedClaims.previewDigest()).isNotEqualTo(first.previewDigest());
        assertThat(renamedClaims.requestDigest()).isEqualTo(first.requestDigest());
        assertThat(renamedClaims.sourceSetDigest()).isEqualTo(first.sourceSetDigest());
        assertThat(renamedClaims.payloadSetDigest()).isEqualTo(first.payloadSetDigest());
    }

    @Test
    void acceptsMaximumDocumentsAndSourcesWithFixedApproversAndBoundedQueries() {
        var selected = IntStream.range(0, 500).mapToObj(i -> project("P" + i, "D1", "U1")).toList();
        when(projects.findVersions(anyCollection(), anyCollection())).thenReturn(selected);
        var documents =
                IntStream.range(0, 100)
                        .mapToObj(
                                d ->
                                        new DocumentRequest(
                                                "doc" + d,
                                                IntStream.range(0, 5)
                                                        .mapToObj(
                                                                i ->
                                                                        new SourceRef(
                                                                                SourceKind.PROJECT,
                                                                                "P" + (d * 5 + i),
                                                                                1,
                                                                                i + 1))
                                                        .toList()))
                        .toList();
        var approvers = new ArrayList<ApproverRef>();
        approvers.add(new ApproverRef(RequestApproverRole.TEAM_LEAD, "A1"));
        approvers.add(new ApproverRef(RequestApproverRole.DEPT_HEAD, "A2"));
        var result = facade.preview(actor, new PreviewRequest(approvers, documents));
        assertThat(result.documents()).hasSize(100);
        assertThat(result.documents().getFirst().snapshot().approvalLine().approvers()).hasSize(2);
        verify(projects).findVersions(anyCollection(), anyCollection());
        verify(items).findSourceVersions(anyCollection(), anyCollection());
        verify(codes).findAllByCIdIn(anyCollection());
        verifyNoInteractions(costs, terminals);
    }

    @Test
    void missingDepartmentFailsAndAdminUsesExistingPolicy() {
        var withoutDepartment =
                new CustomUserDetails("U1", List.of(CustomUserDetails.ATH_USER), null);
        assertThatThrownBy(() -> facade.preview(withoutDepartment, request()))
                .isInstanceOf(AccessDeniedException.class);
        var admin = new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), null);
        assertThat(facade.preview(admin, request()).documents()).hasSize(1);
    }

    @Test
    void pendingApproversHaveNoInventedApprovalDate() {
        var result = facade.preview(actor, request());
        assertThat(result.documents().getFirst().snapshot().approvalLine().approvers())
                .allSatisfy(p -> assertThat(p.date()).isNull());
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(
                            factory.getValidator()
                                    .validate(
                                            new ApprovalPerson(
                                                    ApproverRole.TEAM_LEAD,
                                                    "A1",
                                                    "성명",
                                                    "직급",
                                                    null)))
                    .isEmpty();
        }
    }

    @Test
    void createsServerSnapshotWithFixedStringsAndTokenOwnedCaptureTime() {
        var result = facade.preview(actor, request());
        var claims = tokens.verify(result.previewToken(), "U1");
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-09-06T06:00:00Z"));
        assertThat(claims.previewDigest()).isEqualTo(result.previewDigest());
        var snapshot = result.documents().getFirst().snapshot();
        assertThat(snapshot.form().id()).isEqualTo("it-budget");
        assertThat(snapshot.form().version()).isEqualTo(3);
        assertThat(snapshot.integrity().capturedAt()).isEqualTo(claims.issuedAt());
        assertThat(snapshot.integrity().algorithm()).isEqualTo("SHA-256");
        assertThat(snapshot.integrity().canonicalization()).isEqualTo("IT_BUDGET_V3");
        assertThat(snapshot.approvalLine().requester())
                .isEqualTo(
                        new com.kdb.it.common.approval.itbudget.dto.ItBudgetSnapshotV3Dto.Requester(
                                "U1", "이름 U1", "직급"));
        assertThat(snapshot.approvalLine().approvers())
                .extracting(person -> person.eno())
                .containsExactly("A1", "A2");
        assertThat(snapshot.payload().projects().getFirst().currentRequestAmount())
                .isEqualTo("7.125");
        assertThat(snapshot.payload().projects().getFirst().items().getFirst().id())
                .isEqualTo("I1");
        assertThat(snapshot.payload().projects().getFirst().items().getFirst().foreignAmount())
                .isEqualTo("1000.000");
        assertThat(snapshot.payload().ledger().aggregates()).hasSize(2);
        assertThat(snapshot.payload().ledger().aggregates())
                .extracting(aggregate -> aggregate.id())
                .containsExactly("C1", "P1");
        assertThat(snapshot.payload().ledger().aggregates().get(1).children().getFirst().columns())
                .containsEntry("FC_AMT", "1000.000")
                .containsEntry("SNO", 3);
        assertThat(snapshot.payload().costs().getFirst().baseYear()).isEqualTo("2027");
        assertThat(snapshot.payload().costs().getFirst().terminals().getFirst().id())
                .isEqualTo("T1");
        assertThat(snapshot.payload().costs().getFirst().exchangeRate()).isEqualTo("1.2500");
        assertThat(snapshot.payload().summary().total()).isEqualTo("10.250");
        assertThat(snapshot.payload().costs().getFirst().name()).isEqualTo(" 계약 ");
        assertThat(result.documents().getFirst().sources())
                .extracting(SourceDigest::id)
                .containsExactly("C1", "P1");
        assertThat(snapshot.integrity().payloadDigest())
                .isEqualTo(result.documents().getFirst().payloadDigest());
        assertThat(canonical.digest(snapshot.payload()))
                .isNotEqualTo(snapshot.integrity().payloadDigest());
        var typedPayload =
                mapper.convertValue(
                        snapshot.payload(),
                        com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshotV3.Payload.class);
        assertThat(canonical.digest(typedPayload)).isEqualTo(snapshot.integrity().payloadDigest());
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(result)).isEmpty();
        }
        verify(loader).load(anyList());
        verify(builder).buildDocuments(anyList(), anyList());
        verify(projects).findVersions(anyCollection(), anyCollection());
        verify(costs).findVersions(anyCollection(), anyCollection());
        verify(projects, never()).save(any());
        verify(costs, never()).save(any());
        verify(items).findSourceVersions(anyCollection(), anyCollection());
        verify(terminals).findSourceVersions(anyCollection(), anyCollection());
        verifyNoMoreInteractions(projects, costs, items, terminals);
        assertThat(ItBudgetApprovalFacade.class.getAnnotation(Transactional.class).readOnly())
                .isTrue();
    }

    @Test
    void preservesDocumentOrderAndCanonicalSourceOrderWithoutTrimming() {
        var input =
                new PreviewRequest(
                        request().approvers(),
                        List.of(
                                new DocumentRequest(" z ", List.of(costRef())),
                                new DocumentRequest("a", List.of(projectRef()))));
        assertThat(facade.preview(actor, input).documents())
                .extracting(PreviewDocument::clientDocumentKey)
                .containsExactly(" z ", "a");
        var reversed = new PreviewRequest(input.approvers(), input.documents().reversed());
        assertThat(facade.preview(actor, reversed).previewDigest())
                .isNotEqualTo(facade.preview(actor, input).previewDigest());
    }

    @Test
    void unauthorizedProjectFailsBeforeAnyPayloadDisplayLookupOrToken() {
        when(projects.findVersions(anyCollection(), anyCollection()))
                .thenReturn(List.of(project("P1", "OTHER", "U1")));
        assertThatThrownBy(() -> facade.preview(actor, request()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users, codes, organizations, tokens);
        verify(builder, never()).buildDocuments(anyList(), anyList());
    }

    @Test
    void sameDepartmentNonOwnerCostCanBeSubmitted() {
        when(costs.findVersions(anyCollection(), anyCollection()))
                .thenReturn(
                        List.of(
                                Bcostm.builder()
                                        .costBgNo("C1")
                                        .bgSno(2)
                                        .costSvnDpmC("D1")
                                        .fstEnrUsid("other")
                                        .cgprId("U1")
                                        .delYn("N")
                                        .build()));

        assertThat(facade.preview(actor, request()).documents()).hasSize(1);
    }

    @Test
    void absentOrDeletedExactVersionIs404AndInvalidReferenceIs400() {
        when(projects.findVersions(anyCollection(), anyCollection()))
                .thenReturn(List.of(Bprojm.builder().abusMngNo("P1").sno(2).delYn("N").build()));
        assertError(404, () -> facade.preview(actor, request()));
        var deleted = project("P1", "D1", "U1");
        deleted.delete();
        when(projects.findVersions(anyCollection(), anyCollection())).thenReturn(List.of(deleted));
        assertError(404, () -> facade.preview(actor, request()));
        assertError(
                400,
                () ->
                        facade.preview(
                                actor,
                                new PreviewRequest(
                                        request().approvers(),
                                        List.of(
                                                new DocumentRequest(
                                                        "bad",
                                                        List.of(
                                                                new SourceRef(
                                                                        SourceKind.PROJECT,
                                                                        "P1",
                                                                        0,
                                                                        1)))))));
        verifyNoInteractions(tokens);
    }

    @Test
    void malformedAndDuplicateInputsFailBeforeReadingLedgers() {
        var approvers = request().approvers();
        var p = projectRef();
        var bad = new ArrayList<PreviewRequest>();
        bad.add(new PreviewRequest(approvers, List.of()));
        bad.add(new PreviewRequest(approvers, List.of(new DocumentRequest("x", List.of(p, p)))));
        bad.add(
                new PreviewRequest(
                        approvers,
                        List.of(
                                new DocumentRequest("x", List.of(p)),
                                new DocumentRequest("y", List.of(p)))));
        bad.add(
                new PreviewRequest(
                        approvers,
                        List.of(
                                new DocumentRequest(
                                        "x",
                                        List.of(p, new SourceRef(SourceKind.COST, "C1", 2, 2))))));
        bad.add(
                new PreviewRequest(
                        approvers,
                        List.of(
                                new DocumentRequest("same", List.of(p)),
                                new DocumentRequest("same", List.of(costRef())))));
        bad.add(new PreviewRequest(approvers.reversed(), request().documents()));
        bad.add(
                new PreviewRequest(
                        List.of(
                                new ApproverRef(RequestApproverRole.TEAM_LEAD, "A1"),
                                new ApproverRef(RequestApproverRole.TEAM_LEAD, "A2")),
                        request().documents()));
        bad.add(
                new PreviewRequest(
                        Collections.nCopies(
                                3, new ApproverRef(RequestApproverRole.TEAM_LEAD, "A1")),
                        request().documents()));
        bad.add(
                new PreviewRequest(
                        approvers,
                        IntStream.range(0, 101)
                                .mapToObj(
                                        i ->
                                                new DocumentRequest(
                                                        "d" + i,
                                                        List.of(
                                                                new SourceRef(
                                                                        SourceKind.PROJECT,
                                                                        "P" + i,
                                                                        1,
                                                                        1))))
                                .toList()));
        bad.add(
                new PreviewRequest(
                        approvers,
                        List.of(
                                new DocumentRequest(
                                        "a",
                                        IntStream.range(0, 500)
                                                .mapToObj(
                                                        i ->
                                                                new SourceRef(
                                                                        SourceKind.PROJECT,
                                                                        "P" + i,
                                                                        1,
                                                                        i + 1))
                                                .toList()),
                                new DocumentRequest(
                                        "b",
                                        List.of(new SourceRef(SourceKind.COST, "C1", 1, 1))))));
        for (var value : bad) assertError(400, () -> facade.preview(actor, value));
        assertError(400, () -> facade.preview(actor, null));
        verifyNoInteractions(loader, builder, users, tokens);
    }

    @Test
    void missingRequesterOrApproverDisplayDataFailsWithoutSigning() {
        when(users.findByEnoIn(anyCollection())).thenReturn(List.of(person("U1", "이름")));
        assertError(400, () -> facade.preview(actor, request()));
        when(users.findByEnoIn(anyCollection()))
                .thenReturn(List.of(person("A1", "이름"), person("A2", "이름")));
        assertError(400, () -> facade.preview(actor, request()));
        verifyNoInteractions(tokens);
    }

    @Test
    void requesterRankMayBeNullButApproverIdentityAndRankAreRequired() {
        when(users.findByEnoIn(anyCollection()))
                .thenReturn(
                        List.of(
                                CuserI.builder().eno("U1").usrNm("신청자").delYn("N").build(),
                                person("A1", "일차"),
                                person("A2", "이차")));
        assertThat(
                        facade.preview(actor, request())
                                .documents()
                                .getFirst()
                                .snapshot()
                                .approvalLine()
                                .requester()
                                .rank())
                .isNull();
        clearInvocations(tokens);
        when(users.findByEnoIn(anyCollection()))
                .thenReturn(
                        List.of(
                                person("U1", "신청자"),
                                CuserI.builder().eno("A1").usrNm("일차").delYn("N").build(),
                                person("A2", "이차")));
        assertError(400, () -> facade.preview(actor, request()));
        verifyNoInteractions(tokens);
    }

    @Test
    void missingPrincipalFailsBeforeReadingAnything() {
        assertThatThrownBy(() -> facade.preview(null, request()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(loader, builder, users, tokens);
    }

    static PreviewRequest request() {
        return new PreviewRequest(
                List.of(
                        new ApproverRef(RequestApproverRole.TEAM_LEAD, "A1"),
                        new ApproverRef(RequestApproverRole.DEPT_HEAD, "A2")),
                List.of(new DocumentRequest(" combined ", List.of(projectRef(), costRef()))));
    }

    static SourceRef projectRef() {
        return new SourceRef(SourceKind.PROJECT, "P1", 1, 2);
    }

    static SourceRef costRef() {
        return new SourceRef(SourceKind.COST, "C1", 2, 1);
    }

    static CuserI person(String eno, String name) {
        return CuserI.builder().eno(eno).usrNm(name).ptCNm("직급").delYn("N").build();
    }

    static Bprojm project(String id, String department, String owner) {
        return Bprojm.builder()
                .abusMngNo(id)
                .sno(1)
                .svnDpmC(department)
                .fstEnrUsid(owner)
                .usid("U1")
                .delYn("N")
                .build();
    }

    static void assertError(
            int status, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ItBudgetApprovalException.class,
                        e -> assertThat(e.status().value()).isEqualTo(status));
    }
}
