package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SheetAnchorScannerTest {

    private final SheetAnchorScanner scanner = new SheetAnchorScanner();
    private final WorkbookReader reader = new WorkbookReader(10_485_760L, 20, 5000);

    private Sheet sheetOf(byte[] bytes, FormSheetKind kind) {
        Workbook workbook = reader.open(bytes, "픽스처.xls");
        return reader.classify(workbook).get(kind);
    }

    @Test
    @DisplayName("라벨이 있는 행을 찾아 오른쪽 값을 읽는다")
    void readsValueRightOfLabel() {
        Sheet sheet = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.CAPITAL_OVERVIEW);

        int row = scanner.findLabelRow(sheet, new int[] {0, 2}, "사업명").orElseThrow();

        assertThat(scanner.valueRightOf(sheet, row, 0)).contains("국채전문유통시장 접속인프라 도입");
    }

    @Test
    @DisplayName("행이 밀린 파일에서도 같은 라벨을 찾는다")
    void findsLabelRegardlessOfRowShift() {
        Sheet shifted = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.CAPITAL_OVERVIEW);
        Sheet compact =
                sheetOf(RequestFormFixtures.capitalOnlyXlsx(), FormSheetKind.CAPITAL_OVERVIEW);

        int shiftedRow = scanner.findLabelRow(shifted, new int[] {0, 2}, "주관부서/팀").orElseThrow();
        int compactRow = scanner.findLabelRow(compact, new int[] {0, 2}, "주관부서/팀").orElseThrow();

        assertThat(shiftedRow).isNotEqualTo(compactRow);
        assertThat(scanner.valueRightOf(shifted, shiftedRow, 2)).contains("자금운용실/원화유가증권팀");
        assertThat(scanner.valueRightOf(compact, compactRow, 2)).contains("자금운용실/원화유가증권팀");
    }

    @Test
    @DisplayName("여러 행에 걸친 값을 줄바꿈으로 잇는다")
    void joinsMultiRowValue() {
        Sheet sheet = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.CAPITAL_OVERVIEW);
        int start = scanner.findLabelRow(sheet, new int[] {0}, "사업 범위 (전산 요구사항)").orElseThrow();

        String joined = scanner.joinedValueRightOf(sheet, start, start + 4, 0);

        assertThat(joined).contains("전용망 거래 기능").contains("추가 요구사항 1");
    }

    @Test
    @DisplayName("헤더를 찾아 컬럼 id 별 열 번호 맵을 만든다")
    void mapsHeaderColumns() {
        Sheet sheet = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.CAPITAL_RESOURCE);
        Map<String, List<String>> aliases =
                Map.of(
                        "item", List.of("항목", "Item"),
                        "qty", List.of("수량", "Qty"),
                        "currency", List.of("통화", "Currency"),
                        "amount", List.of("소요예산", "Budget"));

        SheetAnchorScanner.HeaderMap header =
                scanner.findHeader(sheet, 0, aliases, "item", "qty", "currency", "amount")
                        .orElseThrow();

        assertThat(header.rowIndex()).isEqualTo(9);
        assertThat(header.column("item")).isEqualTo(3);
        assertThat(header.column("qty")).isEqualTo(4);
        assertThat(header.column("amount")).isEqualTo(7);
    }

    @Test
    @DisplayName("두 번째 헤더 블록은 첫 헤더 다음 행부터 다시 찾는다")
    void findsSecondHeaderBlock() {
        Sheet sheet = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.CAPITAL_RESOURCE);
        Map<String, List<String>> aliases = Map.of("amount", List.of("연간 소요예산"));

        SheetAnchorScanner.HeaderMap second =
                scanner.findHeader(sheet, 10, aliases, "amount").orElseThrow();

        assertThat(second.rowIndex()).isEqualTo(18);
    }

    @Test
    @DisplayName("영문 헤더도 같은 별칭 목록으로 찾는다")
    void findsEnglishHeader() {
        Sheet sheet = sheetOf(RequestFormFixtures.englishFormXls(), FormSheetKind.GENERAL_EXPENSE);
        Map<String, List<String>> aliases =
                Map.of(
                        "annual", List.of("연간", "Annual"),
                        "counterparty", List.of("상대처", "Counterparty"));

        SheetAnchorScanner.HeaderMap header =
                scanner.findHeader(sheet, 0, aliases, "annual", "counterparty").orElseThrow();

        assertThat(header.column("annual")).isEqualTo(5);
        assertThat(header.column("counterparty")).isEqualTo(6);
    }

    @Test
    @DisplayName("필수 컬럼이 하나라도 없으면 헤더로 인정하지 않는다")
    void rejectsIncompleteHeader() {
        Sheet sheet = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.CAPITAL_RESOURCE);
        Map<String, List<String>> aliases = Map.of("nowhere", List.of("존재하지 않는 헤더"));

        assertThat(scanner.findHeader(sheet, 0, aliases, "nowhere")).isEmpty();
    }

    @Test
    @DisplayName("공백과 전각 공백을 제거해 비교한다")
    void normalizesWhitespaceAndFullWidth() {
        assertThat(SheetAnchorScanner.normalize(" 비 목 명 ")).isEqualTo("비목명");
        assertThat(SheetAnchorScanner.normalize("전산　임차료")).isEqualTo("전산임차료");
        assertThat(SheetAnchorScanner.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("병합 영역 안의 셀은 좌상단 값을 돌려준다")
    void readsMergedRegionAnchor() {
        Sheet sheet = sheetOf(RequestFormFixtures.fullFormXls(), FormSheetKind.RECURRING);

        // 픽스처에서 `계` 행이 A10:B10으로 병합돼 있다(0-based 10행)
        assertThat(scanner.text(sheet, 10, 1)).isEqualTo("계");
    }
}
