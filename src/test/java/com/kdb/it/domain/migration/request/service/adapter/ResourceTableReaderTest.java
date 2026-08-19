package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.request.service.WorkbookReader;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import java.math.BigDecimal;
import java.util.Optional;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResourceTableReaderTest {

    private final WorkbookReader workbookReader = new WorkbookReader(10_485_760L, 20, 5000);
    private final ResourceTableReader reader = new ResourceTableReader(new SheetAnchorScanner());

    private Sheet sheetOf(byte[] bytes, FormSheetKind kind) {
        return workbookReader.classify(workbookReader.open(bytes, "픽스처.xls")).get(kind);
    }

    private static ResourceRow row(String currency, BigDecimal amount, String timing) {
        return new ResourceRow(
                11,
                "기계장치(HW)",
                "서버",
                BigDecimal.ONE,
                amount,
                currency,
                amount,
                "근거",
                timing,
                "Y",
                "N",
                "");
    }

    @Test
    @DisplayName("자본예산 블록은 다음 블록 헤더에서 멈춘다")
    void capitalBlockStopsAtNextHeader() {
        Sheet sheet = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.CAPITAL_RESOURCE);

        ResourceTableReader.Result capital =
                reader.readCapitalResource(sheet, 0, false).orElseThrow();

        // 픽스처의 자본예산 블록은 2행. 일반관리비 블록(18행 헤더)의 행을 삼키면 3행이 된다.
        assertThat(capital.rows()).hasSize(2);
        assertThat(capital.headerRow()).isEqualTo(9);
    }

    @Test
    @DisplayName("일반관리비 블록은 첫 블록 다음 행부터 따로 읽는다")
    void generalBlockIsReadSeparately() {
        Sheet sheet = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.CAPITAL_RESOURCE);
        int firstHeader = reader.readCapitalResource(sheet, 0, false).orElseThrow().headerRow();

        ResourceTableReader.Result general =
                reader.readCapitalResource(sheet, firstHeader + 1, true).orElseThrow();

        assertThat(general.rows()).extracting(ResourceRow::itemName).containsExactly("전용망 회선 이용료");
    }

    @Test
    @DisplayName("계 행에서 표를 끝내고 합계는 품목으로 만들지 않는다")
    void stopsAtTotalRow() {
        Sheet sheet = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.RECURRING);

        ResourceTableReader.Result table = reader.readRecurring(sheet).orElseThrow();

        assertThat(table.rows()).extracting(ResourceRow::itemName).doesNotContain("계");
        assertThat(table.rows()).hasSize(2);
    }

    @Test
    @DisplayName("표를 못 찾으면 빈 Optional을 돌려준다")
    void returnsEmptyWhenTableAbsent() {
        Sheet overview = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.CAPITAL_OVERVIEW);

        Optional<ResourceTableReader.Result> result =
                reader.readCapitalResource(overview, 0, false);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("1-2는 항목 열 위치와 무관하게 C열을 비목으로 읽는다")
    void takesCapitalResourceGroupFromColumnC() {
        // `소요예산 | 대분류 | 중분류 | 항목` 고정 배치. 항목 열 기준으로 한 칸 왼쪽을 잡으면
        // 부점이 열을 끼워 넣은 파일에서 대분류나 빈 열을 비목으로 읽어 조용히 어긋난다.
        Sheet sheet = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.CAPITAL_RESOURCE);

        ResourceTableReader.Result capital =
                reader.readCapitalResource(sheet, 0, false).orElseThrow();

        assertThat(capital.rows())
                .extracting(ResourceRow::group)
                .allSatisfy(group -> assertThat(group).isNotEmpty());
    }

    @Test
    @DisplayName("신양식은 항목 C열 바로 왼쪽 B열을 비목으로 읽는다")
    void takesCompactCapitalResourceGroupFromColumnB() {
        Sheet sheet = capitalSheet(1, 2, "기계장치(HW)", "서버(일체)");

        ResourceTableReader.Result result =
                reader.readCapitalResource(sheet, 0, false).orElseThrow();

        assertThat(result.rows()).extracting(ResourceRow::group).containsExactly("기계장치(HW)");
    }

    @Test
    @DisplayName("항목 앞에 보조 열이 있어도 기존 C열 비목을 유지한다")
    void keepsCapitalResourceGroupInColumnCWhenItemMovesRight() {
        Sheet sheet = capitalSheet(2, 4, "기타무형자산(SW)", "테스트 자동화 솔루션");

        ResourceTableReader.Result result =
                reader.readCapitalResource(sheet, 0, false).orElseThrow();

        assertThat(result.rows()).extracting(ResourceRow::group).containsExactly("기타무형자산(SW)");
    }

    @Test
    @DisplayName("지급주기 표기를 코드로 바꾸고 주기 열이 없으면 해당없음을 쓴다")
    void mapsPaymentCycle() {
        assertThat(toItem(row("KRW", BigDecimal.TEN, "월")).getDfrCleC()).isEqualTo("M");
        assertThat(toItem(row("KRW", BigDecimal.TEN, "분기")).getDfrCleC()).isEqualTo("Q");
        assertThat(toItem(row("KRW", BigDecimal.TEN, "반기")).getDfrCleC()).isEqualTo("H");
        assertThat(toItem(row("KRW", BigDecimal.TEN, "년")).getDfrCleC()).isEqualTo("Y");
        assertThat(toItem(row("KRW", BigDecimal.TEN, "2분기 중")).getDfrCleC()).isEqualTo("Q");
        assertThat(toItem(row("KRW", BigDecimal.TEN, "")).getDfrCleC())
                .isEqualTo(ResourceTableReader.CYCLE_NOT_APPLICABLE);
    }

    @Test
    @DisplayName("도입시기에서 추진년월을 뽑고 못 뽑으면 비워 둔다")
    void extractsBudgetYearMonth() {
        assertThat(toItem(row("KRW", BigDecimal.TEN, "~26.2월")).getBseYm()).isEqualTo("202602");
        assertThat(toItem(row("KRW", BigDecimal.TEN, "12월")).getBseYm()).isEqualTo("202612");
        assertThat(toItem(row("KRW", BigDecimal.TEN, "연중")).getBseYm()).isNull();
        assertThat(toItem(row("KRW", BigDecimal.TEN, "13월")).getBseYm()).isNull();
    }

    @Test
    @DisplayName("JPY만 천엔을 엔으로 펴고 나머지 외화는 그대로 둔다")
    void expandsOnlyJapaneseYen() {
        assertThat(toItem(row("JPY", new BigDecimal("100"), "년")).getFcAmt())
                .isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(toItem(row("GBP", new BigDecimal("100"), "년")).getFcAmt())
                .isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("원화 행은 AMT에, 외화 행은 FC_AMT에만 담는다")
    void splitsAmountByCurrency() {
        ProjectDto.BitemmDto krw = toItem(row("KRW", new BigDecimal("500"), "년"));
        ProjectDto.BitemmDto gbp = toItem(row("GBP", new BigDecimal("500"), "년"));

        assertThat(krw.getAmt()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(krw.getFcAmt()).isNull();
        assertThat(gbp.getAmt()).isNull();
        assertThat(gbp.getFcAmt()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(gbp.getXcr()).isNull();
    }

    @Test
    @DisplayName("경상사업 통화코드는 공백과 대소문자를 정규화한다")
    void normalizesRecurringProjectCurrencyCode() {
        ProjectDto.BitemmDto gbp = toItem(row(" gbp\u00A0", new BigDecimal("500"), "년"));

        assertThat(gbp.getCurC()).isEqualTo("GBP");
        assertThat(gbp.getAmt()).isNull();
        assertThat(gbp.getFcAmt()).isEqualByComparingTo(new BigDecimal("500"));
    }

    private static ProjectDto.BitemmDto toItem(ResourceRow row) {
        return ResourceTableReader.toItem(row, "101", 1, "2026");
    }

    /** 그룹·항목 열 위치가 다른 자본예산 1-2 표를 만듭니다. */
    private static Sheet capitalSheet(
            int groupColumn, int itemColumn, String group, String itemName) {
        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet("① (정보화사업) 1-2. 소요자원 상세내용");
        Row header = sheet.createRow(0);
        cell(header, itemColumn).setCellValue("항목");
        cell(header, itemColumn + 1).setCellValue("수량");
        cell(header, itemColumn + 2).setCellValue("단가");
        cell(header, itemColumn + 3).setCellValue("통화");
        cell(header, itemColumn + 4).setCellValue("소요예산 (부가세포함)");
        cell(header, itemColumn + 5).setCellValue("산정근거");
        cell(header, itemColumn + 6).setCellValue("도입시기");
        cell(header, itemColumn + 7).setCellValue("정보보호여부");
        cell(header, itemColumn + 8).setCellValue("인프라 통합관리 여부");
        cell(header, itemColumn + 9).setCellValue("비고(적용 환율 등)");

        Row item = sheet.createRow(1);
        cell(item, groupColumn).setCellValue(group);
        cell(item, itemColumn).setCellValue(itemName);
        cell(item, itemColumn + 1).setCellValue(1);
        cell(item, itemColumn + 2).setCellValue(100);
        cell(item, itemColumn + 3).setCellValue("KRW");
        cell(item, itemColumn + 4).setCellValue(100);
        return sheet;
    }

    private static Cell cell(Row row, int column) {
        return row.createCell(column);
    }
}
