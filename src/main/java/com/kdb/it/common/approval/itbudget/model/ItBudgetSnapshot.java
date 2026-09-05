package com.kdb.it.common.approval.itbudget.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 전산예산 v2 저장 스냅샷의 서버 내부 불변 모델이다.
 *
 * <p>HTTP DTO와 분리해 산술 값은 {@link BigDecimal}로 유지하고, 이후 경계 변환기에서 고정 scale 문자열 공개 계약으로 변환한다.
 */
public record ItBudgetSnapshot(
        Form form, Payload payload, ApprovalLine approvalLine, Integrity integrity) {

    public record Form(String id, int version) {}

    public record CodeLabel(String code, String label) {}

    public record Organization(String code, String name) {}

    public record Person(String eno, String name, String rank) {}

    public record ApprovalPerson(String eno, String name, String rank, LocalDate date) {}

    public record ApprovalLine(Person requester, List<ApprovalPerson> approvers) {
        public ApprovalLine {
            approvers = List.copyOf(approvers);
        }
    }

    public record ProjectItem(
            int revision,
            int sequence,
            CodeLabel budgetType,
            String goodsName,
            BigDecimal quantity,
            String currency,
            BigDecimal amount,
            String calculationBasis) {}

    public record Project(
            String id,
            int revision,
            String ordinaryYn,
            String name,
            String baseYear,
            BigDecimal projectBudget,
            CodeLabel editType,
            CodeLabel progressStatus,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate feasibilityDate,
            String outline,
            String scope,
            String security,
            String purpose,
            String necessity,
            String expectedEffect,
            String mainProgress,
            String workforcePlan,
            Organization supervisingOrganization,
            Organization supervisingDepartment,
            Person manager,
            Person teamLeader,
            Organization developmentDepartment,
            Person developmentManager,
            Person developmentTeamLeader,
            CodeLabel businessType,
            CodeLabel businessDetail,
            CodeLabel costType,
            CodeLabel skillType,
            CodeLabel executionPattern,
            String deploymentYn,
            BigDecimal assetBudget,
            BigDecimal costBudget,
            List<ProjectItem> items) {
        public Project {
            items = List.copyOf(items);
        }
    }

    public record Terminal(
            int revision,
            int sequence,
            CodeLabel classification,
            CodeLabel kind,
            String usage,
            String specification,
            String currency,
            BigDecimal exchangeRate,
            BigDecimal foreignAmount,
            BigDecimal budgetAmount) {}

    public record Cost(
            String id,
            int revision,
            String name,
            String counterparty,
            CodeLabel business,
            CodeLabel budgetType,
            BigDecimal totalAmount,
            String currency,
            BigDecimal exchangeRate,
            LocalDate exchangeRateBaseDate,
            CodeLabel deferralType,
            LocalDate firstDeferralDate,
            String reason,
            Organization supervisingDepartment,
            Person manager,
            String securitySystemUseYn,
            BigDecimal assetBudget,
            BigDecimal costBudget,
            List<Terminal> terminals) {
        public Cost {
            terminals = List.copyOf(terminals);
        }
    }

    public record Summary(BigDecimal total, BigDecimal asset, BigDecimal cost) {}

    public record Payload(List<Project> projects, List<Cost> costs, Summary summary) {
        public Payload {
            projects = List.copyOf(projects);
            costs = List.copyOf(costs);
        }
    }

    public record Integrity(
            String algorithm,
            String canonicalization,
            String payloadDigest,
            Instant capturedAt,
            List<Source> sources) {
        public Integrity {
            sources = List.copyOf(sources);
        }
    }

    public record Source(String kind, String id, int revision, int order, String digest) {}
}
