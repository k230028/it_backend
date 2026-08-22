package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 시트 상단 머리말의 확인자·작성자 읽기를 고정합니다.
 *
 * <p>실 제출본이 두 표기를 섞어 씁니다 — 라벨과 이름이 다른 칸인 형태(시트 ②)와 같은 칸에 붙은 형태(시트 ③)입니다.
 */
class FormApproverReaderTest {

    private final SheetAnchorScanner scanner = new SheetAnchorScanner();
    private final FormApproverReader reader = new FormApproverReader(scanner);

    /** `(행, 열) → 값` 배치로 시트를 만듭니다. */
    private static Sheet sheetOf(Map<String, String> cells) {
        Workbook wb = new HSSFWorkbook();
        Sheet sheet = wb.createSheet("② (경상사업) 2. 경상적인 사업");
        cells.forEach(
                (coordinate, value) -> {
                    String[] parts = coordinate.split(",");
                    int rowIndex = Integer.parseInt(parts[0]);
                    int colIndex = Integer.parseInt(parts[1]);
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) row = sheet.createRow(rowIndex);
                    row.createCell(colIndex).setCellValue(value);
                });
        return sheet;
    }

    @Test
    @DisplayName("확인자 이름에 본부장이 포함되어도 사람 이름을 보존한다")
    void preservesConfirmerNameContainingHeadquartersTitle() {
        Sheet sheet = sheetOf(Map.of("1,6", "(확인자)", "1,7", "홍길동 본부장"));

        assertThat(reader.confirmer(sheet)).isEqualTo("홍길동 본부장");
    }

    @Test
    @DisplayName("작성자 이름에 본부장이 포함되어도 사람 이름을 보존한다")
    void preservesAuthorNameContainingHeadquartersText() {
        Sheet sheet = sheetOf(Map.of("1,8", "(작성자)", "1,9", "김영희 본부장"));

        assertThat(reader.author(sheet)).isEqualTo("김영희 본부장");
    }

    @Test
    @DisplayName("라벨 오른쪽 칸의 이름을 읽는다")
    void readsNameFromCellToTheRight() {
        Sheet sheet =
                sheetOf(
                        Map.of(
                                "1,6", "(확인자)",
                                "1,7", "신원석 부부장",
                                "1,8", "(작성자)",
                                "1,9", "김준영 차장"));

        assertThat(reader.confirmer(sheet)).isEqualTo("신원석 부부장");
        assertThat(reader.author(sheet)).isEqualTo("김준영 차장");
    }

    @Test
    @DisplayName("라벨과 이름이 같은 칸에 붙어 있어도 읽는다")
    void readsNameFromTheSameCell() {
        Sheet sheet = sheetOf(Map.of("1,10", "(작성자) 최민호 대리"));

        assertThat(reader.author(sheet)).isEqualTo("최민호 대리");
    }

    @Test
    @DisplayName("담당자·실무자·작성자가 모두 있으면 담당자를 우선한다")
    void prefersManagerOverStaffAndAuthor() {
        Sheet sheet =
                sheetOf(
                        Map.of(
                                "0,1", "작성자: 김작성 차장",
                                "1,1", "(실무자)",
                                "1,2", "박실무 대리",
                                "2,1", "담당자",
                                "2,2", "이담당 과장"));

        assertThat(reader.author(sheet)).isEqualTo("이담당 과장");
    }

    @Test
    @DisplayName("담당자가 없으면 실무자를 작성자보다 우선한다")
    void prefersStaffOverAuthor() {
        Sheet sheet = sheetOf(Map.of("0,1", "(작성자) 김작성 차장", "1,1", "실무자: 박실무 대리"));

        assertThat(reader.author(sheet)).isEqualTo("박실무 대리");
    }

    @Test
    @DisplayName("복합 라벨에 실무자가 포함되면 담당자 이름을 읽는다")
    void readsPersonWhenLabelContainsStaff() {
        Sheet sheet = sheetOf(Map.of("1,8", "(작성실무자)", "1,9", "김실무 대리"));

        assertThat(reader.author(sheet)).isEqualTo("김실무 대리");
    }

    @Test
    @DisplayName("적혀 있지 않으면 비워 둔다")
    void returnsNullWhenAbsent() {
        // 다른 사람으로 대신 채우면 원장에 사실이 아닌 담당자가 남는다
        assertThat(reader.confirmer(sheetOf(Map.of("1,0", "2. 경상적인 사업")))).isNull();
        assertThat(reader.author(sheetOf(Map.of("1,0", "2. 경상적인 사업")))).isNull();
    }

    @Test
    @DisplayName("라벨만 있고 이름이 비면 옆 라벨을 값으로 삼지 않는다")
    void doesNotTakeNextLabelAsName() {
        Sheet sheet = sheetOf(Map.of("1,6", "(확인자)", "1,8", "(작성자)", "1,9", "김준영 차장"));

        assertThat(reader.confirmer(sheet)).isNull();
        assertThat(reader.author(sheet)).isEqualTo("김준영 차장");
    }

    @Test
    @DisplayName("표 안쪽에 같은 문구가 있어도 상단 머리말만 본다")
    void looksOnlyAtTheTopRows() {
        Sheet sheet = sheetOf(Map.of("20,6", "(확인자)", "20,7", "표 안쪽 사람"));

        assertThat(reader.confirmer(sheet)).isNull();
    }
}
