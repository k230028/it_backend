package com.kdb.it.domain.migration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/** 수기 엑셀 이관 요청·응답 계약입니다. */
public final class MigrationDto {

    private MigrationDto() {
        throw new UnsupportedOperationException("계약 컨테이너 — 인스턴스화 금지");
    }

    /** 진단 심각도. BLOCKER가 하나라도 남으면 반영을 거부합니다. */
    @Schema(name = "MigrationSeverity", description = "진단 심각도")
    public enum Severity {
        BLOCKER,
        WARNING
    }

    /**
     * 정규화된 엑셀 한 행입니다.
     *
     * @param excelRow 엑셀 사용자 관점 행 번호 (헤더 다음 행이 2)
     * @param cells 정규 컬럼 id → 셀 문자열. 빈 셀은 빈 문자열이며 키를 생략하지 않습니다
     */
    @Schema(name = "MigrationNormalizedRow", description = "정규화된 엑셀 행")
    public record NormalizedRow(
            @Schema(description = "엑셀 행 번호", example = "2", requiredMode = RequiredMode.REQUIRED)
                    int excelRow,
            @Schema(description = "정규 컬럼 id → 셀 문자열", requiredMode = RequiredMode.REQUIRED) @NotNull
                    Map<String, String> cells) {}

    /**
     * 시트 하나의 전송 단위입니다.
     *
     * @param kind 시트 종류
     * @param bseYy 예산연도 4자리
     * @param rows 정규화 행 목록 (비어 있지 않음)
     */
    @Schema(name = "MigrationSheetPayload", description = "시트 단위 전송 페이로드")
    public record SheetPayload(
            @Schema(description = "시트 종류", requiredMode = RequiredMode.REQUIRED) @NotNull
                    SheetKind kind,
            @Schema(description = "예산연도", example = "2026", requiredMode = RequiredMode.REQUIRED)
                    @Pattern(regexp = "\\d{4}", message = "예산연도는 4자리 숫자입니다.")
                    String bseYy,
            @Schema(description = "정규화 행 목록", requiredMode = RequiredMode.REQUIRED) @NotEmpty @Valid
                    List<NormalizedRow> rows) {}

    /**
     * dry-run 요청입니다.
     *
     * @param sheets 올린 시트 목록 (1~4개)
     */
    @Schema(name = "MigrationDryRunRequest", description = "이관 사전검증 요청")
    public record DryRunRequest(
            @Schema(description = "시트 목록", requiredMode = RequiredMode.REQUIRED) @NotEmpty @Valid
                    List<SheetPayload> sheets) {}

    /**
     * 해석 후보입니다.
     *
     * @param code 저장할 코드값 (부서코드·사번·비목코드 등)
     * @param label 사용자에게 보여줄 이름
     */
    @Schema(name = "MigrationCandidate", description = "해석 후보")
    public record Candidate(
            @Schema(description = "코드값", example = "0210", requiredMode = RequiredMode.REQUIRED)
                    String code,
            @Schema(description = "표시명", example = "IT기획부", requiredMode = RequiredMode.REQUIRED)
                    String label) {}

    /**
     * 셀 단위 진단입니다.
     *
     * @param sheet 시트 종류
     * @param excelRow 엑셀 행 번호
     * @param column 정규 컬럼 id. 행 전체에 걸린 진단은 null
     * @param code 진단 코드 (ORG_UNRESOLVED 등)
     * @param severity 심각도
     * @param message 사용자 문구
     * @param candidates 보정 후보. 후보가 없으면 빈 목록
     */
    @Schema(name = "MigrationCellDiagnostic", description = "셀 단위 진단")
    public record CellDiagnostic(
            @Schema(description = "시트 종류", requiredMode = RequiredMode.REQUIRED) SheetKind sheet,
            @Schema(description = "엑셀 행 번호", example = "2", requiredMode = RequiredMode.REQUIRED)
                    int excelRow,
            @Schema(
                            description = "정규 컬럼 id (행 단위 진단은 null)",
                            example = "deptName",
                            requiredMode = RequiredMode.REQUIRED,
                            nullable = true)
                    String column,
            @Schema(
                            description = "진단 코드",
                            requiredMode = RequiredMode.REQUIRED,
                            allowableValues = {
                                "ORG_UNRESOLVED", "ORG_AMBIGUOUS", "USER_UNRESOLVED",
                                "USER_AMBIGUOUS", "CODE_UNRESOLVED", "REQUIRED_MISSING",
                                "DUPLICATE_EXISTS", "LENGTH_EXCEEDED", "PROJECT_NOT_FOUND",
                                "AMOUNT_MISMATCH", "RATE_OUT_OF_RANGE", "DATE_UNPARSEABLE"
                            })
                    String code,
            @Schema(description = "심각도", requiredMode = RequiredMode.REQUIRED) Severity severity,
            @Schema(description = "사용자 문구", requiredMode = RequiredMode.REQUIRED) String message,
            @Schema(description = "보정 후보", requiredMode = RequiredMode.REQUIRED)
                    List<Candidate> candidates) {}

    /**
     * dry-run 요약입니다.
     *
     * @param totalRows 검증한 전체 행 수
     * @param blockerCount BLOCKER 진단 수
     * @param warningCount WARNING 진단 수
     */
    @Schema(name = "MigrationSummary", description = "사전검증 요약")
    public record Summary(
            @Schema(description = "전체 행 수", requiredMode = RequiredMode.REQUIRED) int totalRows,
            @Schema(description = "BLOCKER 수", requiredMode = RequiredMode.REQUIRED)
                    int blockerCount,
            @Schema(description = "WARNING 수", requiredMode = RequiredMode.REQUIRED)
                    int warningCount) {}

    /**
     * dry-run 응답입니다.
     *
     * @param diagnostics 진단 목록. 문제가 없으면 빈 목록
     * @param summary 요약
     */
    @Schema(name = "MigrationDryRunResponse", description = "이관 사전검증 응답")
    public record DryRunResponse(
            @Schema(description = "진단 목록", requiredMode = RequiredMode.REQUIRED)
                    List<CellDiagnostic> diagnostics,
            @Schema(description = "요약", requiredMode = RequiredMode.REQUIRED) Summary summary) {}

    /**
     * 사용자가 미리보기에서 보정한 셀 하나입니다.
     *
     * @param sheet 시트 종류
     * @param excelRow 엑셀 행 번호
     * @param column 정규 컬럼 id
     * @param value 보정 값 (코드값)
     */
    @Schema(name = "MigrationCellOverride", description = "미리보기 보정값")
    public record CellOverride(
            @Schema(description = "시트 종류", requiredMode = RequiredMode.REQUIRED) @NotNull
                    SheetKind sheet,
            @Schema(description = "엑셀 행 번호", example = "2", requiredMode = RequiredMode.REQUIRED)
                    int excelRow,
            @Schema(
                            description = "정규 컬럼 id",
                            example = "ioeName",
                            requiredMode = RequiredMode.REQUIRED)
                    @NotNull
                    String column,
            @Schema(description = "보정 코드값", example = "008", requiredMode = RequiredMode.REQUIRED)
                    @NotNull
                    String value) {}

    /**
     * 확정 반영 요청입니다.
     *
     * @param sheets 시트 목록 (dry-run과 같은 내용)
     * @param overrides 보정값 목록. 없으면 빈 목록
     */
    @Schema(name = "MigrationCommitRequest", description = "이관 확정 반영 요청")
    public record CommitRequest(
            @Schema(description = "시트 목록", requiredMode = RequiredMode.REQUIRED) @NotEmpty @Valid
                    List<SheetPayload> sheets,
            @Schema(description = "보정값 목록", requiredMode = RequiredMode.REQUIRED) @NotNull @Valid
                    List<CellOverride> overrides) {}

    /**
     * 확정 반영 응답입니다.
     *
     * @param costCount 생성한 전산업무비 수
     * @param projectCount 생성한 사업 수
     * @param itemCount 생성한 품목 수
     * @param budgetRowCount applyItemRates가 만든 편성행 수
     * @param planReqDocNo 생성한 계획관리번호. 부문계획 시트를 올리지 않았으면 null
     * @param createdIds 생성한 관리번호 목록 (화면 표시용)
     */
    @Schema(name = "MigrationCommitResponse", description = "이관 확정 반영 응답")
    public record CommitResponse(
            @Schema(description = "생성 전산업무비 수", requiredMode = RequiredMode.REQUIRED) int costCount,
            @Schema(description = "생성 사업 수", requiredMode = RequiredMode.REQUIRED) int projectCount,
            @Schema(description = "생성 품목 수", requiredMode = RequiredMode.REQUIRED) int itemCount,
            @Schema(description = "생성 편성행 수", requiredMode = RequiredMode.REQUIRED)
                    int budgetRowCount,
            @Schema(
                            description = "생성 계획관리번호",
                            example = "PLN-2026-0001",
                            requiredMode = RequiredMode.REQUIRED,
                            nullable = true)
                    String planReqDocNo,
            @Schema(description = "생성 관리번호 목록", requiredMode = RequiredMode.REQUIRED)
                    List<String> createdIds) {}
}
