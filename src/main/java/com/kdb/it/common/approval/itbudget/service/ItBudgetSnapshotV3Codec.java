package com.kdb.it.common.approval.itbudget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetSnapshotV3Dto;
import com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshot;
import com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshotV3;

/** v3 내부 숫자 모델과 공개 고정 소수 문자열 계약을 변환한다. */
public final class ItBudgetSnapshotV3Codec {
    private ItBudgetSnapshotV3Codec() {}

    /** 표시 금액만 고정 scale 문자열로 바꾸고 원장 column 값의 캡처 타입과 값을 보존한다. */
    public static ItBudgetSnapshotV3Dto.Payload toPublic(
            ObjectMapper mapper,
            ItBudgetCanonicalJson canonical,
            ItBudgetSnapshotV3.Payload payload)
            throws JsonProcessingException {
        return mapper.readValue(canonical.write(payload), ItBudgetSnapshotV3Dto.Payload.class);
    }

    /** 검증된 공개 payload를 ledger를 포함한 v3 해시 모델로 복원한다. */
    public static ItBudgetSnapshotV3.Payload toInternal(
            ObjectMapper mapper, ItBudgetSnapshotV3Dto.Payload payload) {
        return mapper.convertValue(payload, ItBudgetSnapshotV3.Payload.class);
    }

    /** 기존 메일·서버 소비자가 쓰는 v2 표시 projection으로 변환한다. */
    public static ItBudgetSnapshot.Payload toV2View(ItBudgetSnapshotV3.Payload payload) {
        return new ItBudgetSnapshot.Payload(
                payload.projects().stream().map(ItBudgetSnapshotV3Codec::toV2Project).toList(),
                payload.costs().stream().map(ItBudgetSnapshotV3Codec::toV2Cost).toList(),
                new ItBudgetSnapshot.Summary(
                        payload.summary().total(),
                        payload.summary().asset(),
                        payload.summary().cost()));
    }

    private static ItBudgetSnapshot.Project toV2Project(ItBudgetSnapshotV3.Project project) {
        return new ItBudgetSnapshot.Project(
                project.id(),
                project.revision(),
                project.ordinaryYn(),
                project.name(),
                project.baseYear(),
                project.projectBudget(),
                project.currentRequestAmount(),
                code(project.editType()),
                code(project.progressStatus()),
                project.startDate(),
                project.endDate(),
                project.feasibilityDate(),
                project.outline(),
                project.scope(),
                project.security(),
                project.purpose(),
                project.necessity(),
                project.expectedEffect(),
                project.mainProgress(),
                project.workforcePlan(),
                organization(project.supervisingOrganization()),
                organization(project.supervisingDepartment()),
                person(project.manager()),
                person(project.teamLeader()),
                organization(project.developmentDepartment()),
                person(project.developmentManager()),
                person(project.developmentTeamLeader()),
                code(project.businessType()),
                code(project.businessDetail()),
                code(project.costType()),
                code(project.skillType()),
                code(project.executionPattern()),
                project.deploymentYn(),
                project.assetBudget(),
                project.costBudget(),
                project.items().stream()
                        .map(
                                item ->
                                        new ItBudgetSnapshot.ProjectItem(
                                                item.id(),
                                                item.revision(),
                                                item.sequence(),
                                                code(item.budgetType()),
                                                item.goodsName(),
                                                item.quantity(),
                                                item.currency(),
                                                item.amount(),
                                                item.calculationBasis()))
                        .toList());
    }

    private static ItBudgetSnapshot.Cost toV2Cost(ItBudgetSnapshotV3.Cost cost) {
        return new ItBudgetSnapshot.Cost(
                cost.id(),
                cost.revision(),
                cost.baseYear(),
                cost.name(),
                cost.counterparty(),
                code(cost.business()),
                code(cost.budgetType()),
                cost.totalAmount(),
                cost.currency(),
                cost.exchangeRate(),
                cost.exchangeRateBaseDate(),
                code(cost.deferralType()),
                cost.firstDeferralDate(),
                cost.reason(),
                organization(cost.supervisingDepartment()),
                person(cost.manager()),
                cost.securitySystemUseYn(),
                cost.assetBudget(),
                cost.costBudget(),
                cost.terminals().stream()
                        .map(
                                terminal ->
                                        new ItBudgetSnapshot.Terminal(
                                                terminal.id(),
                                                terminal.revision(),
                                                terminal.sequence(),
                                                code(terminal.classification()),
                                                code(terminal.kind()),
                                                terminal.usage(),
                                                terminal.specification(),
                                                terminal.currency(),
                                                terminal.exchangeRate(),
                                                terminal.foreignAmount(),
                                                terminal.budgetAmount()))
                        .toList());
    }

    private static ItBudgetSnapshot.CodeLabel code(ItBudgetSnapshotV3.CodeLabel value) {
        return new ItBudgetSnapshot.CodeLabel(value.code(), value.label());
    }

    private static ItBudgetSnapshot.Organization organization(
            ItBudgetSnapshotV3.Organization value) {
        return new ItBudgetSnapshot.Organization(value.code(), value.name());
    }

    private static ItBudgetSnapshot.Person person(ItBudgetSnapshotV3.Person value) {
        return new ItBudgetSnapshot.Person(value.eno(), value.name(), value.rank());
    }
}
