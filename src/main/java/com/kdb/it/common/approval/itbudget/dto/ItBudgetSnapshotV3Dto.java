package com.kdb.it.common.approval.itbudget.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ApproverRole;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 신규 전산예산 미리보기와 상신에 사용하는 v3 공개 스냅샷 계약이다. */
public final class ItBudgetSnapshotV3Dto {

    private static final String MONEY_PATTERN = "^-?\\d+\\.\\d{3}$";
    private static final String EXCHANGE_RATE_PATTERN = "^-?\\d+\\.\\d{4}$";
    private static final String QUANTITY_PATTERN = "^-?\\d+$";

    private ItBudgetSnapshotV3Dto() {}

    @Schema(name = "ItBudgetSnapshotV3", description = "서버가 생성한 전산예산 v3 스냅샷")
    public record ItBudgetSnapshot(
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Form form,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Payload payload,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    SnapshotApprovalLine approvalLine,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    Integrity integrity) {}

    @Schema(name = "ItBudgetSnapshotV3Form", description = "전산예산 v3 양식 식별자")
    public record Form(
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version) {}

    @Schema(name = "ItBudgetSnapshotV3CodeLabel", description = "코드와 생성 시점 표시명")
    public record CodeLabel(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String label) {}

    @Schema(name = "ItBudgetSnapshotV3Organization", description = "조직 식별자와 표시명")
    public record Organization(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String name) {}

    @Schema(name = "ItBudgetSnapshotV3Person", description = "사용자 식별자와 표시명")
    public record Person(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String eno,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String rank) {}

    @Schema(name = "ItBudgetSnapshotV3Requester", description = "서버가 확정한 결재 신청자")
    public record Requester(
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eno,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String rank,
            @JsonFormat(
                            shape = JsonFormat.Shape.STRING,
                            pattern = "uuuu-MM-dd'T'HH:mm:ss",
                            lenient = OptBoolean.FALSE)
                    @JsonDeserialize(using = ApprovalDateTimeDeserializer.class)
                    @Schema(
                            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                            format = "date-time",
                            nullable = true)
                    LocalDateTime date) {
        public Requester(String eno, String name, String rank) {
            this(eno, name, rank, null);
        }
    }

    @Schema(name = "ItBudgetSnapshotV3ApprovalPerson", description = "결재선 사용자 표시 정보")
    public record ApprovalPerson(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ApproverRole role,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eno,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rank,
            @JsonFormat(
                            shape = JsonFormat.Shape.STRING,
                            pattern = "uuuu-MM-dd'T'HH:mm:ss",
                            lenient = OptBoolean.FALSE)
                    @JsonDeserialize(using = ApprovalDateTimeDeserializer.class)
                    @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            format = "date-time",
                            nullable = true)
                    LocalDateTime date) {}

    @Schema(name = "ItBudgetSnapshotV3ApprovalLine", description = "서버가 해석한 신청자와 결재선")
    public record SnapshotApprovalLine(
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    Requester requester,
            @NotNull @Size(max = 102) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid ApprovalPerson> approvers) {}

    @Schema(name = "ItBudgetSnapshotV3ProjectItem", description = "v3 사업 스냅샷 품목")
    public record ProjectItem(
            @NotBlank @Size(max = 30) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sequence,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    CodeLabel budgetType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String goodsName,
            @Pattern(regexp = QUANTITY_PATTERN)
                    @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            type = "string",
                            pattern = QUANTITY_PATTERN,
                            example = "1",
                            nullable = true)
                    String quantity,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String currency,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000",
                            nullable = true)
                    String amount,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            type = "string",
                            pattern = MONEY_PATTERN,
                            nullable = true,
                            description = "BITEMM.FC_AMT 외화 원금")
                    String foreignAmount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
                    String calculationBasis) {}

    @Schema(name = "ItBudgetSnapshotV3Project", description = "사업 원장 기반 v3 표시 스냅샷")
    public record Project(
            @NotBlank String id,
            @Positive int revision,
            @Schema(nullable = true) String ordinaryYn,
            @Schema(nullable = true) String name,
            @Schema(nullable = true) String baseYear,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(type = "string", pattern = MONEY_PATTERN, nullable = true)
                    String projectBudget,
            @NotBlank
                    @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            type = "string",
                            pattern = MONEY_PATTERN)
                    String currentRequestAmount,
            @NotNull @Valid CodeLabel editType,
            @NotNull @Valid CodeLabel progressStatus,
            @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
                    @Schema(type = "string", format = "date", nullable = true)
                    LocalDate startDate,
            @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
                    @Schema(type = "string", format = "date", nullable = true)
                    LocalDate endDate,
            @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
                    @Schema(type = "string", format = "date", nullable = true)
                    LocalDate feasibilityDate,
            @Schema(nullable = true) String outline,
            @Schema(nullable = true) String scope,
            @Schema(nullable = true) String security,
            @Schema(nullable = true) String purpose,
            @Schema(nullable = true) String necessity,
            @Schema(nullable = true) String expectedEffect,
            @Schema(nullable = true) String mainProgress,
            @Schema(nullable = true) String workforcePlan,
            @NotNull @Valid Organization supervisingOrganization,
            @NotNull @Valid Organization supervisingDepartment,
            @NotNull @Valid Person manager,
            @NotNull @Valid Person teamLeader,
            @NotNull @Valid Organization developmentDepartment,
            @NotNull @Valid Person developmentManager,
            @NotNull @Valid Person developmentTeamLeader,
            @NotNull @Valid CodeLabel businessType,
            @NotNull @Valid CodeLabel businessDetail,
            @NotNull @Valid CodeLabel costType,
            @NotNull @Valid CodeLabel skillType,
            @NotNull @Valid CodeLabel executionPattern,
            @Schema(nullable = true) String deploymentYn,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(type = "string", pattern = MONEY_PATTERN, nullable = true)
                    String assetBudget,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(type = "string", pattern = MONEY_PATTERN, nullable = true)
                    String costBudget,
            @NotNull List<@NotNull @Valid ProjectItem> items) {}

    @Schema(name = "ItBudgetSnapshotV3Terminal", description = "전산업무비 스냅샷 단말기")
    public record Terminal(
            @NotBlank @Size(max = 30) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Positive int revision,
            @Positive int sequence,
            @NotNull @Valid CodeLabel classification,
            @NotNull @Valid CodeLabel kind,
            @Schema(nullable = true) String usage,
            @Schema(nullable = true) String specification,
            @Schema(nullable = true) String currency,
            @Pattern(regexp = EXCHANGE_RATE_PATTERN)
                    @Schema(type = "string", pattern = EXCHANGE_RATE_PATTERN, nullable = true)
                    String exchangeRate,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(type = "string", pattern = MONEY_PATTERN, nullable = true)
                    String foreignAmount,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(type = "string", pattern = MONEY_PATTERN, nullable = true)
                    String budgetAmount) {}

    @Schema(name = "ItBudgetSnapshotV3Cost", description = "전산업무비 원장 기반 v3 표시 스냅샷")
    public record Cost(
            @NotBlank String id,
            @Positive int revision,
            @Schema(nullable = true) String baseYear,
            @Schema(nullable = true) String name,
            @Schema(nullable = true) String counterparty,
            @NotNull @Valid CodeLabel business,
            @NotNull @Valid CodeLabel budgetType,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(type = "string", pattern = MONEY_PATTERN, nullable = true)
                    String totalAmount,
            @Schema(nullable = true) String currency,
            @Pattern(regexp = EXCHANGE_RATE_PATTERN)
                    @Schema(type = "string", pattern = EXCHANGE_RATE_PATTERN, nullable = true)
                    String exchangeRate,
            @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
                    @Schema(type = "string", format = "date", nullable = true)
                    LocalDate exchangeRateBaseDate,
            @NotNull @Valid CodeLabel deferralType,
            @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
                    @Schema(type = "string", format = "date", nullable = true)
                    LocalDate firstDeferralDate,
            @Schema(nullable = true) String reason,
            @NotNull @Valid Organization supervisingDepartment,
            @NotNull @Valid Person manager,
            @Schema(nullable = true) String securitySystemUseYn,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(type = "string", pattern = MONEY_PATTERN, nullable = true)
                    String assetBudget,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(type = "string", pattern = MONEY_PATTERN, nullable = true)
                    String costBudget,
            @NotNull List<@NotNull @Valid Terminal> terminals) {}

    @Schema(name = "ItBudgetSnapshotV3Summary", description = "v3 스냅샷 금액 요약")
    public record Summary(
            @NotBlank
                    @Pattern(regexp = MONEY_PATTERN)
                    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String total,
            @NotBlank
                    @Pattern(regexp = MONEY_PATTERN)
                    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String asset,
            @NotBlank
                    @Pattern(regexp = MONEY_PATTERN)
                    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String cost) {}

    @Schema(name = "ItBudgetSnapshotV3LedgerRow", description = "원장 테이블의 전체 영속 컬럼")
    public record LedgerRow(
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String table,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    Map<@NotBlank String, Object> columns) {}

    @Schema(name = "ItBudgetSnapshotV3LedgerAggregate", description = "원장 aggregate 스냅샷")
    public record LedgerAggregate(
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kind,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LedgerRow parent,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid LedgerRow> children) {}

    @Schema(name = "ItBudgetSnapshotV3Ledger", description = "상신 시점 전체 원장 스냅샷")
    public record Ledger(
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String format,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid LedgerAggregate> aggregates) {}

    @Schema(name = "ItBudgetSnapshotV3Payload", description = "표시 projection과 전체 원장")
    public record Payload(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid Project> projects,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid Cost> costs,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Summary summary,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Ledger ledger) {}

    @Schema(name = "ItBudgetSnapshotV3Source", description = "v3 스냅샷 무결성 대상 원장")
    public record SnapshotSource(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SourceKind kind,
            @NotBlank @Size(max = 30) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int order,
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String digest) {}

    @Schema(name = "ItBudgetSnapshotV3Integrity", description = "v3 스냅샷 무결성 메타데이터")
    public record Integrity(
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String algorithm,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String canonicalization,
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String payloadDigest,
            @NotNull
                    @JsonFormat(shape = JsonFormat.Shape.STRING)
                    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
                    Instant capturedAt,
            @NotEmpty @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid SnapshotSource> sources) {}
}
