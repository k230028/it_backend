package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.request.service.WorkbookReader;
import com.kdb.it.domain.migration.request.support.FormDiagnostics;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * 어댑터의 비목 해석·금액 대사 분기를 시트를 직접 만들어 확인합니다.
 *
 * <p>공용 픽스처는 정상 흐름 하나를 재현한 것이라 "비목을 못 정하는 품목", "1-1 합계가 1-2와 어긋나는 파일", "1-2 시트만 없는 파일" 같은 변형을 담지
 * 못합니다. 여기서 그 변형을 직접 만들어 각 진단이 실제로 나오는지 봅니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormAdapterResolutionPathTest {

    @Mock private OrgIdentityResolver.Index orgIndex;
    @Mock private MigrationIoeCatalogReader catalogReader;

    private final WorkbookReader workbookReader = new WorkbookReader(10_485_760L, 20, 5000);
    private final SheetAnchorScanner scanner = new SheetAnchorScanner();
    private final FormLabelReader labelReader = new FormLabelReader(scanner);
    private final ResourceTableReader resourceTableReader = new ResourceTableReader(scanner);

    private CapitalProjectFormAdapter capitalAdapter() {
        when(catalogReader.exePttCodeByName()).thenReturn(Map.of());
        when(catalogReader.edrtCapitalCodeByName()).thenReturn(Map.of());
        when(orgIndex.resolveOrg(any()))
                .thenReturn(new OrgIdentityResolver.Resolution("0210", "부서", List.of(), false));
        when(orgIndex.resolveUser(any(), any()))
                .thenReturn(
                        new OrgIdentityResolver.Resolution("12345678", "담당자", List.of(), false));
        return new CapitalProjectFormAdapter(
                new CapitalOverviewReader(scanner, labelReader, new FormCheckboxReader()),
                resourceTableReader,
                catalogReader);
    }

    /** 통화 공통코드(`CUR_C`)를 답하도록 미리 스텁한 시트 ③ 어댑터. */
    private GeneralExpenseFormAdapter generalExpenseAdapter() {
        when(catalogReader.currencyCandidates())
                .thenReturn(
                        List.of(
                                new MigrationDto.Candidate("KRW", "원화"),
                                new MigrationDto.Candidate("USD", "미국 달러"),
                                new MigrationDto.Candidate("GBP", "영국 파운드"),
                                new MigrationDto.Candidate("JPY", "일본 엔")));
        return new GeneralExpenseFormAdapter(
                scanner, catalogReader, new FormApproverReader(scanner));
    }

    private FormAdapterContext contextOf(byte[] bytes, Map<String, String> overrides) {
        return new FormAdapterContext(
                workbookReader.classify(workbookReader.open(bytes, "픽스처.xls")),
                "2026",
                new RequestFormDto.FileEntry("부서/파일.xls", "부서", null, AmountUnit.WON, null),
                "0210",
                "부서",
                orgIndex,
                TestIoeIndex.snapshot(),
                overrides,
                "12345678");
    }

    @Test
    @DisplayName("1-2 시트가 없으면 사업만 만들고 품목은 비운다")
    void capitalWithoutResourceSheetHasNoItems() {
        FormAdapterOutput output =
                capitalAdapter().adapt(contextOf(overviewOnlyWorkbook(null), Map.of()));

        ProjectDto.CreateRequest project = output.projects().get(0);
        assertThat(project.getAbusNm()).isEqualTo("사업");
        assertThat(project.getItems()).isEmpty();
        // 요약표가 없어 1-1 선언 금액을 산출하지 못하므로(Task 3) 사업은 그대로 만들되 경고를 낸다
        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        // 산출 실패 경고는 나오되, 대사할 상대가 없으므로 대사 경고는 침묵해야 한다.
        // 두 진단이 같은 코드를 쓰므로 code만 보면 이 보증이 사라진다 — field로 갈라서 본다.
        assertThat(FormDiagnostics.byField(output.diagnostics(), "declaredYearTotal")).isEmpty();
        assertThat(FormDiagnostics.byField(output.diagnostics(), "declaredAmounts"))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .containsExactly(RequestFormDiagnosticCode.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("1-1 합계가 1-2와 어느 단위로도 맞지 않으면 불일치 경고를 낸다")
    void reportsAmountMismatch() {
        // 1-2 품목 합계는 1,000,000원인데 1-1 요약표에는 7을 적었다 — 어떤 배수로도 맞지 않는다
        FormAdapterOutput output =
                capitalAdapter().adapt(contextOf(overviewWithResource(7d), Map.of()));

        // 같은 조건에서 산출 실패 경고(field=declaredAmounts)도 같은 코드로 나온다.
        // 대사 경고 자체를 검증하려면 field와 문구까지 좁혀야 한다.
        assertThat(FormDiagnostics.byField(output.diagnostics(), "declaredYearTotal"))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .containsExactly(RequestFormDiagnosticCode.AMOUNT_MISMATCH);
        assertThat(FormDiagnostics.messageOf(output.diagnostics(), "declaredYearTotal"))
                .contains("어느 단위로도 맞지 않습니다");
    }

    @Test
    @DisplayName("1-1 합계가 배수로 맞으면 경고를 내지 않는다")
    void staysQuietWhenTotalsReconcile() {
        // 1,000,000원 = 1백만원. 백만원 단위로 적은 것으로 해석된다
        FormAdapterOutput output =
                capitalAdapter().adapt(contextOf(overviewWithResource(1d), Map.of()));

        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("품목 비목을 못 정하면 미해석 진단을 내고 비목을 비운다")
    void capitalItemIoeUnresolved() {
        FormAdapterOutput output =
                capitalAdapter().adapt(contextOf(overviewWithUnknownGroup(), Map.of()));

        assertThat(output.projects().get(0).getItems().get(0).getIoeC()).isNull();
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.CODE_UNRESOLVED);
    }

    @Test
    @DisplayName("품목 비목 보정값이 있으면 자동 해석보다 우선한다")
    void capitalItemIoeOverride() {
        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(FormSheetKind.CAPITAL_RESOURCE, 12, "ioeC"),
                        "104");

        FormAdapterOutput output =
                capitalAdapter().adapt(contextOf(overviewWithUnknownGroup(), overrides));

        assertThat(output.projects().get(0).getItems().get(0).getIoeC()).isEqualTo("104");
    }

    @Test
    @DisplayName("경상사업 품목 비목을 못 정하면 미해석 진단을 낸다")
    void recurringItemIoeUnresolved() {
        RecurringProjectFormAdapter adapter =
                new RecurringProjectFormAdapter(
                        labelReader, resourceTableReader, new FormApproverReader(scanner));

        FormAdapterOutput output = adapter.adapt(contextOf(recurringWithUnknownGroup(), Map.of()));

        assertThat(output.projects().get(0).getItems().get(0).getIoeC()).isNull();
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.CODE_UNRESOLVED);
    }

    @Test
    @DisplayName("경상사업 품목 비목 보정값이 있으면 자동 해석보다 우선한다")
    void recurringItemIoeOverride() {
        RecurringProjectFormAdapter adapter =
                new RecurringProjectFormAdapter(
                        labelReader, resourceTableReader, new FormApproverReader(scanner));
        Map<String, String> overrides =
                Map.of(FormAdapterContext.overrideKey(FormSheetKind.RECURRING, 10, "ioeC"), "101");

        FormAdapterOutput output = adapter.adapt(contextOf(recurringWithUnknownGroup(), overrides));

        assertThat(output.projects().get(0).getItems().get(0).getIoeC()).isEqualTo("101");
    }

    @Test
    @DisplayName("시트 ③ 정보보호 표기를 해석하지 못하면 N으로 기본 처리하고 경고를 낸다")
    void generalExpenseInfoSecDefaultsToNo() {
        GeneralExpenseFormAdapter adapter = generalExpenseAdapter();

        FormAdapterOutput output =
                adapter.adapt(contextOf(generalExpenseSheet("해당없음", "GBP"), Map.of()));

        assertThat(output.costs().get(0).getSectSysUtzYn()).isEqualTo("N");
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.CODE_DEFAULTED)
                .doesNotContain(RequestFormDiagnosticCode.CODE_UNRESOLVED);
    }

    @Test
    @DisplayName("시트 ③에 원화 행이 없으면 단위 제안이 원 단위로 떨어진다")
    void generalExpenseWithoutKrwRowSuggestsWon() {
        GeneralExpenseFormAdapter adapter = generalExpenseAdapter();
        FormAdapterContext context =
                new FormAdapterContext(
                        workbookReader.classify(
                                workbookReader.open(generalExpenseSheet("X", "GBP"), "픽스처.xls")),
                        "2026",
                        new RequestFormDto.FileEntry("부서/파일.xls", "부서", null, null, null),
                        "0210",
                        "부서",
                        orgIndex,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678");

        FormAdapterOutput output = adapter.adapt(context);

        assertThat(output.suggestedGeneralExpenseUnit()).isEqualTo(AmountUnit.WON);
        CostDto.CreateRequest cost = output.costs().get(0);
        assertThat(cost.getFcAmt()).isNotNull();
        assertThat(cost.getCostTotXpAmt()).isNull();
    }

    @Test
    @DisplayName("연간 금액이 비면 금액을 채우지 않는다")
    void generalExpenseWithoutAnnualLeavesAmountNull() {
        GeneralExpenseFormAdapter adapter = generalExpenseAdapter();

        FormAdapterOutput output =
                adapter.adapt(contextOf(generalExpenseWithoutAnnual(), Map.of()));

        assertThat(output.costs()).hasSize(1);
        assertThat(output.costs().get(0).getCostTotXpAmt()).isNull();
        assertThat(output.costs().get(0).getFcAmt()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 비목코드로 보정하면 자동 해석으로 되돌아간다")
    void recurringIgnoresUnknownOverrideCode() {
        RecurringProjectFormAdapter adapter =
                new RecurringProjectFormAdapter(
                        labelReader, resourceTableReader, new FormApproverReader(scanner));
        Map<String, String> overrides =
                Map.of(FormAdapterContext.overrideKey(FormSheetKind.RECURRING, 10, "ioeC"), "999");

        FormAdapterOutput output = adapter.adapt(contextOf(recurringWithUnknownGroup(), overrides));

        // 보정값이 실재하지 않으면 그대로 믿지 않고 자동 해석 결과(미해석)를 따른다
        assertThat(output.projects().get(0).getItems().get(0).getIoeC()).isNull();
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.CODE_UNRESOLVED);
    }

    @Test
    @DisplayName("사업명만 있고 소요자원이 없으면 품목 없는 사업을 만든다")
    void recurringWithoutResourcesStillCreatesProject() {
        RecurringProjectFormAdapter adapter =
                new RecurringProjectFormAdapter(
                        labelReader, resourceTableReader, new FormApproverReader(scanner));

        FormAdapterOutput output = adapter.adapt(contextOf(recurringNameOnly("경상사업"), Map.of()));

        assertThat(output.projects()).hasSize(1);
        assertThat(output.projects().get(0).getItems()).isEmpty();
        assertThat(output.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("사업명 라벨은 있는데 값이 공백이면 필수값 누락으로 본다")
    void recurringTreatsBlankNameAsMissing() {
        RecurringProjectFormAdapter adapter =
                new RecurringProjectFormAdapter(
                        labelReader, resourceTableReader, new FormApproverReader(scanner));

        FormAdapterOutput output = adapter.adapt(contextOf(recurringNameOnly("   "), Map.of()));

        // 사업명도 소요자원도 없으므로 부점이 쓰지 않은 시트로 보아 조용히 건너뛴다
        assertThat(output.projects()).isEmpty();
        assertThat(output.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("시트 ③ 헤더가 한 행뿐이면 바로 다음 행부터 데이터로 읽는다")
    void generalExpenseWithSingleRowHeader() {
        GeneralExpenseFormAdapter adapter = generalExpenseAdapter();

        FormAdapterOutput output =
                adapter.adapt(contextOf(generalExpenseSingleRowHeader(), Map.of()));

        assertThat(output.costs()).hasSize(1);
        assertThat(output.costs().get(0).getCttNm()).isEqualTo("한 행 헤더 계약");
    }

    @Test
    @DisplayName("시트 ③의 서식만 남은 빈 행은 건너뛴다")
    void generalExpenseSkipsFormattingOnlyRows() {
        GeneralExpenseFormAdapter adapter = generalExpenseAdapter();

        FormAdapterOutput output = adapter.adapt(contextOf(generalExpenseWithBlankRow(), Map.of()));

        assertThat(output.costs())
                .extracting(CostDto.CreateRequest::getCttNm)
                .containsExactly("첫 계약", "셋째 계약");
    }

    // ── 시트 빌더 ────────────────────────────────────────────────────────────

    /** 사업명만 담은 시트 ②. 소요자원 표가 없습니다. */
    private static byte[] recurringNameOnly(String name) {
        return build(
                wb -> {
                    Sheet sheet = wb.createSheet("② (경상사업) 2. 경상적인 사업");
                    put(sheet, 2, 0, "사업명");
                    put(sheet, 2, 2, name);
                });
    }

    /** 헤더가 한 행뿐인 시트 ③. 부점이 병합을 풀어 낸 경우입니다. */
    private static byte[] generalExpenseSingleRowHeader() {
        return build(
                wb -> {
                    Sheet sheet = wb.createSheet("③ (일반관리비) 전산 일반관리비 편성요청서");
                    put(sheet, 3, 0, "비 목 명");
                    put(sheet, 3, 2, "계약명 / 건명");
                    put(sheet, 3, 3, "통화 구분");
                    put(sheet, 3, 5, "연간");
                    put(sheet, 3, 6, "상대처");
                    put(sheet, 4, 0, "전산 제비");
                    put(sheet, 4, 1, "회선사용료");
                    put(sheet, 4, 2, "한 행 헤더 계약");
                    put(sheet, 4, 3, "KRW");
                    putNumber(sheet, 4, 5, 1_000d);
                    put(sheet, 4, 6, "상대처");
                });
    }

    /** 데이터 사이에 서식만 남은 빈 행이 낀 시트 ③. */
    private static byte[] generalExpenseWithBlankRow() {
        return build(
                wb -> {
                    Sheet sheet = writeGeneralExpenseHeader(wb);
                    put(sheet, 5, 0, "전산 제비");
                    put(sheet, 5, 1, "회선사용료");
                    put(sheet, 5, 2, "첫 계약");
                    put(sheet, 5, 3, "KRW");
                    putNumber(sheet, 5, 5, 1_000d);
                    // 6행은 서식만 남은 빈 행 — 셀은 만들되 값은 넣지 않는다
                    cell(sheet, 6, 2).setBlank();
                    put(sheet, 7, 2, "셋째 계약");
                    put(sheet, 7, 3, "KRW");
                    putNumber(sheet, 7, 5, 2_000d);
                });
    }

    /** 1-1만 담은 워크북. `declaredTotal`이 null이면 요약표를 넣지 않습니다. */
    private static byte[] overviewOnlyWorkbook(Double declaredTotal) {
        return build(
                wb -> {
                    Sheet sheet = wb.createSheet("① (정보화사업) 1-1. 정보화사업 개요");
                    put(sheet, 0, 2, "사업명");
                    put(sheet, 0, 3, "사업");
                    if (declaredTotal != null) writeSummaryTable(sheet, declaredTotal);
                });
    }

    /** 1-1 요약표(지정 합계) + `총 사업금액(전체기간)` + 1-2 품목 1건(1,000,000원)을 담은 워크북. */
    private static byte[] overviewWithResource(double declaredTotal) {
        return build(
                wb -> {
                    Sheet overview = wb.createSheet("① (정보화사업) 1-1. 정보화사업 개요");
                    put(overview, 0, 2, "사업명");
                    put(overview, 0, 3, "사업");
                    // Task 3의 산출 로직이 `총 사업금액(전체기간)`을 요구하므로, 대사(reconcileTotals) 검증에만
                    // 집중하는 이 픽스처도 지급금액이 음수가 되지 않도록 넉넉한 값을 채워 둔다
                    put(overview, 1, 7, "총 사업금액(전체기간)");
                    put(overview, 1, 9, "2백만원");
                    writeSummaryTable(overview, declaredTotal);
                    writeResourceSheet(wb, "기계장치(HW)", 1_000_000d);
                });
    }

    /** 1-2 품목의 중분류가 코드표에 없는 워크북. */
    private static byte[] overviewWithUnknownGroup() {
        return build(
                wb -> {
                    Sheet overview = wb.createSheet("① (정보화사업) 1-1. 정보화사업 개요");
                    put(overview, 0, 2, "사업명");
                    put(overview, 0, 3, "사업");
                    writeResourceSheet(wb, "없는중분류", 1_000_000d);
                });
    }

    /** 시트 ②의 품목 중분류가 코드표에 없는 워크북. */
    private static byte[] recurringWithUnknownGroup() {
        return build(
                wb -> {
                    Sheet sheet = wb.createSheet("② (경상사업) 2. 경상적인 사업");
                    put(sheet, 2, 0, "사업명");
                    put(sheet, 2, 2, "경상사업");
                    put(sheet, 7, 0, "구분");
                    put(sheet, 7, 2, "항목");
                    put(sheet, 7, 4, "수량");
                    put(sheet, 7, 6, "통화");
                    put(sheet, 7, 7, "소요예산 (부가세포함)");
                    put(sheet, 9, 1, "없는중분류");
                    put(sheet, 9, 2, "품목");
                    putNumber(sheet, 9, 4, 1);
                    put(sheet, 9, 6, "KRW");
                    putNumber(sheet, 9, 7, 1_000d);
                });
    }

    /** 시트 ③ 1행을 담은 워크북. */
    private static byte[] generalExpenseSheet(String infoSec, String currency) {
        return build(
                wb -> {
                    Sheet sheet = writeGeneralExpenseHeader(wb);
                    put(sheet, 5, 0, "전산 제비");
                    put(sheet, 5, 1, "회선사용료");
                    put(sheet, 5, 2, "계약");
                    put(sheet, 5, 3, currency);
                    putNumber(sheet, 5, 5, 1_000d);
                    put(sheet, 5, 6, "상대처");
                    put(sheet, 5, 9, infoSec);
                });
    }

    /** 연간 금액이 없고 계약명만 있는 시트 ③. */
    private static byte[] generalExpenseWithoutAnnual() {
        return build(
                wb -> {
                    Sheet sheet = writeGeneralExpenseHeader(wb);
                    put(sheet, 5, 0, "전산 제비");
                    put(sheet, 5, 1, "회선사용료");
                    put(sheet, 5, 2, "금액 없는 계약");
                    put(sheet, 5, 3, "KRW");
                    put(sheet, 5, 6, "상대처");
                    put(sheet, 5, 9, "X");
                });
    }

    private static Sheet writeGeneralExpenseHeader(Workbook wb) {
        Sheet sheet = wb.createSheet("③ (일반관리비) 전산 일반관리비 편성요청서");
        put(sheet, 3, 0, "비 목 명");
        put(sheet, 3, 2, "계약명 / 건명");
        put(sheet, 3, 3, "통화 구분");
        put(sheet, 3, 4, "소요예산");
        put(sheet, 3, 6, "계약");
        put(sheet, 3, 9, "정보보호 관련여부");
        put(sheet, 4, 4, "월간");
        put(sheet, 4, 5, "연간");
        put(sheet, 4, 6, "상대처");
        put(sheet, 4, 7, "계속");
        put(sheet, 4, 8, "신규");
        return sheet;
    }

    private static void writeResourceSheet(Workbook wb, String group, double amount) {
        Sheet sheet = wb.createSheet("① (정보화사업) 1-2. 소요자원 상세내용");
        put(sheet, 9, 1, "구분");
        put(sheet, 9, 3, "항목");
        put(sheet, 9, 4, "수량");
        put(sheet, 9, 6, "통화");
        put(sheet, 9, 7, "소요예산 (부가세포함)");
        put(sheet, 11, 2, group);
        put(sheet, 11, 3, "품목");
        putNumber(sheet, 11, 4, 1);
        put(sheet, 11, 6, "KRW");
        putNumber(sheet, 11, 7, amount);
    }

    private static void writeSummaryTable(Sheet sheet, double total) {
        put(sheet, 3, 6, "'26년도 합계");
        put(sheet, 5, 0, "총 계");
        putNumber(sheet, 5, 6, total);
    }

    private static byte[] build(java.util.function.Consumer<Workbook> filler) {
        try (Workbook wb = new HSSFWorkbook()) {
            filler.accept(wb);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void put(Sheet sheet, int rowIndex, int colIndex, String value) {
        cell(sheet, rowIndex, colIndex).setCellValue(value);
    }

    private static void putNumber(Sheet sheet, int rowIndex, int colIndex, double value) {
        cell(sheet, rowIndex, colIndex).setCellValue(value);
    }

    private static Cell cell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell existing = row.getCell(colIndex);
        return existing == null ? row.createCell(colIndex) : existing;
    }
}
