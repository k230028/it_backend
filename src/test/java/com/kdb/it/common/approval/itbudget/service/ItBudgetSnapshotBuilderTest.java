package com.kdb.it.common.approval.itbudget.service;

import static com.kdb.it.common.approval.itbudget.service.ItBudgetSourceLoaderTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.*;
import com.kdb.it.domain.budget.cost.entity.*;
import com.kdb.it.domain.budget.project.entity.*;
import com.kdb.it.domain.budget.project.service.ProjectAmountCalculator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItBudgetSnapshotBuilderTest {
    final ItBudgetCanonicalJson canonical =
            new ItBudgetCanonicalJson(new ObjectMapper().registerModule(new JavaTimeModule()));
    final UserRepository users = mock(UserRepository.class);
    final OrganizationRepository organizations = mock(OrganizationRepository.class);
    final CodeRepository codes = mock(CodeRepository.class);
    final ItBudgetSnapshotBuilder builder =
            new ItBudgetSnapshotBuilder(
                    canonical, new ProjectAmountCalculator(), users, organizations, codes);

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
                        .curC("KRW")
                        .delYn("N")
                        .build();
        var deleted = item("D", 1, "P1", 1, "Y");
        var result = build(p, List.of(i, deleted));
        assertThat(result.payload().projects().getFirst().name()).isEqualTo(" 사업 ");
        assertThat(result.payload().projects().getFirst().items()).hasSize(1);
        assertThat(result.payload().summary().total()).isEqualTo(new BigDecimal("12.340"));
        assertThat(result.payload().summary().asset()).isEqualTo(new BigDecimal("12.340"));
        assertThat(result.payloadDigest()).isEqualTo(canonical.digest(result.payload()));
        assertThat(result.sources().getFirst().digest())
                .isNotEqualTo(build(p, List.of(i)).sources().getFirst().digest());
    }

    @Test
    void auditAndFetchedNamesOnlyChangeTheAppropriateDigestBoundary() {
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
        assertThat(build(audit, List.of()).payloadDigest()).isEqualTo(before.payloadDigest());
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
                                com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.Payload
                                        .class);
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
                                com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.Payload
                                        .class));
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
}
