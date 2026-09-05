package com.kdb.it.common.approval.itbudget.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** 전산예산 미리보기·상신 경계에서 사용하는 고정 HTTP 계약이다. */
public final class ItBudgetApprovalDto {

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
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ApproverRole role,
            @NotBlank @Size(max = 14) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String eno) {}

    @Schema(name = "ItBudgetDocumentRequest", description = "미리보기 또는 상신 문서 단위 입력")
    public record DocumentRequest(
            @NotBlank @Size(max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String clientDocumentKey,
            @NotEmpty @Size(max = 500) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@Valid SourceRef> sources) {}

    @Schema(name = "ItBudgetPreviewRequest", description = "전산예산 결재 미리보기 요청")
    public record PreviewRequest(
            @NotEmpty @Size(max = 102) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@Valid ApproverRef> approvers,
            @NotEmpty @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@Valid DocumentRequest> documents) {}

    @Schema(name = "ItBudgetSourceDigest", description = "미리보기 시점 원장 다이제스트")
    public record SourceDigest(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SourceKind kind,
            @NotBlank @Size(max = 30) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int order,
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String digest) {}

    @Schema(name = "ItBudgetPreviewDocument", description = "서버가 생성한 전산예산 미리보기 문서")
    public record PreviewDocument(
            @NotBlank @Size(max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String clientDocumentKey,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) JsonNode snapshot,
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String payloadDigest,
            @NotEmpty @Size(max = 500) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@Valid SourceDigest> sources) {}

    @Schema(name = "ItBudgetPreviewResponse", description = "전산예산 결재 미리보기 응답")
    public record PreviewResponse(
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String previewDigest,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String previewToken,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
            @NotEmpty @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@Valid PreviewDocument> documents) {}

    @Schema(name = "ItBudgetSubmissionDocument", description = "전산예산 상신 문서별 다이제스트")
    public record SubmissionDocument(
            @NotBlank @Size(max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String clientDocumentKey,
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String payloadDigest,
            @NotEmpty @Size(max = 500) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@Valid SourceDigest> sources) {}

    @Schema(name = "ItBudgetSubmissionRequest", description = "전산예산 결재 상신 요청")
    public record SubmissionRequest(
            @NotBlank @Size(min = 64, max = 64) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String previewDigest,
            @NotBlank @Size(max = 8192) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String previewToken,
            @NotEmpty @Size(max = 102) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@Valid ApproverRef> approvers,
            @NotEmpty @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@Valid SubmissionDocument> documents) {}

    @Schema(name = "ItBudgetSubmissionResponse", description = "전산예산 결재 상신 결과")
    public record SubmissionResponse(
            @NotEmpty @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    List<@NotBlank String> applicationNumbers) {}

    @Schema(name = "ItBudgetChangedSource", description = "미리보기 이후 변경된 전산예산 원장")
    public record ChangedSource(
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int no,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SourceKind kind,
            @NotBlank @Size(max = 30) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String businessName,
            @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modifier,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    LocalDateTime modifiedAt) {}
}
