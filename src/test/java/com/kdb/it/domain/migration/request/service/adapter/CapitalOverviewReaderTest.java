package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 1-1 개요 시트의 값 해석 분기를 시트를 직접 만들어 확인합니다.
 *
 * <p>공용 픽스처는 실 제출본 레이아웃 하나를 재현한 것이라 "주관부서/팀이 아예 없는 파일", "날짜 표기가 깨진 파일" 같은 변형을 담지 못합니다. 이 테스트는 필요한
 * 칸만 담은 시트를 그때그때 만들어 각 분기를 직접 밟습니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapitalOverviewReaderTest {

    @Mock private OrgIdentityResolver.Index orgIndex;

    private final SheetAnchorScanner scanner = new SheetAnchorScanner();
    private final CapitalOverviewReader reader =
            new CapitalOverviewReader(scanner, new FormLabelReader(scanner));

    /** 라벨-값 쌍만 담은 최소 1-1 시트를 만듭니다. 라벨은 C열, 값은 D열에 놓습니다. */
    private static Sheet overviewSheet(Map<String, String> labelToValue) {
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet("① (정보화사업) 1-1. 정보화사업 개요");
            int rowIndex = 0;
            for (Map.Entry<String, String> entry : labelToValue.entrySet()) {
                Row row = sheet.createRow(rowIndex++);
                cell(row, 2).setCellValue(entry.getKey());
                if (entry.getValue() != null) cell(row, 3).setCellValue(entry.getValue());
            }
            // 워크북을 닫으면 시트가 무효화되므로 바이트로 굽고 다시 연다
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return new HSSFWorkbook(new java.io.ByteArrayInputStream(out.toByteArray()))
                        .getSheetAt(0);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Cell cell(Row row, int colIndex) {
        Cell existing = row.getCell(colIndex);
        return existing == null ? row.createCell(colIndex) : existing;
    }

    private FormAdapterContext context(Map<String, String> overrides) {
        return new FormAdapterContext(
                new EnumMap<>(FormSheetKind.class),
                "2026",
                new RequestFormDto.FileEntry("부서/파일.xls", "폴더부서", null, null, null),
                "0999",
                "폴더부서",
                orgIndex,
                TestIoeIndex.snapshot(),
                overrides,
                "12345678");
    }

    @Test
    @DisplayName("주관부서/팀이 없으면 폴더명으로 확정한 부서를 쓴다")
    void fallsBackToFolderDepartment() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업"));

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(Map.of()), Map.of(), Map.of()).project();

        assertThat(project.getSvnDpmC()).isEqualTo("0999");
        assertThat(project.getSvnTemC()).isNull();
    }

    @Test
    @DisplayName("보정값이 있으면 조직 해석보다 우선한다")
    void organizationOverrideWins() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업", "주관부서/팀", "없는부서/없는팀"));
        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(
                                FormSheetKind.CAPITAL_OVERVIEW, null, "svnDpmC"),
                        "0210",
                        FormAdapterContext.overrideKey(
                                FormSheetKind.CAPITAL_OVERVIEW, null, "svnTemC"),
                        "02101");

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(overrides), Map.of(), Map.of()).project();

        assertThat(project.getSvnDpmC()).isEqualTo("0210");
        assertThat(project.getSvnTemC()).isEqualTo("02101");
    }

    @Test
    @DisplayName("조직이 중의적이면 후보를 담아 차단 진단을 낸다")
    void reportsAmbiguousOrganization() {
        when(orgIndex.resolveOrg(any()))
                .thenReturn(
                        new OrgIdentityResolver.Resolution(
                                null,
                                "여러부서",
                                List.of(new MigrationDto.Candidate("0210", "자금운용실")),
                                true));
        Sheet sheet = overviewSheet(Map.of("사업명", "사업", "주관부서/팀", "여러부서"));

        CapitalOverviewReader.Result result =
                reader.read(sheet, context(Map.of()), Map.of(), Map.of());

        assertThat(result.diagnostics())
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.ORG_AMBIGUOUS)
                .singleElement()
                .satisfies(d -> assertThat(d.candidates()).hasSize(1));
    }

    @Test
    @DisplayName("담당자 보정값이 있으면 사용자 해석을 건너뛴다")
    void userOverrideSkipsResolution() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업", "팀장", "없는사람"));
        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(
                                FormSheetKind.CAPITAL_OVERVIEW, null, "tlrUsid"),
                        "K140024");

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(overrides), Map.of(), Map.of()).project();

        assertThat(project.getTlrUsid()).isEqualTo("K140024");
    }

    @Test
    @DisplayName("날짜 표기를 해석하지 못하면 경고를 내고 기간을 비워 둔다")
    void reportsUnparseableDate() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업", "시작일자 (YY/MM)", "미정"));

        CapitalOverviewReader.Result result =
                reader.read(sheet, context(Map.of()), Map.of(), Map.of());

        assertThat(result.project().getSttDtm()).isNull();
        assertThat(result.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.DATE_UNPARSEABLE);
    }

    @Test
    @DisplayName("YY/MM 표기를 시작 1일·종료 말일로 바꾼다")
    void parsesPeriodBoundaries() {
        Sheet sheet =
                overviewSheet(
                        new java.util.LinkedHashMap<>(
                                Map.of(
                                        "사업명", "사업",
                                        "시작일자 (YY/MM)", "26/01",
                                        "종료일자 (YY/MM)", "26/02")));

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(Map.of()), Map.of(), Map.of()).project();

        assertThat(project.getSttDtm()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(project.getEndDtm()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("법규상 완료시기를 YYYYMMDD로 펴고 월만 있으면 말일로 채운다")
    void normalizesLegalDeadline() {
        assertThat(deadlineOf("2026.02")).isEqualTo("20260228");
        assertThat(deadlineOf("2026.02.15")).isEqualTo("20260215");
        assertThat(deadlineOf("미정")).isNull();
        assertThat(deadlineOf("2026.13")).isNull();
    }

    @Test
    @DisplayName("중복 여부 표기를 Y/N으로 접는다")
    void normalizesDuplicateFlag() {
        Sheet sheet =
                overviewSheet(new java.util.LinkedHashMap<>(Map.of("사업명", "사업", "중복 여부", "○")));

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(Map.of()), Map.of(), Map.of()).project();

        assertThat(project.getDplYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("추진가능성·전결권자 이름을 코드로 바꾼다")
    void mapsCodeCatalogs() {
        Sheet sheet =
                overviewSheet(
                        new java.util.LinkedHashMap<>(
                                Map.of("사업명", "사업", "추진가능성", "확정", "전결권자", "전무이사")));

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(Map.of()), Map.of("확정", "1"), Map.of("전무이사", "21"))
                        .project();

        assertThat(project.getExePttYn()).isEqualTo("1");
        assertThat(project.getEdrtTc()).isEqualTo("21");
    }

    @Test
    @DisplayName("전결권자를 코드표에서 못 찾으면 차단하되 보정으로 풀 수 있다")
    void delegationCanBeCorrectedByOverride() {
        Sheet sheet =
                overviewSheet(new java.util.LinkedHashMap<>(Map.of("사업명", "사업", "전결권자", "없는직위")));

        CapitalOverviewReader.Result blocked =
                reader.read(sheet, context(Map.of()), Map.of(), Map.of());
        assertThat(blocked.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.CODE_UNRESOLVED);

        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(
                                FormSheetKind.CAPITAL_OVERVIEW, null, "edrtTc"),
                        "21");
        CapitalOverviewReader.Result corrected =
                reader.read(sheet, context(overrides), Map.of(), Map.of());

        assertThat(corrected.project().getEdrtTc()).isEqualTo("21");
        assertThat(corrected.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.CODE_UNRESOLVED);
    }

    @Test
    @DisplayName("요약표가 없으면 대사 기준값을 비워 둔다")
    void leavesDeclaredTotalNullWhenSummaryAbsent() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업"));

        assertThat(reader.read(sheet, context(Map.of()), Map.of(), Map.of()).declaredYearTotal())
                .isNull();
    }

    private String deadlineOf(String raw) {
        Sheet sheet =
                overviewSheet(new java.util.LinkedHashMap<>(Map.of("사업명", "사업", "법규상 완료시기", raw)));
        return reader.read(sheet, context(Map.of()), Map.of(), Map.of()).project().getFlfFsgDt();
    }
}
