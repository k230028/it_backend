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
        FAILED,
        /**
         * 반입 대상 시트가 없어 건너뜀 — 실패가 아닙니다.
         *
         * <p>부점 폴더 아래에 팀·사업 폴더가 더 있는 구조에서는 편성요청서가 아닌 엑셀이 함께 올라옵니다. 그것을 실패로 부르면 관리자가 고칠 수 없는 붉은 줄이
         * 결과 표를 채웁니다. 차단 건수·반영 건수 어디에도 세지 않습니다.
         */
        SKIPPED
    }

    /**
     * 업로드한 파일 1건의 부가 정보입니다.
     *
     * @param fileKey 브라우저 `webkitRelativePath`. 파일 파트와 결과를 잇는 키
     * @param deptName 최상위 폴더명 원문. 부점은 `부서명(부서코드)`로 만들며 서버가 병기된 코드를 우선 기준으로 삼습니다
     * @param deptCodeOverride 미리보기에서 사용자가 고른 부서코드. 없으면 null
     * @param generalExpenseUnit 시트 ③ 금액 기재 단위. 없으면 서버가 제안값을 씁니다
     * @param bgUntAbusC 시트 ③ 사업코드. 양식에 없어 사용자가 지정합니다. 없으면 null
     * @param archiveOnly true면 원장 파싱 없이 같은 폴더의 반입 원본으로만 보관합니다. 생략하거나 null이면 false로 봅니다
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
                    String bgUntAbusC,
            @Schema(description = "보관 전용 파일 여부", requiredMode = Schema.RequiredMode.REQUIRED)
                    Boolean archiveOnly) {

        /**
         * 보관 전용 여부가 빠진 manifest도 받습니다.
         *
         * <p>원시 {@code boolean}으로 두면 이 항목이 없는 요청이 Jackson 역직렬화 단계에서 통째로 400이 되어, 관리자에게는 파일 하나도 짚어주지
         * 못하는 오류만 남습니다. 서버는 {@code RequestFormParseTarget}으로 파싱 대상 여부를 어차피 다시 판정하므로 이 값은 클라이언트가 주는
         * 힌트일 뿐이라 없으면 false로 접어도 결과가 달라지지 않습니다.
         */
        public FileEntry {
            if (archiveOnly == null) {
                archiveOnly = Boolean.FALSE;
            }
        }

        /** 기존 내부 호출은 모두 반입 대상 파일입니다. */
        public FileEntry(
                String fileKey,
                String deptName,
                String deptCodeOverride,
                AmountUnit generalExpenseUnit,
                String bgUntAbusC) {
            this(fileKey, deptName, deptCodeOverride, generalExpenseUnit, bgUntAbusC, false);
        }
    }

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
     * @param subject 진단이 가리키는 대상의 이름 (사업명·품목명·계약명). 파일·시트 단위 진단은 null
     * @param code 진단 코드
     * @param severity 심각도. `code.severity()`와 항상 같습니다
     * @param message 사용자 문구
     * @param candidates 보정 후보. 없으면 빈 목록
     * @param decision 해소에 필요한 입력 종류. 화면의 `결정` 열이 이 값으로 위젯을 고릅니다
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
            @Schema(
                            description = "대상 이름 (사업명·품목명·계약명)",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    String subject,
            @Schema(description = "진단 코드", requiredMode = Schema.RequiredMode.REQUIRED)
                    RequestFormDiagnosticCode code,
            @Schema(description = "심각도", requiredMode = Schema.RequiredMode.REQUIRED)
                    MigrationDto.Severity severity,
            @Schema(description = "사용자 문구", requiredMode = Schema.RequiredMode.REQUIRED)
                    String message,
            @Schema(description = "보정 후보", requiredMode = Schema.RequiredMode.REQUIRED)
                    List<MigrationDto.Candidate> candidates,
            @Schema(description = "해소에 필요한 입력 종류", requiredMode = Schema.RequiredMode.REQUIRED)
                    RequestFormDecisionKind decision) {

        /**
         * 대상 이름 없이 진단을 만듭니다. 파일·시트 단위 진단에 씁니다.
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
            return about(sheet, excelRow, field, null, code, message, candidates);
        }

        /**
         * 코드의 심각도를 따라 진단을 만듭니다.
         *
         * <p>대상 이름을 함께 담습니다. 한 파일이 사업·품목·계약을 수십 건 만들기 때문에, 같은 문구의 진단이 여러 줄 늘어서면 어느 것을 고쳐야 하는지 알 수
         * 없습니다(실측: `품목 비목코드가 비어 있습니다`가 구분 없이 3줄).
         *
         * @param sheet 대상 시트 (null 허용)
         * @param excelRow 엑셀 행 번호 (null 허용)
         * @param field 필드 id (null 허용)
         * @param subject 대상 이름 (null·공백이면 담지 않습니다)
         * @param code 진단 코드
         * @param message 사용자 문구
         * @param candidates 보정 후보. null이면 빈 목록으로 접습니다
         * @return 진단
         */
        public static FormDiagnostic about(
                FormSheetKind sheet,
                Integer excelRow,
                String field,
                String subject,
                RequestFormDiagnosticCode code,
                String message,
                List<MigrationDto.Candidate> candidates) {
            return decide(
                    sheet,
                    excelRow,
                    field,
                    subject,
                    code,
                    message,
                    candidates,
                    candidates == null || candidates.isEmpty()
                            ? RequestFormDecisionKind.NONE
                            : RequestFormDecisionKind.SELECT);
        }

        /**
         * 해소 입력 종류를 직접 지정해 진단을 만듭니다.
         *
         * <p>후보 목록이 없어도 사람이 고칠 수 있는 진단(사업명 입력·완료기한 입력·금액 단위 지정)에 씁니다. 지정한 종류의 보정값을 서버가 실제로 읽는지 확인하고
         * 지정하십시오 — 화면에만 입력칸이 생기고 서버가 무시하면 같은 진단이 되풀이됩니다.
         *
         * @param sheet 대상 시트 (null 허용)
         * @param excelRow 엑셀 행 번호 (null 허용)
         * @param field 필드 id (null 허용)
         * @param subject 대상 이름 (null·공백이면 담지 않습니다)
         * @param code 진단 코드
         * @param message 사용자 문구
         * @param candidates 보정 후보. null이면 빈 목록으로 접습니다
         * @param decision 해소에 필요한 입력 종류
         * @return 진단
         */
        public static FormDiagnostic decide(
                FormSheetKind sheet,
                Integer excelRow,
                String field,
                String subject,
                RequestFormDiagnosticCode code,
                String message,
                List<MigrationDto.Candidate> candidates,
                RequestFormDecisionKind decision) {
            return new FormDiagnostic(
                    sheet,
                    excelRow,
                    field,
                    subject == null || subject.isBlank() ? null : subject.trim(),
                    code,
                    code.severity(),
                    message,
                    candidates == null ? List.of() : List.copyOf(candidates),
                    decision);
        }
    }

    /**
     * 생성된 원장 1건입니다.
     *
     * @param table 원천테이블명 (`BPROJM` 또는 `BCOSTM`)
     * @param key 관리번호
     * @param label 표시명
     * @param apfMngNo 이 원장에 붙인 반입 받이 신청서번호. 결재현황에서 반입 원본 파일을 찾는 키입니다
     */
    @Schema(name = "RequestFormCreatedRecord", description = "생성된 원장")
    public record CreatedRecord(
            @Schema(description = "원천테이블명", requiredMode = Schema.RequiredMode.REQUIRED)
                    String table,
            @Schema(description = "관리번호", requiredMode = Schema.RequiredMode.REQUIRED) String key,
            @Schema(description = "표시명", requiredMode = Schema.RequiredMode.REQUIRED) String label,
            @Schema(description = "반입 받이 신청서번호", requiredMode = Schema.RequiredMode.REQUIRED)
                    String apfMngNo) {}

    /**
     * 원장 종류별 건수입니다.
     *
     * <p>사업(`BPROJM`)은 한 테이블이지만 화면에서는 정보화사업과 경상사업을 따로 셉니다 — 두 시트에서 오고 담당자가 보는 단위도 다릅니다.
     * 상시운영여부(`ODN_YN`)로 가릅니다.
     *
     * <p>이 건수는 {@code created}와 달리 <b>dry-run과 차단된 파일에도 채웁니다</b>. 사전검증의 목적이 "차단을 풀면 무엇이 몇 건 생기는가"를
     * 미리 보는 것인데, 생성된 원장 목록은 두 경우 모두 비어 있어 화면이 항상 0건으로 보입니다. 파일 단위 표기는 "이 파일에 무엇이 들어 있나"이고, 실제로 반영되는
     * 합계는 {@link ImportSummary#created()}가 따로 셉니다.
     *
     * @param capitalProjects 정보화사업 수 (시트 ①)
     * @param recurringProjects 경상사업 수 (시트 ②)
     * @param costs 전산업무비 수 (시트 ③)
     */
    @Schema(name = "RequestFormRecordCounts", description = "원장 종류별 건수")
    public record RecordCounts(
            @Schema(description = "정보화사업 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int capitalProjects,
            @Schema(description = "경상사업 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int recurringProjects,
            @Schema(description = "전산업무비 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int costs) {

        /** 아무것도 만들지 않는 건수입니다. */
        public static RecordCounts zero() {
            return new RecordCounts(0, 0, 0);
        }

        /**
         * 두 건수를 더합니다. 배치 요약을 만들 때 씁니다.
         *
         * @param other 더할 건수
         * @return 합계
         */
        public RecordCounts plus(RecordCounts other) {
            return new RecordCounts(
                    capitalProjects + other.capitalProjects,
                    recurringProjects + other.recurringProjects,
                    costs + other.costs);
        }
    }

    /**
     * 파일 1건의 처리 결과입니다.
     *
     * @param fileKey 파일 상대경로
     * @param deptName 부서명
     * @param status 반영 상태
     * @param diagnostics 진단 목록. 없으면 빈 목록
     * @param created 생성된 원장. dry-run이거나 반영하지 않았으면 빈 목록
     * @param counts 파일에서 읽어낸 원장 종류별 건수. 차단된 파일도 채우므로 실제 반영 여부는 `status`로 판단합니다
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
            @Schema(description = "원장 종류별 건수", requiredMode = Schema.RequiredMode.REQUIRED)
                    RecordCounts counts,
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
     * @param created 반영된 파일들의 원장 종류별 합계. dry-run이면 반영 시 생길 합계
     * @param archiveFailedFileKeys 원본 보관에 실패한 파일의 안전한 상대 식별자 목록
     */
    @Schema(name = "RequestFormImportSummary", description = "반입 배치 요약")
    public record ImportSummary(
            @Schema(description = "보낸 파일 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int totalFiles,
            @Schema(description = "반영된 파일 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int appliedFiles,
            @Schema(description = "차단된 파일 수", requiredMode = Schema.RequiredMode.REQUIRED)
                    int blockedFiles,
            @Schema(description = "원장 종류별 합계", requiredMode = Schema.RequiredMode.REQUIRED)
                    RecordCounts created,
            @Schema(description = "원본 보관 실패 파일 상대경로", requiredMode = Schema.RequiredMode.REQUIRED)
                    List<String> archiveFailedFileKeys) {

        public ImportSummary {
            archiveFailedFileKeys =
                    archiveFailedFileKeys == null ? List.of() : List.copyOf(archiveFailedFileKeys);
        }

        /** 기존 내부 호출부와의 호환을 위한 원본 보관 실패 목록 없는 생성자입니다. */
        public ImportSummary(
                int totalFiles, int appliedFiles, int blockedFiles, RecordCounts created) {
            this(totalFiles, appliedFiles, blockedFiles, created, List.of());
        }
    }

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
