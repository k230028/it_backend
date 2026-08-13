package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkbookReaderTest {

    private final WorkbookReader reader = new WorkbookReader(10_485_760L, 20, 5000);

    @Test
    @DisplayName("BIFF8(.xls) 워크북을 열고 4개 시트를 모두 판별한다")
    void opensBiff8AndClassifiesAllSheets() {
        Workbook workbook = reader.open(RequestFormFixtures.fullFormXls(), "요청서.xls");

        Map<FormSheetKind, Sheet> sheets = reader.classify(workbook);

        assertThat(sheets)
                .containsOnlyKeys(
                        FormSheetKind.CAPITAL_OVERVIEW,
                        FormSheetKind.CAPITAL_RESOURCE,
                        FormSheetKind.RECURRING,
                        FormSheetKind.GENERAL_EXPENSE);
    }

    @Test
    @DisplayName("OOXML(.xlsx) 워크북에서 채워진 시트만 판별한다")
    void opensOoxmlAndClassifiesPresentSheets() {
        Workbook workbook = reader.open(RequestFormFixtures.capitalOnlyXlsx(), "자료1.xlsx");

        assertThat(reader.classify(workbook))
                .containsOnlyKeys(FormSheetKind.CAPITAL_OVERVIEW, FormSheetKind.CAPITAL_RESOURCE);
    }

    @Test
    @DisplayName("확장자가 xlsx인데 내용이 BIFF8이면 매직바이트로 판단해 연다")
    void opensByMagicBytesNotByExtension() {
        Workbook workbook = reader.open(RequestFormFixtures.fullFormXls(), "위장.xlsx");

        assertThat(workbook.getNumberOfSheets()).isGreaterThan(0);
    }

    @Test
    @DisplayName("엑셀이 아닌 바이트는 WorkbookOpenException으로 거부한다")
    void rejectsNonExcelBytes() {
        byte[] notExcel = "이것은 엑셀이 아닙니다".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.open(notExcel, "가짜.xlsx"))
                .isInstanceOf(WorkbookReader.WorkbookOpenException.class)
                .hasMessageContaining("엑셀");
    }

    @Test
    @DisplayName("빈 바이트는 열기 전에 거부한다")
    void rejectsEmptyBytes() {
        assertThatThrownBy(() -> reader.open(new byte[0], "빈파일.xlsx"))
                .isInstanceOf(WorkbookReader.WorkbookOpenException.class)
                .hasMessageContaining("비어");
    }

    @Test
    @DisplayName("파일 크기 상한을 넘으면 열기 전에 거부한다")
    void rejectsOversizeBeforeParsing() {
        WorkbookReader tiny = new WorkbookReader(16L, 20, 5000);

        assertThatThrownBy(() -> tiny.open(RequestFormFixtures.fullFormXls(), "요청서.xls"))
                .isInstanceOf(WorkbookReader.WorkbookOpenException.class)
                .hasMessageContaining("크기");
    }

    @Test
    @DisplayName("시트 수 상한을 넘으면 거부한다")
    void rejectsTooManySheets() {
        WorkbookReader narrow = new WorkbookReader(10_485_760L, 2, 5000);

        assertThatThrownBy(() -> narrow.open(RequestFormFixtures.fullFormXls(), "요청서.xls"))
                .isInstanceOf(WorkbookReader.WorkbookOpenException.class)
                .hasMessageContaining("시트");
    }

    @Test
    @DisplayName("행 수 상한을 넘는 시트가 있으면 분류 단계에서 거부한다")
    void rejectsTooManyRows() {
        WorkbookReader shallow = new WorkbookReader(10_485_760L, 20, 3);
        Workbook workbook = shallow.open(RequestFormFixtures.fullFormXls(), "요청서.xls");

        assertThatThrownBy(() -> shallow.classify(workbook))
                .isInstanceOf(WorkbookReader.WorkbookOpenException.class)
                .hasMessageContaining("행 수");
    }
}
