package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.kdb.it.domain.migration.request.support.FormCheckboxFixtures;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 양식 컨트롤 체크박스 추출을 두 형식 모두에서 고정합니다.
 *
 * <p>1-1 시트의 `업무구분`·`사업유형` 같은 항목은 셀이 비어 있고 체크박스로만 표시되므로, 이 추출이 실패하면 해당 항목 전체가 조용히 공란으로 반입됩니다. POI에
 * 체크박스 생성 API가 없어 픽스처를 저수준으로 만듭니다({@link FormCheckboxFixtures}).
 *
 * <p>좌표·문구 선택 규칙은 {@link CheckboxFieldReaderTest}가 담당합니다.
 */
class FormCheckboxReaderTest {

    private final FormCheckboxReader reader = new FormCheckboxReader();

    @Test
    @DisplayName(".xls는 Escher 도형에서 체크박스만 골라 좌표·문구·체크상태를 읽는다")
    void readsBiff8Checkboxes() throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            HSSFSheet sheet = FormCheckboxFixtures.biff8SheetWithCheckboxes(workbook);

            List<FormCheckbox> boxes = reader.read(sheet);

            // 콤보 도형과 앵커 없는 도형은 걸러진다
            assertThat(boxes)
                    .extracting(
                            FormCheckbox::rowIndex,
                            FormCheckbox::colIndex,
                            FormCheckbox::caption,
                            FormCheckbox::checked)
                    .containsExactly(
                            tuple(17, 2, "여신", true),
                            tuple(17, 4, "", false),
                            // FtCblsData가 없으면 상태를 알 수 없어 해제로 접는다
                            tuple(17, 8, "국제", false));
        }
    }

    @Test
    @DisplayName(".xls에 도형이 아예 없으면 빈 목록을 낸다")
    void readsEmptyWhenNoDrawing() throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            assertThat(reader.read(workbook.createSheet("도형없음"))).isEmpty();
        }
    }

    @Test
    @DisplayName(".xlsx는 시트 XML·ctrlProps·VML을 이어 붙여 체크박스를 읽는다")
    void readsOoxmlCheckboxes() throws IOException {
        try (XSSFWorkbook workbook =
                new XSSFWorkbook(new ByteArrayInputStream(FormCheckboxFixtures.checkboxXlsx()))) {
            List<FormCheckbox> boxes = reader.read(workbook.getSheetAt(0));

            assertThat(boxes)
                    .extracting(
                            FormCheckbox::rowIndex,
                            FormCheckbox::colIndex,
                            FormCheckbox::caption,
                            FormCheckbox::checked)
                    .containsExactly(
                            // 문구는 VML 텍스트박스에서 오고 공백은 한 칸으로 접는다
                            tuple(17, 2, "여신", true),
                            // Excel은 해제 상태에서 checked 속성을 쓰지 않는다
                            tuple(17, 4, "", false),
                            // 행 번호가 숫자가 아니면 좌표를 -1로 둔다
                            tuple(-1, 4, "", true),
                            // VML에 문구 도형이 없는 컨트롤은 빈 문구로 남는다
                            tuple(17, 10, "", true));
        }
    }

    @Test
    @DisplayName(".xlsx에 컨트롤이 없는 시트는 빈 목록을 낸다")
    void readsEmptyWhenNoControls() throws IOException {
        try (XSSFWorkbook workbook =
                new XSSFWorkbook(new ByteArrayInputStream(FormCheckboxFixtures.checkboxXlsx()))) {
            assertThat(reader.read(workbook.getSheetAt(1))).isEmpty();
        }
    }

    @Test
    @DisplayName("두 형식이 아닌 시트 구현은 빈 목록을 낸다")
    void readsEmptyForUnknownSheetType() {
        assertThat(reader.read(Mockito.mock(Sheet.class))).isEmpty();
    }

    @Test
    @DisplayName("읽는 중 예외가 나도 배치를 무너뜨리지 않고 빈 목록으로 접는다")
    void foldsFailureIntoEmptyList() {
        // 체크박스를 못 읽는 것은 항목이 공란인 것과 같은 상태이고, 그 상태는 선택항목 경고로 이미 처리된다
        HSSFSheet broken = Mockito.mock(HSSFSheet.class);
        Mockito.when(broken.getDrawingPatriarch()).thenThrow(new IllegalStateException("깨진 도형"));
        Mockito.when(broken.getSheetName()).thenReturn("1-1");

        assertThat(reader.read(broken)).isEmpty();
    }
}
