package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDecisionKind;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
            new CapitalOverviewReader(
                    scanner, new FormLabelReader(scanner), new FormCheckboxReader());

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
    @DisplayName("사업명의 개행을 공백으로 바꾼다")
    void replacesProjectNameLineBreaksWithSpaces() {
        Sheet sheet = overviewSheet(Map.of("사업명", "차세대\r\n정보계\n구축\r사업"));

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(Map.of()), FormCatalogs.empty()).project();

        assertThat(project.getAbusNm()).isEqualTo("차세대 정보계 구축 사업");
    }

    @Test
    @DisplayName("주관부서/팀이 없으면 폴더명으로 확정한 부서를 쓴다")
    void fallsBackToFolderDepartment() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업"));

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(Map.of()), FormCatalogs.empty()).project();

        assertThat(project.getSvnDpmC()).isEqualTo("0999");
        assertThat(project.getSvnTemC()).isNull();
    }

    @Test
    @DisplayName("시트에 적힌 부서명이 무엇이든 폴더 부서코드를 쓴다")
    void alwaysTakesDepartmentFromFolderCode() {
        // 조직 개편으로 낡은 부서명이 적혀 있어도 폴더 코드가 기준이라 흔들리지 않는다
        Sheet sheet = overviewSheet(Map.of("사업명", "사업", "주관부서/팀", "없는부서/없는팀"));
        when(orgIndex.parentOrgNameOf("0999")).thenReturn("어느부문");

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(Map.of()), FormCatalogs.empty()).project();

        assertThat(project.getSvnDpmC()).isEqualTo("0999");
        assertThat(project.getPrlmHrkOgzCCone()).isEqualTo("어느부문");
        assertThat(project.getSvnTemNm()).isEqualTo("없는팀");
        assertThat(project.getSvnTemC()).isNull();
    }

    @Test
    @DisplayName("주관부서/팀에 팀이 없으면 팀명을 비워 둔다")
    void leavesTeamNameNullWhenAbsent() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업", "주관부서/팀", "자금운용실"));

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(Map.of()), FormCatalogs.empty()).project();

        assertThat(project.getSvnTemNm()).isNull();
    }

    @Test
    @DisplayName("조직·담당자를 코드로 해석하지 않으므로 미해석 차단 진단을 내지 않는다")
    void neverBlocksOnOrganizationOrPerson() {
        Sheet sheet =
                overviewSheet(
                        new java.util.LinkedHashMap<>(
                                Map.of("사업명", "사업", "주관부서/팀", "없는부서/없는팀", "팀장", "없는사람")));

        CapitalOverviewReader.Result result =
                reader.read(sheet, context(Map.of()), FormCatalogs.empty());

        assertThat(result.project().getTlrUsid()).isEqualTo("없는사람");
        assertThat(result.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(
                        RequestFormDiagnosticCode.ORG_UNRESOLVED,
                        RequestFormDiagnosticCode.ORG_AMBIGUOUS,
                        RequestFormDiagnosticCode.USER_UNRESOLVED,
                        RequestFormDiagnosticCode.USER_AMBIGUOUS);
    }

    @Test
    @DisplayName("담당자 보정값이 있으면 시트 기재값보다 우선한다")
    void personOverrideWins() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업", "팀장", "없는사람"));
        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(
                                FormSheetKind.CAPITAL_OVERVIEW, null, "tlrUsid"),
                        "K140024");

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(overrides), FormCatalogs.empty()).project();

        assertThat(project.getTlrUsid()).isEqualTo("K140024");
    }

    @Test
    @DisplayName("담당자 이름이 컬럼 길이를 넘으면 잘라 담고 알린다")
    void truncatesOverlongPersonName() {
        // 영문 성명은 14자를 넘길 수 있다. 파일을 막는 대신 잘라 담는다
        Sheet sheet = overviewSheet(Map.of("사업명", "사업", "팀장", "Luke Buckingham-Brown"));

        CapitalOverviewReader.Result result =
                reader.read(sheet, context(Map.of()), FormCatalogs.empty());

        assertThat(result.project().getTlrUsid()).isEqualTo("Luke Buckingha").hasSize(14);
        assertThat(result.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.SUBSTITUTE_DROPPED);
    }

    @Test
    @DisplayName("날짜 표기를 해석하지 못하면 경고를 내고 기간을 비워 둔다")
    void reportsUnparseableDate() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업", "시작일자 (YY/MM)", "미정"));

        CapitalOverviewReader.Result result =
                reader.read(sheet, context(Map.of()), FormCatalogs.empty());

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
                reader.read(sheet, context(Map.of()), FormCatalogs.empty()).project();

        assertThat(project.getSttDtm()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(project.getEndDtm()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("법규상 완료시기는 구간 표기라 날짜로 반입하지 않고 안내만 남긴다")
    void keepsLegalDeadlineUnimported() {
        // 컬럼은 YYYYMMDD 의무완료기한인데 양식은 `2026년 이내`처럼 구간을 고르게 되어 있다.
        // 추정한 날짜를 법규 기한 칸에 넣지 않고 사람이 상세 화면에서 채우도록 안내한다.
        assertThat(deadlineOf("2026년 이내")).isNull();
        assertThat(deadlineOf("2026.02")).isNull();

        Sheet sheet =
                overviewSheet(
                        new java.util.LinkedHashMap<>(Map.of("사업명", "사업", "법규상 완료시기", "2026년 이내")));

        assertThat(reader.read(sheet, context(Map.of()), FormCatalogs.empty()).diagnostics())
                .anyMatch(
                        d ->
                                "flfFsgDt".equals(d.field())
                                        && d.code() == RequestFormDiagnosticCode.OPTIONAL_MISSING
                                        && d.message().contains("정확한 기한"));
    }

    @Test
    @DisplayName("선택 항목 보정값은 그대로 저장값이 되고 미기재 안내도 사라진다")
    void optionalFieldOverrideBecomesStoredValue() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업"));
        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(
                                FormSheetKind.CAPITAL_OVERVIEW, null, "bzDttNm"),
                        "IT",
                        FormAdapterContext.overrideKey(
                                FormSheetKind.CAPITAL_OVERVIEW, null, "exePttYn"),
                        "1",
                        FormAdapterContext.overrideKey(
                                FormSheetKind.CAPITAL_OVERVIEW, null, "flfFsgDt"),
                        "20261231");

        CapitalOverviewReader.Result result =
                reader.read(sheet, context(overrides), FormCatalogs.empty());

        assertThat(result.project().getBzDttNm()).isEqualTo("IT");
        assertThat(result.project().getExePttYn()).isEqualTo("1");
        assertThat(result.project().getFlfFsgDt()).isEqualTo("20261231");
        assertThat(result.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::field)
                .doesNotContain("bzDttNm", "exePttYn", "flfFsgDt");
    }

    @Test
    @DisplayName("미기재 안내는 고를 후보와 입력 종류를 함께 담는다")
    void optionalDiagnosticCarriesDecisionInput() {
        // 화면 `결정` 열이 이 값으로 위젯을 고른다. 후보가 없는 항목도 입력 종류는 지정된다
        Sheet sheet = overviewSheet(Map.of("사업명", "사업"));
        FormCatalogs catalogs =
                new FormCatalogs(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of("bzDttNm", List.of(new MigrationDto.Candidate("IT", "IT"))));

        List<RequestFormDto.FormDiagnostic> diagnostics =
                reader.read(sheet, context(Map.of()), catalogs).diagnostics();

        assertThat(diagnostics)
                .filteredOn(d -> "bzDttNm".equals(d.field()))
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.decision()).isEqualTo(RequestFormDecisionKind.SELECT);
                            assertThat(d.candidates()).hasSize(1);
                        });
        assertThat(diagnostics)
                .filteredOn(d -> "flfFsgDt".equals(d.field()))
                .singleElement()
                .satisfies(d -> assertThat(d.decision()).isEqualTo(RequestFormDecisionKind.DATE));
    }

    @Test
    @DisplayName("법규상 완료시기가 `별도없음`이면 미기재 안내를 내지 않는다")
    void treatsNoDeadlineAsAnswer() {
        // 미기재가 아니라 "기한이 없다"는 확정된 답이다.
        Sheet sheet =
                overviewSheet(
                        new java.util.LinkedHashMap<>(Map.of("사업명", "사업", "법규상 완료시기", "별도없음")));

        assertThat(reader.read(sheet, context(Map.of()), FormCatalogs.empty()).diagnostics())
                .noneMatch(d -> "flfFsgDt".equals(d.field()));
    }

    @Test
    @DisplayName("중복 여부 표기를 Y/N으로 접는다")
    void normalizesDuplicateFlag() {
        Sheet sheet =
                overviewSheet(new java.util.LinkedHashMap<>(Map.of("사업명", "사업", "중복 여부", "○")));

        ProjectDto.CreateRequest project =
                reader.read(sheet, context(Map.of()), FormCatalogs.empty()).project();

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
                reader.read(
                                sheet,
                                context(Map.of()),
                                new FormCatalogs(
                                        Map.of("확정", "1"),
                                        Map.of("전무이사", "21"),
                                        Map.of(),
                                        Map.of()))
                        .project();

        assertThat(project.getExePttYn()).isEqualTo("1");
        assertThat(project.getEdrtTc()).isEqualTo("21");
    }

    @ParameterizedTest(name = "{0} → 지역본부장(23)")
    @ValueSource(strings = {"지역본부장", "동남권본부장", "동남권 본부장", "홍길동 본부장"})
    @DisplayName("전결권자 값에 본부장이 포함되면 지역본부장 자본예산 코드로 해석한다")
    void mapsAnyHeadquartersDelegationToRegionalHeadCode(String source) {
        Sheet sheet =
                overviewSheet(new java.util.LinkedHashMap<>(Map.of("사업명", "사업", "전결권자", source)));

        CapitalOverviewReader.Result result =
                reader.read(
                        sheet,
                        context(Map.of()),
                        new FormCatalogs(Map.of(), Map.of("지역본부장", "23"), Map.of(), Map.of()));

        assertThat(result.project().getEdrtTc()).isEqualTo("23");
        assertThat(result.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.CODE_UNRESOLVED);
    }

    @Test
    @DisplayName("전결권자를 코드표에서 못 찾으면 차단하되 보정으로 풀 수 있다")
    void delegationCanBeCorrectedByOverride() {
        Sheet sheet =
                overviewSheet(new java.util.LinkedHashMap<>(Map.of("사업명", "사업", "전결권자", "없는직위")));

        CapitalOverviewReader.Result blocked =
                reader.read(sheet, context(Map.of()), FormCatalogs.empty());
        assertThat(blocked.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.CODE_UNRESOLVED);

        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(
                                FormSheetKind.CAPITAL_OVERVIEW, null, "edrtTc"),
                        "21");
        CapitalOverviewReader.Result corrected =
                reader.read(sheet, context(overrides), FormCatalogs.empty());

        assertThat(corrected.project().getEdrtTc()).isEqualTo("21");
        assertThat(corrected.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.CODE_UNRESOLVED);
    }

    @Test
    @DisplayName("전결권자 코드를 못 찾으면 공통코드 후보를 붙여 고르게 한다")
    void offersDelegationCandidatesWhenUnresolved() {
        Sheet sheet =
                overviewSheet(new java.util.LinkedHashMap<>(Map.of("사업명", "사업", "전결권자", "없는직위")));
        FormCatalogs catalogs =
                new FormCatalogs(
                        Map.of(),
                        Map.of("전무이사", "21"),
                        Map.of(),
                        Map.of(
                                "edrtTc",
                                List.of(
                                        new MigrationDto.Candidate("21", "전무이사"),
                                        new MigrationDto.Candidate("22", "부문장"))));

        CapitalOverviewReader.Result read = reader.read(sheet, context(Map.of()), catalogs);

        assertThat(read.diagnostics())
                .filteredOn(diagnostic -> "edrtTc".equals(diagnostic.field()))
                .singleElement()
                .satisfies(
                        diagnostic -> {
                            assertThat(diagnostic.code())
                                    .isEqualTo(RequestFormDiagnosticCode.CODE_UNRESOLVED);
                            assertThat(diagnostic.severity())
                                    .isEqualTo(MigrationDto.Severity.BLOCKER);
                            assertThat(diagnostic.decision())
                                    .isEqualTo(RequestFormDecisionKind.SELECT);
                            assertThat(diagnostic.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .containsExactly("21", "22");
                        });
    }

    @Test
    @DisplayName("요약표가 없으면 대사 기준값을 비워 둔다")
    void leavesDeclaredTotalNullWhenSummaryAbsent() {
        Sheet sheet = overviewSheet(Map.of("사업명", "사업"));

        assertThat(
                        reader.read(sheet, context(Map.of()), FormCatalogs.empty())
                                .amounts()
                                .yearTotalRaw())
                .isNull();
    }

    @Test
    @DisplayName("요약표의 두 컬럼을 총 계 행에서 읽는다")
    void readsSummaryColumnsFromTotalRow() {
        Sheet sheet =
                summarySheet("2,000백만원", Map.of(6, 1_265_624_700d, 7, 500_000_000d), Map.of());

        CapitalOverviewReader.DeclaredAmounts amounts = amountsOf(sheet);

        assertThat(amounts.yearTotalRaw()).isEqualByComparingTo("1265624700");
        assertThat(amounts.laterTotalRaw()).isEqualByComparingTo("500000000");
    }

    @Test
    @DisplayName("총 계 행의 칸이 비면 데이터 행을 더해 채운다")
    void sumsDataRowsWhenTotalCellBlank() {
        // 실측 제출본에 `'26년도 이후`만 총 계 행이 비어 있는 파일이 있다
        Sheet sheet =
                summarySheet(
                        "2,000백만원",
                        Map.of(6, 1_265_624_700d),
                        Map.of(7, new double[] {300_000_000d, 200_000_000d}));

        assertThat(amountsOf(sheet).laterTotalRaw()).isEqualByComparingTo("500000000");
    }

    @Test
    @DisplayName("총 계 행과 데이터 행이 모두 비면 그 컬럼은 null이다")
    void leavesColumnNullWhenNothingWritten() {
        Sheet sheet = summarySheet("2,000백만원", Map.of(6, 1_265_624_700d), Map.of());

        assertThat(amountsOf(sheet).laterTotalRaw()).isNull();
    }

    @Test
    @DisplayName("요약표가 없으면 두 컬럼 모두 null이다")
    void leavesSummaryNullWhenAbsent() {
        CapitalOverviewReader.DeclaredAmounts amounts =
                amountsOf(overviewSheet(Map.of("사업명", "사업")));

        assertThat(amounts.yearTotalRaw()).isNull();
        assertThat(amounts.laterTotalRaw()).isNull();
    }

    @Test
    @DisplayName("총 사업금액의 단위 접미사를 원 단위로 편다")
    void parsesWholePeriodUnitSuffix() {
        assertThat(amountsOf(summarySheet("2,000백만원", Map.of(), Map.of())).wholePeriodWon())
                .isEqualByComparingTo("2000000000");
        assertThat(amountsOf(summarySheet("1,500천원", Map.of(), Map.of())).wholePeriodWon())
                .isEqualByComparingTo("1500000");
        assertThat(amountsOf(summarySheet("1,265,624,700원", Map.of(), Map.of())).wholePeriodWon())
                .isEqualByComparingTo("1265624700");
    }

    @Test
    @DisplayName("접미사가 없으면 원 단위로 확정하지 않고 기재값만 남긴다")
    void keepsRawWhenNoSuffix() {
        CapitalOverviewReader.DeclaredAmounts amounts =
                amountsOf(summarySheet("2000", Map.of(), Map.of()));

        assertThat(amounts.wholePeriodWon()).isNull();
        assertThat(amounts.wholePeriodRaw()).isEqualByComparingTo("2000");
        assertThat(amounts.wholePeriodUnknownUnit()).isFalse();
    }

    @Test
    @DisplayName("모르는 접미사는 배수 폴백을 막기 위해 해석 실패로 표시한다")
    void flagsUnknownSuffix() {
        // `20억원`을 요약표 배수로 환산하면 100배 틀린 값이 조용히 들어간다
        CapitalOverviewReader.DeclaredAmounts unknown =
                amountsOf(summarySheet("20억원", Map.of(), Map.of()));
        assertThat(unknown.wholePeriodWon()).isNull();
        assertThat(unknown.wholePeriodRaw()).isNull();
        assertThat(unknown.wholePeriodUnknownUnit()).isTrue();

        CapitalOverviewReader.DeclaredAmounts text =
                amountsOf(summarySheet("미정", Map.of(), Map.of()));
        assertThat(text.wholePeriodUnknownUnit()).isTrue();
    }

    @Test
    @DisplayName("총 사업금액 칸이 없으면 해석 실패가 아니라 미기재로 둔다")
    void leavesWholePeriodEmptyWhenLabelAbsent() {
        CapitalOverviewReader.DeclaredAmounts amounts =
                amountsOf(summarySheet(null, Map.of(6, 100d), Map.of()));

        assertThat(amounts.wholePeriodWon()).isNull();
        assertThat(amounts.wholePeriodRaw()).isNull();
        assertThat(amounts.wholePeriodUnknownUnit()).isFalse();
    }

    private String deadlineOf(String raw) {
        Sheet sheet =
                overviewSheet(new java.util.LinkedHashMap<>(Map.of("사업명", "사업", "법규상 완료시기", raw)));
        return reader.read(sheet, context(Map.of()), FormCatalogs.empty()).project().getFlfFsgDt();
    }

    /**
     * 요약표가 있는 1-1 시트를 만듭니다.
     *
     * <p>레이아웃은 실 제출본과 같습니다 — 헤더 행에 `'26년도 합계`(6열)·`'26년도 이후`(7열)를 놓고 데이터 행 2개 아래에 `총 계` 행을 둡니다.
     *
     * @param wholePeriod `총 사업금액(전체기간)` 칸에 적을 문자열. null이면 칸을 만들지 않습니다
     * @param totalRowValues 총 계 행의 {열 번호 → 값}. 비워 두면 그 칸이 빈칸이 됩니다
     * @param dataRowValues 데이터 행 2개의 {열 번호 → 값 2개}. 비워 두면 그 칸이 빈칸이 됩니다
     */
    private static Sheet summarySheet(
            String wholePeriod,
            Map<Integer, Double> totalRowValues,
            Map<Integer, double[]> dataRowValues) {
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet("① (정보화사업) 1-1. 정보화사업 개요");
            Row labelRow = sheet.createRow(0);
            cell(labelRow, 2).setCellValue("사업명");
            cell(labelRow, 3).setCellValue("사업");
            if (wholePeriod != null) {
                Row amountRow = sheet.createRow(1);
                cell(amountRow, 7).setCellValue("총 사업금액(전체기간)");
                cell(amountRow, 9).setCellValue(wholePeriod);
            }
            Row header = sheet.createRow(2);
            cell(header, 6).setCellValue("'26년도 합계");
            cell(header, 7).setCellValue("'26년도 이후");
            cell(header, 8).setCellValue("전체 합계");
            for (int offset = 0; offset < 2; offset++) {
                Row dataRow = sheet.createRow(3 + offset);
                cell(dataRow, 0).setCellValue(offset == 0 ? "자본예산" : "일반관리비");
                for (Map.Entry<Integer, double[]> entry : dataRowValues.entrySet()) {
                    cell(dataRow, entry.getKey()).setCellValue(entry.getValue()[offset]);
                }
            }
            Row totalRow = sheet.createRow(5);
            cell(totalRow, 0).setCellValue("총 계");
            for (Map.Entry<Integer, Double> entry : totalRowValues.entrySet()) {
                cell(totalRow, entry.getKey()).setCellValue(entry.getValue());
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return new HSSFWorkbook(new java.io.ByteArrayInputStream(out.toByteArray()))
                        .getSheetAt(0);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CapitalOverviewReader.DeclaredAmounts amountsOf(Sheet sheet) {
        return reader.read(sheet, context(Map.of()), FormCatalogs.empty()).amounts();
    }
}
