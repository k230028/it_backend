package com.kdb.it.common.approval.itbudget.service;

import static com.kdb.it.common.approval.itbudget.service.ItBudgetSourceLoaderTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.approval.itbudget.model.ItBudgetLedgerSnapshot;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.*;
import com.kdb.it.domain.budget.cost.entity.*;
import com.kdb.it.domain.budget.project.entity.*;
import com.kdb.it.domain.budget.project.service.ProjectAmountCalculator;
import com.kdb.it.infra.file.entity.Cfilem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItBudgetSnapshotBuilderTest {
    final ItBudgetCanonicalJson canonical =
            new ItBudgetCanonicalJson(new ObjectMapper().registerModule(new JavaTimeModule()));
    final UserRepository users = mock(UserRepository.class);
    final OrganizationRepository organizations = mock(OrganizationRepository.class);
    final CodeRepository codes = mock(CodeRepository.class);
    final ItBudgetSnapshotBuilder builder =
            new ItBudgetSnapshotBuilder(
                    canonical,
                    new ItBudgetLedgerCapture(canonical),
                    new ProjectAmountCalculator(),
                    users,
                    organizations,
                    codes);

    ItBudgetSnapshotBuilderTest() {
        when(users.findByEnoIn(anyCollection()))
                .thenReturn(
                        List.of(
                                CuserI.builder()
                                        .eno("U1")
                                        .usrNm("사용자")
                                        .ptCNm("직위")
                                        .delYn("N")
                                        .build()));
        when(codes.findAllByCIdIn(anyCollection()))
                .thenReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("IOE_C")
                                        .cdva("A")
                                        .cdvaNm("자산")
                                        .cTp("IOE_HW")
                                        .delYn("N")
                                        .build()));
    }

    @Test
    void buildsExactMoneyAndExcludesDeletedChildrenFromDisplayButNotDigest() {
        var p =
                Bprojm.builder()
                        .abusMngNo("P1")
                        .sno(1)
                        .abusNm(" 사업 ")
                        .usid("U1")
                        .delYn("N")
                        .build();
        var i =
                Bitemm.builder()
                        .gclMngNo("I")
                        .sno(3)
                        .abusMngNo("P1")
                        .fntTbCrySno(1)
                        .ioeC("A")
                        .qty(new BigDecimal("2"))
                        .amt(new BigDecimal("12.340"))
                        .fcAmt(new BigDecimal("1000.000"))
                        .xcr(new BigDecimal("1.0000"))
                        .curC("USD")
                        .delYn("N")
                        .build();
        var deleted = item("D", 1, "P1", 1, "Y");
        var result = build(p, List.of(i, deleted));
        assertThat(result.payload().projects().getFirst().name()).isEqualTo(" 사업 ");
        assertThat(result.payload().projects().getFirst().items()).hasSize(1);
        assertThat(result.payload().summary().total()).isEqualTo(new BigDecimal("12.340"));
        assertThat(result.payload().summary().asset()).isEqualTo(new BigDecimal("12.340"));
        assertThat(result.payload().projects().getFirst().items().getFirst().foreignAmount())
                .isEqualByComparingTo("1000.000");
        assertThat(result.payload().ledger().format()).isEqualTo("IT_BUDGET_LEDGER_V1");
        assertThat(result.payload().ledger().aggregates()).hasSize(1);
        assertThat(result.payloadDigest()).isEqualTo(canonical.digest(result.payload()));
        assertThat(result.sources().getFirst().digest())
                .isNotEqualTo(build(p, List.of(i)).sources().getFirst().digest());
    }

    @Test
    void auditAndFetchedNamesStayOutsideSourceDigestButChangeV3PayloadDigest() {
        var p = project("P1", 1);
        var before = build(p, List.of());
        var audit =
                Bprojm.builder()
                        .abusMngNo("P1")
                        .sno(1)
                        .usid("U1")
                        .delYn("N")
                        .lstChgUsid("other")
                        .lstChgDtm(LocalDateTime.now())
                        .guid("guid")
                        .build();
        assertThat(build(audit, List.of()).sources()).isEqualTo(before.sources());
        assertThat(build(audit, List.of()).payloadDigest()).isNotEqualTo(before.payloadDigest());
        when(users.findByEnoIn(anyCollection()))
                .thenReturn(
                        List.of(
                                CuserI.builder()
                                        .eno("U1")
                                        .usrNm("바뀐 이름")
                                        .ptCNm("직위")
                                        .delYn("N")
                                        .build()));
        var renamed = build(p, List.of());
        assertThat(renamed.sources()).isEqualTo(before.sources());
        assertThat(renamed.payloadDigest()).isNotEqualTo(before.payloadDigest());
    }

    @Test
    void attachmentMetadataChangesV3PayloadDigestWithoutChangingSourceDigest() {
        var p = project("P1", 1);
        var first = build(p, List.of(), file("FL-1", "first.pdf"));
        var renamed = build(p, List.of(), file("FL-1", "renamed.pdf"));

        assertThat(renamed.sources()).isEqualTo(first.sources());
        assertThat(renamed.payloadDigest()).isNotEqualTo(first.payloadDigest());
    }

    @Test
    void rejectsMissingRequiredUserAndDeletedParent() {
        when(users.findByEnoIn(anyCollection())).thenReturn(List.of());
        invalid(() -> build(project("P1", 1), List.of()));
        var deleted = project("P1", 1);
        deleted.delete();
        invalid(() -> build(deleted, List.of()));
    }

    @Test
    void rejectsExcessMoneyRateAndQuantityPrecisionWithoutRounding() {
        for (var i :
                List.of(
                        Bitemm.builder()
                                .gclMngNo("I")
                                .sno(1)
                                .amt(new BigDecimal("1.0001"))
                                .delYn("N")
                                .build(),
                        Bitemm.builder()
                                .gclMngNo("I")
                                .sno(1)
                                .xcr(new BigDecimal("1.00001"))
                                .delYn("N")
                                .build(),
                        Bitemm.builder()
                                .gclMngNo("I")
                                .sno(1)
                                .qty(new BigDecimal("1.1"))
                                .delYn("N")
                                .build())) {
            invalid(() -> build(project("P1", 1), List.of(i)));
        }
    }

    @Test
    void optionalMissingCodeUsesEmptyLabelAndNullBusinessValuesStayNull() {
        var result = build(project("P1", 1), List.of());
        var p = result.payload().projects().getFirst();
        assertThat(p.editType().code()).isNull();
        assertThat(p.editType().label()).isEmpty();
        assertThat(p.outline()).isNull();
        assertThat(p.startDate()).isNull();
        var publicPayload =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .convertValue(
                                result.payload(),
                                com.kdb.it.common.approval.itbudget.dto.ItBudgetSnapshotV3Dto
                                        .Payload.class);
        assertThat(
                        jakarta.validation.Validation.buildDefaultValidatorFactory()
                                .getValidator()
                                .validate(publicPayload))
                .isEmpty();
    }

    @Test
    void costIncludesStoredYearTerminalIdentityAndScaledWireValues() {
        var ref = new SourceRef(SourceKind.COST, "C1", 2, 1);
        var c =
                Bcostm.builder()
                        .costBgNo("C1")
                        .bgSno(2)
                        .bseYy("2027")
                        .cttNm("계약")
                        .cgprId("U1")
                        .costTotXpAmt(new BigDecimal("3.125"))
                        .xcr(new BigDecimal("1.2500"))
                        .curC("USD")
                        .delYn("N")
                        .build();
        var t =
                Btermm.builder()
                        .tmnMngNo("T1")
                        .sno(4)
                        .termBgNo("C1")
                        .termBgSno(2)
                        .termRqmBgAmt(new BigDecimal("3.125"))
                        .fcAmt(new BigDecimal("2.500"))
                        .xcr(new BigDecimal("1.2500"))
                        .delYn("N")
                        .build();
        var built =
                builder.buildDocuments(
                                List.of(new DocumentRequest("cost", List.of(ref))),
                                List.of(ItBudgetSourceLoader.aggregate(ref, c, List.of(t))))
                        .getFirst();
        var mapper = new ObjectMapper().findAndRegisterModules();
        var wire =
                mapper.valueToTree(
                        mapper.convertValue(
                                built.payload(),
                                com.kdb.it.common.approval.itbudget.dto.ItBudgetSnapshotV3Dto
                                        .Payload.class));
        assertThat(wire.at("/costs/0/baseYear").asText()).isEqualTo("2027");
        assertThat(wire.at("/costs/0/terminals/0/id").asText()).isEqualTo("T1");
        assertThat(wire.at("/costs/0/terminals/0/revision").asInt()).isEqualTo(2);
        assertThat(wire.at("/costs/0/terminals/0/sequence").asInt()).isEqualTo(4);
        assertThat(wire.at("/costs/0/terminals/0/budgetAmount").asText()).isEqualTo("3.125");
        assertThat(wire.at("/costs/0/exchangeRate").asText()).isEqualTo("1.2500");
    }

    @Test
    void rejectsCrossDocumentReuseAndDuplicateOrder() {
        var a = ItBudgetSourceLoader.aggregate(ref("P1", 1, 1), project("P1", 1), List.of());
        invalid(
                () ->
                        builder.buildDocuments(
                                List.of(
                                        new DocumentRequest("one", List.of(a.ref())),
                                        new DocumentRequest("two", List.of(a.ref()))),
                                List.of(a)));
        invalid(
                () ->
                        builder.buildDocuments(
                                List.of(
                                        new DocumentRequest(
                                                "one", List.of(a.ref(), ref("P2", 1, 1)))),
                                List.of(a)));
    }

    @Test
    void rejectsLedgerThatDoesNotMatchTheDisplayAggregateIdentity() {
        var ledgerCapture = mock(ItBudgetLedgerCapture.class);
        when(ledgerCapture.capture(anyList()))
                .thenReturn(
                        new ItBudgetLedgerSnapshot(
                                "IT_BUDGET_LEDGER_V1",
                                List.of(
                                        new ItBudgetLedgerSnapshot.Aggregate(
                                                "PROJECT",
                                                "OTHER",
                                                1,
                                                new ItBudgetLedgerSnapshot.Row("BPROJM", Map.of()),
                                                List.of()))));
        var mismatchedBuilder =
                new ItBudgetSnapshotBuilder(
                        canonical,
                        ledgerCapture,
                        new ProjectAmountCalculator(),
                        users,
                        organizations,
                        codes);
        var p = project("P1", 1);
        var ref = ref("P1", 1, 1);

        invalid(
                () ->
                        mismatchedBuilder.buildDocuments(
                                List.of(new DocumentRequest("one", List.of(ref))),
                                List.of(ItBudgetSourceLoader.aggregate(ref, p, List.of()))));
    }

    @Test
    void rejectsEveryInvalidDocumentIdentityBoundary() {
        var validRef = ref("P1", 1, 1);
        var tooMany = new ArrayList<DocumentRequest>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(new DocumentRequest("document-" + i, List.of(validRef)));
        }

        invalid(() -> builder.buildDocuments(null, List.of()));
        invalid(() -> builder.buildDocuments(List.of(), List.of()));
        invalid(() -> builder.buildDocuments(tooMany, List.of()));
        invalid(() -> builder.buildDocuments(Collections.singletonList(null), List.of()));
        invalid(
                () ->
                        builder.buildDocuments(
                                List.of(new DocumentRequest(null, List.of(validRef))), List.of()));
        invalid(
                () ->
                        builder.buildDocuments(
                                List.of(new DocumentRequest(" ", List.of(validRef))), List.of()));
        invalid(
                () ->
                        builder.buildDocuments(
                                List.of(new DocumentRequest("x".repeat(65), List.of(validRef))),
                                List.of()));
        invalid(
                () ->
                        builder.buildDocuments(
                                List.of(
                                        new DocumentRequest("same", List.of(validRef)),
                                        new DocumentRequest("same", List.of(validRef))),
                                List.of()));
    }

    @Test
    void rawBusinessChangesAndSoftDeleteRemainDetectableWithoutBuildingPayload() {
        var p = project("P1", 1);
        var aggregate = ItBudgetSourceLoader.aggregate(ref("P1", 1, 1), p, List.of());
        String before = builder.sourceDigest(aggregate);
        p.delete();
        assertThat(builder.sourceDigest(aggregate)).isNotEqualTo(before);
        p.restore();
        assertThat(builder.sourceDigest(aggregate)).isEqualTo(before);
    }

    @Test
    void resolvesLabelsAndUsersOnceAcrossDocumentsAndPreservesSelectedOrder() {
        var a = ItBudgetSourceLoader.aggregate(ref("P1", 1, 2), project("P1", 1), List.of());
        var b = ItBudgetSourceLoader.aggregate(ref("P2", 1, 1), project("P2", 1), List.of());
        var results =
                builder.buildDocuments(
                        List.of(new DocumentRequest("first", List.of(a.ref(), b.ref()))),
                        List.of(a, b));
        assertThat(results.getFirst().payload().projects())
                .extracting(p -> p.id())
                .containsExactly("P2", "P1");
        verify(users).findByEnoIn(List.of("U1"));
        verify(codes).findAllByCIdIn(anyCollection());
    }

    @Test
    void currentCodeLabelsChangePayloadButNeverStoredSourceDigest() {
        var i =
                Bitemm.builder()
                        .gclMngNo("I")
                        .sno(1)
                        .abusMngNo("P1")
                        .fntTbCrySno(1)
                        .ioeC("A")
                        .amt(BigDecimal.ONE)
                        .delYn("N")
                        .build();
        var before = build(project("P1", 1), List.of(i));
        when(codes.findAllByCIdIn(anyCollection()))
                .thenReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("IOE_C")
                                        .cdva("A")
                                        .cdvaNm("표시명 변경")
                                        .cTp("IOE_HW")
                                        .delYn("N")
                                        .build()));
        var after = build(project("P1", 1), List.of(i));
        assertThat(after.sources()).isEqualTo(before.sources());
        assertThat(after.payloadDigest()).isNotEqualTo(before.payloadDigest());
    }

    @Test
    void missingOptionalCodeClassificationDoesNotHideTheProjectOrFail() {
        when(codes.findAllByCIdIn(anyCollection()))
                .thenReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("IOE_C")
                                        .cdva("A")
                                        .cdvaNm("비목")
                                        .delYn("N")
                                        .build()));
        var item =
                Bitemm.builder()
                        .gclMngNo("I1")
                        .sno(1)
                        .abusMngNo("P1")
                        .fntTbCrySno(1)
                        .amt(BigDecimal.TEN)
                        .ioeC("A")
                        .delYn("N")
                        .build();
        var built = build(project("P1", 1), List.of(item));
        assertThat(built.payload().projects().getFirst().projectBudget())
                .isEqualTo(new BigDecimal("10.000"));
        assertThat(built.payload().projects().getFirst().items().getFirst().budgetType().label())
                .isEqualTo("비목");
    }

    ItBudgetSnapshotBuilder.BuiltDocument build(Bprojm p, List<Bitemm> children) {
        var r = ref(p.getAbusMngNo(), p.getSno(), 1);
        return builder.buildDocuments(
                        List.of(new DocumentRequest("one", List.of(r))),
                        List.of(ItBudgetSourceLoader.aggregate(r, p, children)))
                .getFirst();
    }

    ItBudgetSnapshotBuilder.BuiltDocument build(
            Bprojm p, List<Bitemm> children, Cfilem attachment) {
        var r = ref(p.getAbusMngNo(), p.getSno(), 1);
        return builder.buildDocuments(
                        List.of(new DocumentRequest("one", List.of(r))),
                        List.of(
                                ItBudgetSourceLoader.aggregate(
                                        r, p, children, List.of(attachment))))
                .getFirst();
    }

    private static Cfilem file(String id, String name) {
        return Cfilem.builder()
                .flMpnId(id)
                .flNm(name)
                .apgFlKdNm("정보화사업")
                .apgFlLnkCtzNm("P1")
                .delYn("N")
                .build();
    }

    @Test
    void unclassifiedRequestsRemainInTotalWithoutPlannedOrPaidAmounts() {
        var p =
                Bprojm.builder()
                        .abusMngNo("P1")
                        .sno(1)
                        .usid("U1")
                        .dfrAmt(new BigDecimal("5.000"))
                        .delYn("N")
                        .build();
        var i =
                Bitemm.builder()
                        .gclMngNo("I1")
                        .sno(1)
                        .abusMngNo("P1")
                        .fntTbCrySno(1)
                        .amt(new BigDecimal("10.125"))
                        .mplAmt(new BigDecimal("100.000"))
                        .curC("KRW")
                        .ioeC("UNKNOWN")
                        .delYn("N")
                        .build();
        var built = build(p, List.of(i));
        assertThat(built.payload().summary().total()).isEqualTo(new BigDecimal("10.125"));
        assertThat(built.payload().summary().asset()).isEqualTo(new BigDecimal("0.000"));
        assertThat(built.payload().summary().cost()).isEqualTo(new BigDecimal("0.000"));
        assertThat(built.payload().projects().getFirst().projectBudget())
                .isEqualTo(new BigDecimal("115.125"));
        var mapper = new ObjectMapper().findAndRegisterModules();
        var wire =
                mapper.valueToTree(
                        mapper.convertValue(
                                built.payload(),
                                com.kdb.it.common.approval.itbudget.dto.ItBudgetSnapshotV3Dto
                                        .Payload.class));
        assertThat(wire.at("/projects/0/currentRequestAmount").asText()).isEqualTo("10.125");
    }

    @Test
    void storedSnapshotRestoresCurrentRequestWithNullAndNegativeBoundaries() {
        var item =
                Bitemm.builder()
                        .gclMngNo("I1")
                        .sno(1)
                        .abusMngNo("P1")
                        .fntTbCrySno(1)
                        .amt(new BigDecimal("3.000"))
                        .ioeC("A")
                        .delYn("N")
                        .build();
        var cases =
                List.of(
                        new String[] {"100.000", "20.000", "10.000", "70.000"},
                        new String[] {"100.000", null, null, "100.000"},
                        new String[] {"100.000", null, "10.000", "90.000"},
                        new String[] {"100.000", "20.000", null, "80.000"},
                        new String[] {"5.000", "10.000", "1.000", "-6.000"});
        for (var values : cases) {
            var p =
                    Bprojm.builder()
                            .abusMngNo("P1")
                            .sno(1)
                            .usid("U1")
                            .totRqmAmt(decimal(values[0]))
                            .mplAmt(decimal(values[1]))
                            .dfrAmt(decimal(values[2]))
                            .delYn("N")
                            .build();
            var built = build(p, List.of(item));
            assertThat(built.payload().summary().total())
                    .as(java.util.Arrays.toString(values))
                    .isEqualTo(decimal(values[3]));
            assertThat(built.payload().summary().asset()).isEqualTo(new BigDecimal("3.000"));
            assertThat(
                            new ObjectMapper()
                                    .valueToTree(built.payload())
                                    .at("/projects/0/currentRequestAmount")
                                    .decimalValue())
                    .isEqualByComparingTo(decimal(values[3]));
        }
    }

    @Test
    void mixedAndContractOnlyTotalsCountEveryRequestExactlyOnce() {
        var p1 = project("P1", 1);
        var p2 = project("P2", 1);
        var i1 =
                Bitemm.builder()
                        .gclMngNo("I1")
                        .sno(1)
                        .abusMngNo("P1")
                        .fntTbCrySno(1)
                        .amt(new BigDecimal("5.000"))
                        .ioeC("A")
                        .delYn("N")
                        .build();
        var i2 =
                Bitemm.builder()
                        .gclMngNo("I2")
                        .sno(1)
                        .abusMngNo("P2")
                        .fntTbCrySno(1)
                        .amt(new BigDecimal("2.000"))
                        .delYn("N")
                        .build();
        var c =
                Bcostm.builder()
                        .costBgNo("C1")
                        .bgSno(1)
                        .cgprId("U1")
                        .costTotXpAmt(new BigDecimal("7.000"))
                        .delYn("N")
                        .build();
        var a1 = ItBudgetSourceLoader.aggregate(ref("P1", 1, 1), p1, List.of(i1));
        var a2 = ItBudgetSourceLoader.aggregate(ref("P2", 1, 2), p2, List.of(i2));
        var ac =
                ItBudgetSourceLoader.aggregate(
                        new SourceRef(SourceKind.COST, "C1", 1, 3), c, List.of());
        var mixed =
                builder.buildDocuments(
                                List.of(
                                        new DocumentRequest(
                                                "mixed", List.of(a1.ref(), a2.ref(), ac.ref()))),
                                List.of(a1, a2, ac))
                        .getFirst();
        assertThat(mixed.payload().summary().total()).isEqualTo(new BigDecimal("14.000"));
        assertThat(mixed.payload().summary().asset()).isEqualTo(new BigDecimal("5.000"));
        assertThat(mixed.payload().summary().cost()).isEqualTo(new BigDecimal("7.000"));
        var only =
                builder.buildDocuments(
                                List.of(new DocumentRequest("contract", List.of(ac.ref()))),
                                List.of(ac))
                        .getFirst();
        assertThat(only.payload().summary().total()).isEqualTo(new BigDecimal("7.000"));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
