package com.kdb.it.domain.migration.request.dto;

import com.kdb.it.domain.migration.dto.MigrationDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/** 부점 편성요청서 반입 요청·응답 계약입니다. */
public final class RequestFormDto {

    private RequestFormDto() {
        throw new UnsupportedOperationException("계약 컨테이너 — 인스턴스화 금지");
    }

    /** 파일 1건의 반영 결과 상태입니다. */
    @Schema(name = "RequestFormFileStatus", description = "파일 반영 상태")
    public enum FileStatus {
        /** 원장에 반영됨 */
        APPLIED,
        /** BLOCKER 진단이 남아 반영하지 않음 */
        BLOCKED,
        /** 예상하지 못한 오류로 실패 (해당 파일만 롤백) */
        FAILED
    }

    /**
     * 업로드한 파일 1건의 부가 정보입니다.
     *
     * @param fileKey 브라우저 `webkitRelativePath`. 파일 파트와 결과를 잇는 키
     * @param deptName 최상위 폴더명에서 뽑은 부서명
     * @param deptCodeOverride 미리보기에서 사용자가 고른 부서코드. 없으면 null
     * @param generalExpenseUnit 시트 ③ 금액 기재 단위. 없으면 서버가 제안값을 씁니다
     * @param bgUntAbusC 시트 ③ 사업코드. 양식에 없어 사용자가 지정합니다. 없으면 null
     */
    @Schema(name = "RequestFormFileEntry", description = "업로드 파일 부가 정보")
    public record FileEntry(
            @Schema(description = "파일 상대경로", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                    String fileKey,
            @Schema(description = "폴더에서 뽑은 부서명", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank
                    String deptName,
            @Schema(
                            description = "사용자가 고른 부서코드",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    String deptCodeOverride,
            @Schema(
                            description = "시트 ③ 금액 기재 단위",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    AmountUnit generalExpenseUnit,
            @Schema(
                            description = "시트 ③ 사업코드",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    String bgUntAbusC) {}

    /**
     * 미리보기에서 사용자가 고친 셀 값입니다.
     *
     * @param fileKey 대상 파일
     * @param sheet 대상 시트. 파일 전체에 걸린 보정은 null
     * @param excelRow 엑셀 사용자 관점 행 번호(1-based). 행에 매이지 않는 보정은 null
     * @param field 보정 대상 필드 id (`ioeC`, `svnDpmC`, `abusNm` 등)
     * @param value 사용자가 고른 값
     */
    @Schema(name = "RequestFormCellOverride", description = "미리보기 보정값")
    public record CellOverride(
            @Schema(description = "파일 상대경로", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                    String fileKey,
            @Schema(
                            description = "시트 종류",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    FormSheetKind sheet,
            @Schema(
                            description = "엑셀 행 번호",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    Integer excelRow,
            @Schema(description = "필드 id", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                    String field,
            @Schema(description = "보정값", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                    String value) {}

    /**
     * 배치 1회의 메타데이터입니다. multipart의 `manifest` 파트로 보냅니다.
     *
     * @param bseYy 예산연도 4자리. 화면에서 사용자가 고른 값
     * @param entries 파일별 부가 정보
     * @param overrides 보정값. 최초 dry-run은 빈 목록
     */
    @Schema(name = "RequestFormImportManifest", description = "반입 배치 메타데이터")
    public record ImportManifest(
            @Schema(
                            description = "예산연도",
                            example = "2026",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull(message = "예산연도는 필수입니다.")
                    @Pattern(regexp = "\\d{4}", message = "예산연도는 4자리 숫자입니다.")
                    String bseYy,
            @Schema(description = "파일별 부가 정보", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotEmpty
                    @Valid
                    List<FileEntry> entries,
            @Schema(description = "보정값 목록", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull
                    @Valid
                    List<CellOverride> overrides) {}

    /**
     * 진단 1건입니다.
     *
     * @param sheet 대상 시트. 파일 단위 진단은 null
     * @param excelRow 엑셀 행 번호(1-based). 행에 매이지 않으면 null
     * @param field 대상 필드 id. 필드에 매이지 않으면 null
     * @param code 진단 코드
     * @param severity 심각도. `code.severity()`와 항상 같습니다
     * @param message 사용자 문구
     * @param candidates 보정 후보. 없으면 빈 목록
     */
    @Schema(name = "RequestFormDiagnostic", description = "편성요청서 반입 진단")
    public record FormDiagnostic(
            @Schema(
                            description = "시트 종류",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    FormSheetKind sheet,
            @Schema(
                            description = "엑셀 행 번호",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    Integer excelRow,
            @Schema(
                            description = "필드 id",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    String field,
            @Schema(description = "진단 코드", requiredMode = Schema.RequiredMode.REQUIRED)
                    RequestFormDiagnosticCode code,
            @Schema(description = "심각도", requiredMode = Schema.RequiredMode.REQUIRED)
                    MigrationDto.Severity severity,
            @Schema(description = "사용자 문구", requiredMode = Schema.RequiredMode.REQUIRED)
                    String message,
            @Schema(description = "보정 후보", requiredMode = Schema.RequiredMode.REQUIRED)
                    List<MigrationDto.Candidate> candidates) {

        /**
         * 코드의 심각도를 따라 진단을 만듭니다.
         *
         * @param sheet 대상 시트 (null 허용)
         * @param excelRow 엑셀 행 번호 (null 허용)
         * @param field 필드 id (null 허용)
         * @param code 진단 코드
         * @param message 사용자 문구
         * @param candidates 보정 후보. null이면 빈 목록으로 접습니다
         * @return 진단
         */
        public static FormDiagnostic of(
                FormSheetKind sheet,
                Integer excelRow,
                String field,
                RequestFormDiagnosticCode code,
                String message,
                List<MigrationDto.Candidate> candidates) {
            return new FormDiagnostic(
                    sheet,
                    excelRow,
                    field,
                    code,
                    code.severity(),
                    message,
                    candidates == null ? List.of() : List.copyOf(candidates));
        }
    }

    /**
     * 생성된 원장 1건입니다.
     *
     * @param table 원천테이블명 (`BPROJM`·`BITEMM`·`BCOSTM`)
     * @param key 생성된 관리번호
     * @param label 화면 표시명 (사업명·계약명)
     */
    @Schema(name = "RequestFormCreatedRecord", description = "생성된 원장")
    public record CreatedRecord(
            @Schema(description = "원천테이블명", requiredMode = Schema.RequiredMode.REQUIRED)
                    String table,
            @Schema(description = "관리번호", requiredMode = Schema.RequiredMode.REQUIRED) String key,
            @Schema(description = "표시명", requiredMode = Schema.RequiredMode.REQUIRED)
                    String label) {}

    /**
     * 파일 1건의 처리 결과입니다.
     *
     * @param fileKey 파일 상대경로
     * @param deptName 부서명
     * @param status 반영 상태
     * @param diagnostics 진단 목록. 없으면 빈 목록
     * @param created 생성된 원장. dry-run이거나 반영하지 않았으면 빈 목록
     * @param suggestedGeneralExpenseUnit 시트 ③ 단위 제안값. 시트 ③이 없으면 null
     */
    @Schema(name = "RequestFormFileResult", description = "파일 처리 결과")
    public record FileResult(
            @Schema(description = "파일 상대경로", requiredMode = Schema.RequiredMode.REQUIRED)
                    String fileKey,
            @Schema(description = "부서명", requiredMode = Schema.RequiredMode.REQUIRED)
                    String deptName,
            @Schema(description = "반영 상태", requiredMode = Schema.RequiredMode.REQUIRED)
                    FileStatus status,
            @Schema(description = "진단 목록", requiredMode = Schema.RequiredMode.REQUIRED)
                    List<FormDiagnostic> diagnostics,
            @Schema(description = "생성된 원장", requiredMode = Schema.RequiredMode.REQUIRED)
                    List<CreatedRecord> created,
            @Schema(
                            description = "시트 ③ 단위 제안값",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    AmountUnit suggestedGeneralExpenseUnit) {}

    /**
     * 배치 요약입니다.
     *
     * @param totalFiles 보낸 파일 수
     * @param appliedFiles 반영된 파일 수. dry-run이면 반영 가능한 파일 수
     * @param blockedFiles BLOCKER가 남은 파일 수
     * @param createdProjects 생성된 사업 수
     * @param createdItems 생성된 품목 수
     * @param createdCosts 생성된 전산업무비 수
     */
    @Schema(name = "RequestFormImportSummary", description = "반입 배치 요약")
    public record ImportSummary(
            @Schema(description = "보낸 파일 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int totalFiles,
            @Schema(description = "반영된 파일 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int appliedFiles,
            @Schema(description = "차단된 파일 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int blockedFiles,
            @Schema(description = "생성된 사업 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int createdProjects,
            @Schema(description = "생성된 품목 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int createdItems,
            @Schema(description = "생성된 전산업무비 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int createdCosts) {}

    /**
     * 반입 응답입니다. dry-run과 commit이 같은 형태를 씁니다.
     *
     * @param dryRun 사전검증이면 true
     * @param summary 배치 요약
     * @param files 파일별 결과
     */
    @Schema(name = "RequestFormImportResponse", description = "편성요청서 반입 응답")
    public record ImportResponse(
            @Schema(description = "사전검증 여부", requiredMode = Schema.RequiredMode.REQUIRED)
                    boolean dryRun,
            @Schema(description = "배치 요약", requiredMode = Schema.RequiredMode.REQUIRED)
                    ImportSummary summary,
            @Schema(description = "파일별 결과", requiredMode = Schema.RequiredMode.REQUIRED)
                    List<FileResult> files) {}
}
