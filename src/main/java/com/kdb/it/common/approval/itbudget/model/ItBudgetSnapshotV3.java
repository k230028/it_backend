package com.kdb.it.common.approval.itbudget.model;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ApproverRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 전산예산 v3 저장 스냅샷의 서버 내부 불변 모델이다. */
public record ItBudgetSnapshotV3(
        Form form, Payload payload, ApprovalLine approvalLine, Integrity integrity) {

    public record Form(String id, int version) {}

    public record CodeLabel(String code, String label) {}

    public record Organization(String code, String name) {}

    public record Person(String eno, String name, String rank) {}

    /** 결재 신청자는 선택 담당자와 구분하며 date에는 상신 시각을 초 단위로 기록한다. */
    public record Requester(String eno, String name, String rank, LocalDateTime date) {
        public Requester(String eno, String name, String rank) {
            this(eno, name, rank, null);
        }
    }

    /** date는 초 단위 실제 결재일시이며 미리보기의 미결재 사용자는 null이다. */
    public record ApprovalPerson(
            ApproverRole role, String eno, String name, String rank, LocalDateTime date) {}

    public record ApprovalLine(Requester requester, List<ApprovalPerson> approvers) {
        public ApprovalLine {
            approvers = List.copyOf(approvers);
        }
    }

    /** id·sequence는 품목 PK이며 revision은 부모 사업의 개정 순번이다. */
    public record ProjectItem(
            String id,
            int revision,
            int sequence,
            CodeLabel budgetType,
            String goodsName,
            BigDecimal quantity,
            String currency,
            BigDecimal amount,
            BigDecimal foreignAmount,
            String calculationBasis) {}

    public record Project(
            String id,
            int revision,
            String ordinaryYn,
            String name,
            String baseYear,
            BigDecimal projectBudget,
            BigDecimal currentRequestAmount,
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

    /** id·sequence는 단말기 PK이며 revision은 부모 전산업무비의 개정 순번이다. */
    public record Terminal(
            String id,
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
            String baseYear,
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

    public record Payload(
            List<Project> projects,
            List<Cost> costs,
            Summary summary,
            ItBudgetLedgerSnapshot ledger) {
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
