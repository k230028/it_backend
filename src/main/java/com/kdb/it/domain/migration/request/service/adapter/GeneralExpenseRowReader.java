package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * 시트 ③의 표를 읽습니다.
 *
 * <p>A·B열(비목명·세부비목)은 병합이라 빈 행이 위 값을 이어받습니다(forward-fill). 계약명과 연간 금액이 모두 빈 행은 서식만 남은 잔재로 보아 버립니다 —
 * 실측 시트가 6만 5천 행까지 서식을 달고 있었습니다.
 */
final class GeneralExpenseRowReader {

    private final Sheet sheet;
    private final SheetAnchorScanner.HeaderMap header;
    private final SheetAnchorScanner scanner;

    GeneralExpenseRowReader(
            Sheet sheet, SheetAnchorScanner.HeaderMap header, SheetAnchorScanner scanner) {
        this.sheet = sheet;
        this.header = header;
        this.scanner = scanner;
    }

    /**
     * 데이터 행을 전부 읽습니다.
     *
     * @return forward-fill을 마친 행 목록. 데이터가 없으면 빈 목록
     */
    List<GeneralExpenseRow> readAll() {
        int expenseCol = header.column("expense");
        int detailCol = expenseCol + 1;
        int firstDataRow = header.rowIndex() + (hasSubHeaderRow() ? 2 : 1);

        List<GeneralExpenseRow> rows = new ArrayList<>();
        String lastMid = "";
        String lastDetail = "";
        for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            String contractName = FormText.singleLineName(cell(rowIndex, "contractName"));
            BigDecimal annual = number(rowIndex, "annual");
            if (contractName.isEmpty() && annual == null) continue;

            String mid = scanner.text(sheet, rowIndex, expenseCol);
            String detail = scanner.text(sheet, rowIndex, detailCol);
            if (!mid.isEmpty()) lastMid = mid;
            if (!detail.isEmpty()) lastDetail = detail;

            rows.add(
                    new GeneralExpenseRow(
                            rowIndex + 1,
                            lastMid,
                            lastDetail,
                            contractName,
                            cell(rowIndex, "currency"),
                            number(rowIndex, "monthly"),
                            annual,
                            cell(rowIndex, "counterparty"),
                            cell(rowIndex, "continued"),
                            cell(rowIndex, "isNew"),
                            cell(rowIndex, "infoSec"),
                            cell(rowIndex, "remarks")));
        }
        return rows;
    }

    /**
     * 헤더 다음 행이 `월간`·`연간` 같은 하위 라벨 행인지 판정합니다.
     *
     * <p>양식의 헤더가 2행에 걸치므로 데이터 시작 행이 헤더 다음 다음 행입니다. 이 판정을 빼면 하위 라벨 행이 데이터로 읽힙니다.
     */
    private boolean hasSubHeaderRow() {
        Integer annualCol = header.column("annual");
        if (annualCol == null) return false;
        String below =
                SheetAnchorScanner.normalize(scanner.text(sheet, header.rowIndex() + 1, annualCol));
        return below.equals("연간") || below.equalsIgnoreCase("Annual");
    }

    private String cell(int rowIndex, String columnId) {
        Integer col = header.column(columnId);
        return col == null ? "" : scanner.text(sheet, rowIndex, col);
    }

    private BigDecimal number(int rowIndex, String columnId) {
        String raw = cell(rowIndex, columnId).replace(",", "").trim();
        if (raw.isEmpty()) return null;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
