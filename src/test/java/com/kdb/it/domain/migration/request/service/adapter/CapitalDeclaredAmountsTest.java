package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 1-1 선언 금액 산출의 실패 분기를 시트를 직접 만들어 확인합니다.
 *
 * <p>공용 픽스처는 정상 파일 하나를 재현한 것이라 "단위를 못 정하는 파일", "총액이 요약표 합계보다 작은 파일" 같은 변형을 담지 못합니다. 여기서는 1-1만 담은
 * 시트를 만들어 각 분기를 직접 밟습니다 — 1-2가 없으므로 품목 합계가 0이 되어 배수 판정이 실패하는 경로가 기본값입니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapitalDeclaredAmountsTest {

    @Mock private OrgIdentityResolver.Index orgIndex;
    @Mock private MigrationIoeCatalogReader catalogReader;

    private final SheetAnchorScanner scanner = new SheetAnchorScanner();
    private CapitalProjectFormAdapter adapter;

    // 필드 초기화식은 Mockito가 @Mock을 주입하기 전(테스트 인스턴스 생성 시점)에 실행되어 catalogReader가
    // 아직 null이다. CapitalProjectFormAdapterTest와 같이 @BeforeEach에서 조립해 주입 이후로 미룬다.
    @BeforeEach
    void setUp() {
        adapter =
                new CapitalProjectFormAdapter(
                        new CapitalOverviewReader(
                                scanner, new FormLabelReader(scanner), new FormCheckboxReader()),
                        new ResourceTableReader(scanner),
                        catalogReader);
    }

    @Test
    @DisplayName("품목이 없어 배수를 못 정하면 금액을 적재하지 않고 경고만 낸다")
    void skipsAmountsWhenUnitUnresolved() {
        FormAdapterOutput output = adapt(overviewOnly("2,000백만원", 1_265_624_700d, 0d));

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("기 지급예산을 산출하지 못했습니다");
    }

    @Test
    @DisplayName("요약표가 없어도 금액을 적재하지 않고 경고만 낸다")
    void skipsAmountsWhenSummaryAbsent() {
        FormAdapterOutput output = adapt(overviewOnly("2,000백만원", null, null));

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("기 지급예산을 산출하지 못했습니다");
    }

    @Test
    @DisplayName("사업은 그대로 만들어 파일을 막지 않는다")
    void stillProducesProject() {
        FormAdapterOutput output = adapt(overviewOnly("2,000백만원", 1_265_624_700d, 0d));

        assertThat(output.projects()).hasSize(1);
        assertThat(output.diagnostics()).noneMatch(diagnostic -> diagnostic.code().blocks());
    }

    private FormAdapterOutput adapt(Sheet overview) {
        Map<FormSheetKind, Sheet> sheets = new EnumMap<>(FormSheetKind.class);
        sheets.put(FormSheetKind.CAPITAL_OVERVIEW, overview);
        return adapter.adapt(
                new FormAdapterContext(
                        sheets,
                        "2026",
                        new RequestFormDto.FileEntry("부서/파일.xls", "폴더부서", null, null, null),
                        "0999",
                        "폴더부서",
                        orgIndex,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678"));
    }

    private static String amountWarning(FormAdapterOutput output) {
        return output.diagnostics().stream()
                .filter(d -> d.code() == RequestFormDiagnosticCode.AMOUNT_MISMATCH)
                .map(RequestFormDto.FormDiagnostic::message)
                .findFirst()
                .orElse("");
    }

    /** 1-1만 담은 시트를 만듭니다. 요약표 값이 null이면 그 칸을 비웁니다. */
    private static Sheet overviewOnly(String wholePeriod, Double yearTotal, Double laterTotal) {
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet("① (정보화사업) 1-1. 정보화사업 개요");
            Row nameRow = sheet.createRow(0);
            cell(nameRow, 2).setCellValue("사업명");
            cell(nameRow, 3).setCellValue("사업");
            Row amountRow = sheet.createRow(1);
            cell(amountRow, 7).setCellValue("총 사업금액(전체기간)");
            cell(amountRow, 9).setCellValue(wholePeriod);
            if (yearTotal != null || laterTotal != null) {
                Row header = sheet.createRow(2);
                cell(header, 6).setCellValue("'26년도 합계");
                cell(header, 7).setCellValue("'26년도 이후");
                Row totalRow = sheet.createRow(4);
                cell(totalRow, 0).setCellValue("총 계");
                if (yearTotal != null) cell(totalRow, 6).setCellValue(yearTotal);
                if (laterTotal != null) cell(totalRow, 7).setCellValue(laterTotal);
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return new HSSFWorkbook(new ByteArrayInputStream(out.toByteArray())).getSheetAt(0);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Cell cell(Row row, int colIndex) {
        Cell existing = row.getCell(colIndex);
        return existing == null ? row.createCell(colIndex) : existing;
    }
}
