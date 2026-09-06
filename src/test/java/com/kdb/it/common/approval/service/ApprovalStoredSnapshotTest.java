package com.kdb.it.common.approval.service;

import static com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.exception.DataCorruptionException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ApprovalStoredSnapshotTest {
    private final ApprovalLineDelegate delegate =
            com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.delegate(MAPPER);

    @Test
    void replacementInheritsPendingSlotRoleAndNewSlotsAreAdditional() throws Exception {
        var root = v2();
        object(root, "/approvalLine/approvers/0").put("role", "TEAM_LEAD");
        object(root, "/approvalLine/approvers/1").put("role", "DEPT_HEAD");
        var application = Capplm.builder().itPtlApfPrgStsC("1").dcdReqInf(root.toString()).build();
        var orders =
                List.of(
                        Cdecim.builder().dcrEno("E3").itPtlDcdStsC("1").build(),
                        Cdecim.builder().dcrEno("E4").itPtlDcdStsC("1").build(),
                        Cdecim.builder().dcrEno("E5").itPtlDcdStsC("1").build());
        var users =
                orders.stream()
                        .<com.kdb.it.common.iam.entity.CuserI>map(
                                a ->
                                        com.kdb.it.common.iam.entity.CuserI.builder()
                                                .eno(a.getDcrEno())
                                                .usrNm("교체")
                                                .ptCNm("직급")
                                                .build())
                        .toList();
        delegate.replacePendingApproversInDetail(application, orders, users);
        var result = MAPPER.readTree(application.getDcdReqInf());
        assertThat(result.at("/approvalLine/approvers/0/role").asText()).isEqualTo("TEAM_LEAD");
        assertThat(result.at("/approvalLine/approvers/1/role").asText()).isEqualTo("DEPT_HEAD");
        assertThat(result.at("/approvalLine/approvers/2/role").asText()).isEqualTo("ADDITIONAL");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void legacyReplacementRemovesUnselectedStaticPendingNodes(boolean withOrder) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode line = root.putObject("approvalLine");
        line.putObject("teamLead").put("id", "E1").put("date", "");
        line.putObject("deptHead").put("id", "E2").put("date", "");
        if (withOrder) line.putArray("order").add("E1").add("E2");
        Capplm application =
                Capplm.builder().itPtlApfPrgStsC("1").dcdReqInf(root.toString()).build();
        List<Cdecim> order =
                List.of(Cdecim.builder().dcrEno("E3").dcrSqnSno(1).itPtlDcdStsC("1").build());
        var user =
                com.kdb.it.common.iam.entity.CuserI.builder()
                        .eno("E3")
                        .usrNm("교체")
                        .ptCNm("팀장")
                        .build();

        delegate.replacePendingApproversInDetail(application, order, List.of(user));

        assertThat(reader().read(application.getDcdReqInf()).version()).isEqualTo(1);
        assertThat(
                        MAPPER.readTree(application.getDcdReqInf())
                                .at("/approvalLine/deptHead")
                                .isMissingNode())
                .isTrue();
        delegate.updateApprovalOrder(application, order);
        delegate.doUpdate(application, order, order);
        assertThat(MAPPER.readTree(application.getDcdReqInf()).at("/approvalLine/order").toString())
                .isEqualTo("[\"E3\"]");
    }

    @Test
    void legacyReplacementPreservesCompletedAdditionalBeforePendingStaticNodes() throws Exception {
        Capplm application =
                Capplm.builder()
                        .itPtlApfPrgStsC("1")
                        .dcdReqInf(
                                """
            {"approvalLine":{"teamLead":{"id":"E1","date":""},"deptHead":{"id":"E2","date":""},
            "additionalApprovers":[{"id":"E0","name":"완료","date":"2026-09-01"}],"order":["E0","E1","E2"]}}
            """)
                        .build();
        var completed =
                MAPPER.readTree(application.getDcdReqInf())
                        .at("/approvalLine/additionalApprovers/0");
        List<Cdecim> order =
                List.of(
                        Cdecim.builder().dcrEno("E0").dcrSqnSno(1).itPtlDcdStsC("2").build(),
                        Cdecim.builder().dcrEno("E3").dcrSqnSno(2).itPtlDcdStsC("1").build());
        var user =
                com.kdb.it.common.iam.entity.CuserI.builder()
                        .eno("E3")
                        .usrNm("교체")
                        .ptCNm("팀장")
                        .build();

        delegate.replacePendingApproversInDetail(application, order, List.of(user));

        assertThat(reader().read(application.getDcdReqInf()).version()).isEqualTo(1);
        delegate.updateApprovalOrder(application, order);
        var updated = MAPPER.readTree(application.getDcdReqInf());
        assertThat(updated.at("/approvalLine/additionalApprovers/0")).isEqualTo(completed);
        assertThat(updated.at("/approvalLine/deptHead").isMissingNode()).isTrue();
        assertThat(updated.at("/approvalLine/order").toString()).isEqualTo("[\"E0\",\"E3\"]");
    }

    static Stream<Consumer<ObjectNode>> corruptions() {
        return Stream.of(
                r -> object(r, "/approvalLine/approvers/0").remove("role"),
                r -> object(r, "/approvalLine/approvers/0").putNull("role"),
                r -> object(r, "/approvalLine/approvers/0").put("role", "UNKNOWN"),
                r -> object(r, "/approvalLine/approvers/1").put("role", "TEAM_LEAD"),
                r -> object(r, "/form").put("id", "another-form"),
                r -> object(r, "/form").put("version", 3),
                r -> object(r, "/form").put("version", "2"),
                r -> object(r, "/form").remove("version"),
                r -> r.remove("payload"),
                r -> r.put("unknown", true),
                r -> object(r, "/payload/projects/0/items/0").put("unknown", true),
                r -> object(r, "/payload/projects/0").remove("outline"),
                r -> object(r, "/payload/projects/0").putNull("currentRequestAmount"),
                r -> object(r, "/payload/projects/0").put("currentRequestAmount", 100),
                r -> object(r, "/payload/projects/0").put("currentRequestAmount", "1e2"),
                r -> object(r, "/payload/projects/0").put("currentRequestAmount", "100.00"),
                r -> object(r, "/payload/projects/0").put("startDate", "2026-02-30"),
                r -> object(r, "/payload/projects/0/items/0").put("quantity", "1.5"),
                r -> object(r, "/payload/costs/0/terminals/0").put("exchangeRate", "1.25"),
                r -> object(r, "/payload/costs/0").putNull("terminals"),
                r -> object(r, "/payload/projects/0/items/0").put("id", ""),
                r -> object(r, "/payload/projects/0/items/0").put("revision", 0),
                r -> object(r, "/payload/projects/0").put("name", "변조된 사업"),
                r -> object(r, "/integrity").put("algorithm", "MD5"),
                r -> object(r, "/integrity").put("canonicalization", "OTHER"),
                r -> object(r, "/integrity").put("payloadDigest", "a".repeat(64)),
                r -> object(r, "/integrity").put("payloadDigest", "a".repeat(63)),
                r -> object(r, "/integrity").put("capturedAt", "2026-09-06"),
                r -> object(r, "/integrity/sources/0").put("digest", "z".repeat(64)),
                r -> object(r, "/integrity/sources/0").put("kind", "OTHER"),
                r -> object(r, "/integrity/sources/0").put("id", "unknown-source"),
                r -> object(r, "/approvalLine/requester").putNull("eno"),
                r -> object(r, "/approvalLine/approvers/0").put("date", "2026-02-30"),
                r -> object(r, "/approvalLine/approvers/0").remove("date"),
                r -> object(r, "/approvalLine/approvers/0").putNull("name"),
                r -> r.putNull("approvalLine"));
    }

    @ParameterizedTest
    @MethodSource("corruptions")
    void corruptedV2BlocksStateMutation(Consumer<ObjectNode> corruption) throws Exception {
        ObjectNode root = v2();
        corruption.accept(root);
        Capplm application = application(root.toString());
        assertThatThrownBy(
                        () ->
                                delegate.doUpdate(
                                        application, List.of(approver()), List.of(approver())))
                .isInstanceOf(DataCorruptionException.class);
        verify(application, never()).updateDetailContent(anyString());
    }

    @Test
    void malformedAndTrailingJsonBlockMutation() throws Exception {
        for (String raw : List.of("{\"privateName\":broken", v2() + " {}")) {
            assertThatThrownBy(
                            () ->
                                    delegate.doUpdate(
                                            application(raw),
                                            List.of(approver()),
                                            List.of(approver())))
                    .isInstanceOf(DataCorruptionException.class);
        }
    }

    @Test
    void validV2UpdatesPendingDateWithoutChangingPayloadOrIntegrity() throws Exception {
        ObjectNode original = v2();
        Capplm application = application(original.toString());
        delegate.doUpdate(application, List.of(approver()), List.of(approver()));
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(application).updateDetailContent(captor.capture());
        var updated = MAPPER.readTree(captor.getValue());
        assertThat(updated.at("/approvalLine/approvers/0/date").asText())
                .matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(updated.get("payload")).isEqualTo(original.get("payload"));
        assertThat(updated.at("/approvalLine/approvers/0/role").asText()).isEqualTo("TEAM_LEAD");
        assertThat(updated.at("/approvalLine/approvers/1/role").asText()).isEqualTo("DEPT_HEAD");
        assertThat(updated.get("integrity")).isEqualTo(original.get("integrity"));
    }

    @Test
    void inProgressLegacyMissingRequiredLineBlocksMutation() {
        Capplm application = application("{\"form\":{\"id\":\"it-budget\",\"version\":1}}");
        when(application.getItPtlApfPrgStsC()).thenReturn("1");
        assertThatThrownBy(
                        () ->
                                delegate.doUpdate(
                                        application, List.of(approver()), List.of(approver())))
                .isInstanceOf(DataCorruptionException.class);
    }

    static Cdecim approver() {
        return Cdecim.builder().dcrEno("E1").dcrSqnSno(1).build();
    }

    @Test
    void everyV2MutationVerifiesIntegrityBeforeWriting() throws Exception {
        ObjectNode root = v2();
        object(root, "/payload/projects/0").put("name", "오염");
        List<Consumer<Capplm>> mutations =
                List.of(
                        c ->
                                delegate.applyRecallInfo(
                                        c,
                                        "U1",
                                        "회수",
                                        ApprovalDetailPolicy.DetailMode.SNAPSHOT_REQUIRED),
                        c -> delegate.addApproverToDetail(c, "E3", "새 결재자", "직급"),
                        c -> delegate.removeApproverFromDetail(c, 0),
                        c -> delegate.updateApprovalOrder(c, List.of(approver())),
                        c ->
                                delegate.replacePendingApproversInDetail(
                                        c, List.of(approver()), List.of()));
        for (Consumer<Capplm> mutation : mutations) {
            Capplm application = application(root.toString());
            assertThatThrownBy(() -> mutation.accept(application))
                    .isInstanceOf(DataCorruptionException.class);
            verify(application, never()).updateDetailContent(anyString());
        }
    }

    @Test
    void v2ApprovalCommandsPreserveCompletedPersonAndReplacePendingIdentityAndDate()
            throws Exception {
        ObjectNode original = v2();
        object(original, "/approvalLine/approvers/0").put("role", "ADDITIONAL");
        object(original, "/approvalLine/approvers/1").put("role", "ADDITIONAL");
        Capplm application =
                Capplm.builder().dcdReqInf(original.toString()).itPtlApfPrgStsC("1").build();
        Cdecim first = approver();
        Cdecim second = Cdecim.builder().dcrEno("E2").dcrSqnSno(2).itPtlDcdStsC("1").build();
        delegate.doUpdate(application, List.of(first, second), List.of(first));
        var firstCompleted =
                MAPPER.readTree(application.getDcdReqInf()).at("/approvalLine/approvers/0");
        delegate.updateApprovalOrder(application, List.of(second, first));
        assertThat(MAPPER.readTree(application.getDcdReqInf()).at("/approvalLine/approvers/1"))
                .isEqualTo(firstCompleted);
        delegate.removeApproverFromDetail(application, 0);
        delegate.addApproverToDetail(application, "E3", "추가 사용자", "과장");
        first = Cdecim.builder().dcrEno("E1").dcrSqnSno(1).itPtlDcdStsC("2").build();
        Cdecim replacement = Cdecim.builder().dcrEno("E4").dcrSqnSno(2).itPtlDcdStsC("1").build();
        var user =
                com.kdb.it.common.iam.entity.CuserI.builder()
                        .eno("E4")
                        .usrNm("교체 사용자")
                        .ptCNm("부장")
                        .build();
        delegate.replacePendingApproversInDetail(
                application, List.of(first, replacement), List.of(user));
        var line = MAPPER.readTree(application.getDcdReqInf()).get("approvalLine");
        assertThat(line.at("/approvers/0")).isEqualTo(firstCompleted);
        assertThat(line.at("/approvers/1/eno").textValue()).isEqualTo("E4");
        assertThat(line.at("/approvers/1/name").textValue()).isEqualTo("교체 사용자");
        assertThat(line.at("/approvers/1/rank").textValue()).isEqualTo("부장");
        assertThat(line.at("/approvers/1/date").isNull()).isTrue();
        assertThat(line.at("/approvers/0/role").asText()).isEqualTo("ADDITIONAL");
        assertThat(line.at("/approvers/1/role").asText()).isEqualTo("ADDITIONAL");
        delegate.applyRecallInfo(
                application, "U1", "회수", ApprovalDetailPolicy.DetailMode.SNAPSHOT_REQUIRED);
        var finalRoot = MAPPER.readTree(application.getDcdReqInf());
        assertThat(finalRoot.get("payload")).isEqualTo(original.get("payload"));
        assertThat(finalRoot.get("integrity")).isEqualTo(original.get("integrity"));
        assertThat(reader().read(application.getDcdReqInf()).version()).isEqualTo(2);
    }

    @Test
    void legacyInProgressMalformedLineAndUnknownTargetAreNotSilentlySkipped() {
        for (String raw :
                List.of(
                        "{}",
                        "{\"approvalLine\":[]}",
                        "{\"approvalLine\":{}}",
                        "{\"approvalLine\":{\"step1\":{\"id\":\"E2\"}}}",
                        "{\"approvalLine\":{\"step1\":{\"id\":\"E1\"},\"order\":[\"GHOST\"]}}")) {
            Capplm application = application(raw);
            assertThatThrownBy(
                            () ->
                                    delegate.doUpdate(
                                            application, List.of(approver()), List.of(approver())))
                    .isInstanceOf(DataCorruptionException.class);
        }
    }

    static Capplm application(String raw) {
        Capplm application = mock(Capplm.class);
        when(application.getDcdReqInf()).thenReturn(raw);
        return application;
    }

    @Test
    void inProgressLegacyCommandsKeepStoredOrderConsistentBetweenCalls() throws Exception {
        Capplm application =
                Capplm.builder()
                        .itPtlApfPrgStsC("1")
                        .dcdReqInf(
                                """
                {"form":{"id":"another-form","version":1},"business":{"keep":true},
                 "approvalLine":{"teamLead":{"id":"E1","date":"2026-09-01"},
                   "additionalApprovers":[{"id":"E2","date":""}],"order":["E1","E2"]}}
                """)
                        .build();
        Cdecim first = Cdecim.builder().dcrEno("E1").dcrSqnSno(1).itPtlDcdStsC("2").build();
        Cdecim second = Cdecim.builder().dcrEno("E2").dcrSqnSno(2).itPtlDcdStsC("1").build();
        Cdecim third = Cdecim.builder().dcrEno("E3").dcrSqnSno(3).itPtlDcdStsC("1").build();
        delegate.addApproverToDetail(application, "E3", "추가 결재자", "과장");
        delegate.updateApprovalOrder(application, List.of(first, second, third));
        delegate.removeApproverFromDetail(application, 1);
        delegate.updateApprovalOrder(application, List.of(first, third));
        Cdecim replacement = Cdecim.builder().dcrEno("E4").dcrSqnSno(2).itPtlDcdStsC("1").build();
        var user =
                com.kdb.it.common.iam.entity.CuserI.builder()
                        .eno("E4")
                        .usrNm("교체 결재자")
                        .ptCNm("부장")
                        .build();
        delegate.replacePendingApproversInDetail(
                application, List.of(first, replacement), List.of(user));
        delegate.updateApprovalOrder(application, List.of(first, replacement));
        var root = MAPPER.readTree(application.getDcdReqInf());
        assertThat(root.at("/approvalLine/order").toString()).isEqualTo("[\"E1\",\"E4\"]");
        assertThat(root.at("/business/keep").booleanValue()).isTrue();
    }
}
