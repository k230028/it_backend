package com.kdb.it.common.approval.itbudget.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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

/** 전산예산 미리보기·상신 경계에서 사용하는 고정 HTTP 계약이다. */
public final class ItBudgetApprovalDto {

    private static final String MONEY_PATTERN = "^-?\\d+\\.\\d{3}$";
    private static final String EXCHANGE_RATE_PATTERN = "^-?\\d+\\.\\d{4}$";
    private static final String QUANTITY_PATTERN = "^-?\\d+$";

    private ItBudgetApprovalDto() {}

    public enum SourceKind {
        PROJECT,
        COST
    }

    public enum ApproverRole {
        TEAM_LEAD,
        DEPT_HEAD,
        ADDITIONAL
    }

    public enum RequestApproverRole {
        TEAM_LEAD,
        DEPT_HEAD
    }

    @Schema(name = "ItBudgetSourceRef", description = "전산예산 원장 정확한 개정본 참조")
    public record SourceRef(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SourceKind kind,
            @NotBlank @Size(max = 30) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @NotNull @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    Integer revision,
            @NotNull @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    Integer order) {}

    @Schema(name = "ItBudgetApproverRef", description = "역할이 부여된 결재자 사번")
    public record ApproverRef(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RequestApproverRole role,
            @NotBlank @Size(max = 14) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String eno) {}

    @Schema(name = "ItBudgetDocumentRequest", description = "미리보기 또는 상신 문서 단위 입력")
    public record DocumentRequest(
            @NotBlank @Size(max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String clientDocumentKey,
            @NotEmpty @Size(max = 500) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid SourceRef> sourceRefs) {}

    @Schema(name = "ItBudgetPreviewRequest", description = "전산예산 결재 미리보기 요청")
    public record PreviewRequest(
            @NotNull @Size(max = 2) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid ApproverRef> approvers,
            @NotEmpty @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid DocumentRequest> documents) {}

    @Schema(name = "ItBudgetSourceDigest", description = "미리보기 시점 원장 다이제스트")
    public record SourceDigest(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SourceKind kind,
            @NotBlank @Size(max = 30) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int order,
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String sourceDigest,
            @NotBlank
                    @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            description = "미리보기 명칭. 토큰에 결속되어 삭제된 원장의 충돌 안내에도 사용한다.")
                    String displayName) {}

    @Schema(name = "ItBudgetPreviewDocument", description = "서버가 생성한 전산예산 미리보기 문서")
    public record PreviewDocument(
            @NotBlank @Size(max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String clientDocumentKey,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    ItBudgetSnapshotV3Dto.ItBudgetSnapshot snapshot,
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String payloadDigest,
            @NotEmpty @Size(max = 500) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid SourceDigest> sources) {}

    @Schema(name = "ItBudgetPreviewResponse", description = "전산예산 결재 미리보기 응답")
    public record PreviewResponse(
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String previewDigest,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String previewToken,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
            @NotEmpty @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid PreviewDocument> documents) {}

    @Schema(name = "ItBudgetSubmissionDocument", description = "전산예산 상신 문서별 다이제스트")
    public record SubmissionDocument(
            @NotBlank @Size(max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String clientDocumentKey,
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String payloadDigest,
            @NotEmpty @Size(max = 500) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid SourceDigest> sources) {}

    @Schema(name = "ItBudgetSubmissionRequest", description = "전산예산 결재 상신 요청")
    public record SubmissionRequest(
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String previewDigest,
            @NotBlank @Size(max = 8192) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String previewToken,
            @NotNull @Size(min = 2, max = 2) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid ApproverRef> approvers,
            @NotEmpty @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid SubmissionDocument> documents) {}

    @Schema(name = "ItBudgetSubmissionResponse", description = "전산예산 결재 상신 결과")
    public record SubmissionResponse(
            @NotEmpty @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotBlank String> applicationNumbers) {}

    /** 전산예산 v2 미리보기의 공개 스냅샷 구조다. 내부 원장 모델과 분리해 API schema를 안정화한다. */
    @Schema(name = "ItBudgetSnapshot", description = "서버가 생성한 전산예산 v2 스냅샷")
    public record ItBudgetSnapshot(
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Form form,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Payload payload,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    SnapshotApprovalLine approvalLine,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    Integrity integrity) {}

    @Schema(name = "ItBudgetSnapshotForm", description = "전산예산 스냅샷 양식 식별자")
    public record Form(
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version) {}

    @Schema(name = "ItBudgetSnapshotCodeLabel", description = "코드와 미리보기 시점 표시명")
    public record CodeLabel(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String label) {}

    @Schema(name = "ItBudgetSnapshotOrganization", description = "조직 식별자와 표시명")
    public record Organization(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String name) {}

    @Schema(name = "ItBudgetSnapshotPerson", description = "사용자 식별자와 미리보기 시점 표시명")
    public record Person(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String eno,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String rank) {}

    /** 신청자 사번·성명은 필수이며, 원장 선택 담당자용 Person과 혼용하지 않는다. */
    @Schema(name = "ItBudgetSnapshotRequester", description = "서버가 확정한 필수 결재 신청자")
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
                            nullable = true,
                            description = "기안 결재일시(초 단위), 미리보기와 과거 문서는 null")
                    LocalDateTime date) {
        public Requester(String eno, String name, String rank) {
            this(eno, name, rank, null);
        }
    }

    @Schema(name = "ItBudgetSnapshotApprovalPerson", description = "결재선 사용자 표시 정보")
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
                            nullable = true,
                            description = "실제 결재일시(초 단위), 미결재 상태는 null")
                    LocalDateTime date) {}

    @Schema(name = "ItBudgetSnapshotApprovalLine", description = "서버가 해석한 신청자와 결재선")
    public record SnapshotApprovalLine(
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    Requester requester,
            @NotNull @Size(max = 102) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid ApprovalPerson> approvers) {}

    @Schema(name = "ItBudgetSnapshotProjectItem", description = "사업 스냅샷 품목")
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
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
                    String calculationBasis) {}

    @Schema(name = "ItBudgetSnapshotProject", description = "사업 원장 기반 스냅샷")
    public record Project(
            @NotBlank String id,
            @Positive int revision,
            @Schema(nullable = true) String ordinaryYn,
            @Schema(nullable = true) String name,
            @Schema(nullable = true) String baseYear,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000",
                            nullable = true)
                    String projectBudget,
            @NotBlank
                    @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000",
                            description = "예정·지급금액을 제외한 당해 요청금액")
                    String currentRequestAmount,
            @NotNull @Valid CodeLabel editType,
            @NotNull @Valid CodeLabel progressStatus,
            @JsonFormat(
                            shape = JsonFormat.Shape.STRING,
                            pattern = "uuuu-MM-dd",
                            lenient = OptBoolean.FALSE)
                    @Schema(type = "string", format = "date", nullable = true)
                    LocalDate startDate,
            @JsonFormat(
                            shape = JsonFormat.Shape.STRING,
                            pattern = "uuuu-MM-dd",
                            lenient = OptBoolean.FALSE)
                    @Schema(type = "string", format = "date", nullable = true)
                    LocalDate endDate,
            @JsonFormat(
                            shape = JsonFormat.Shape.STRING,
                            pattern = "uuuu-MM-dd",
                            lenient = OptBoolean.FALSE)
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
                    @Schema(
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000",
                            nullable = true)
                    String assetBudget,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000",
                            nullable = true)
                    String costBudget,
            @NotNull List<@NotNull @Valid ProjectItem> items) {}

    @Schema(name = "ItBudgetSnapshotTerminal", description = "전산업무비 스냅샷 단말기")
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
                    @Schema(
                            type = "string",
                            pattern = EXCHANGE_RATE_PATTERN,
                            example = "1.0000",
                            nullable = true)
                    String exchangeRate,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000",
                            nullable = true)
                    String foreignAmount,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000",
                            nullable = true)
                    String budgetAmount) {}

    @Schema(name = "ItBudgetSnapshotCost", description = "전산업무비 원장 기반 스냅샷")
    public record Cost(
            @NotBlank String id,
            @Positive int revision,
            @Schema(nullable = true) String baseYear,
            @Schema(nullable = true) String name,
            @Schema(nullable = true) String counterparty,
            @NotNull @Valid CodeLabel business,
            @NotNull @Valid CodeLabel budgetType,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000",
                            nullable = true)
                    String totalAmount,
            @Schema(nullable = true) String currency,
            @Pattern(regexp = EXCHANGE_RATE_PATTERN)
                    @Schema(
                            type = "string",
                            pattern = EXCHANGE_RATE_PATTERN,
                            example = "1.0000",
                            nullable = true)
                    String exchangeRate,
            @JsonFormat(
                            shape = JsonFormat.Shape.STRING,
                            pattern = "uuuu-MM-dd",
                            lenient = OptBoolean.FALSE)
                    @Schema(type = "string", format = "date", nullable = true)
                    LocalDate exchangeRateBaseDate,
            @NotNull @Valid CodeLabel deferralType,
            @JsonFormat(
                            shape = JsonFormat.Shape.STRING,
                            pattern = "uuuu-MM-dd",
                            lenient = OptBoolean.FALSE)
                    @Schema(type = "string", format = "date", nullable = true)
                    LocalDate firstDeferralDate,
            @Schema(nullable = true) String reason,
            @NotNull @Valid Organization supervisingDepartment,
            @NotNull @Valid Person manager,
            @Schema(nullable = true) String securitySystemUseYn,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000",
                            nullable = true)
                    String assetBudget,
            @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000",
                            nullable = true)
                    String costBudget,
            @NotNull List<@NotNull @Valid Terminal> terminals) {}

    @Schema(name = "ItBudgetSnapshotSummary", description = "스냅샷 금액 요약")
    public record Summary(
            @NotBlank
                    @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000")
                    String total,
            @NotBlank
                    @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000")
                    String asset,
            @NotBlank
                    @Pattern(regexp = MONEY_PATTERN)
                    @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            type = "string",
                            pattern = MONEY_PATTERN,
                            example = "0.000")
                    String cost) {}

    @Schema(name = "ItBudgetSnapshotPayload", description = "사업·전산업무비와 금액 요약")
    public record Payload(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid Project> projects,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotNull @Valid Cost> costs,
            @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Summary summary) {}

    @Schema(name = "ItBudgetSnapshotSource", description = "스냅샷 무결성 대상 원장")
    public record SnapshotSource(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SourceKind kind,
            @NotBlank @Size(max = 30) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int order,
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String digest) {}

    @Schema(name = "ItBudgetSnapshotIntegrity", description = "스냅샷 무결성 메타데이터")
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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(name = "ItBudgetApprovalErrorResponse", description = "전산예산 결재 오류 응답")
    public record ErrorResponse(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime timestamp,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
            List<@NotNull @Valid ChangedSource> changedSources) {}

    @Schema(name = "ItBudgetChangedSource", description = "미리보기 이후 변경된 전산예산 원장")
    public record ChangedSource(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SourceKind kind,
            @NotBlank @Size(max = 30) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modifiedBy,
            @Schema(
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true,
                            description = "원장 물리 삭제 등으로 실제 수정 시각을 확인할 수 없으면 null")
                    LocalDateTime modifiedAt) {}
}
