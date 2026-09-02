package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.request.service.FormAmount;
import com.kdb.it.domain.migration.request.service.FormEnumeration;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * 시트 ③의 표를 읽습니다.
 *
 * <p>A·B열(비목명·세부비목)은 병합이라 빈 행이 위 값을 이어받습니다(forward-fill). 계약명과 연간 금액이 모두 빈 행은 서식만 남은 잔재로 보아 버립니다 —
 * 실측 시트가 6만 5천 행까지 서식을 달고 있었습니다.
 *
 * <p>부점이 표 끝(또는 비목 묶음마다)에 붙이는 <b>집계 행</b>은 원장 행이 아니므로 읽지 않습니다. 읽으면 비목·통화가 둘 다 미해석으로 떨어져 파일이 차단되고,
 * 차단을 사람이 풀어 주면 이번에는 같은 금액이 두 번 반입됩니다.
 */
final class GeneralExpenseRowReader {

    /**
     * 집계 행의 표기. 이 표기가 비목·세부비목·계약명 어디에 적혀 있어도 집계 행으로 봅니다.
     *
     * <p>비목명이나 계약명으로 쓰일 수 있는 말이 아니므로 오검출 여지가 없습니다.
     */
    private static final Set<String> SUMMARY_LABELS = Set.of("계", "소계", "합계", "총계", "총합계");

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
     * @return forward-fill을 마친 행 목록. 집계 행은 빠져 있습니다. 데이터가 없으면 빈 목록
     */
    List<GeneralExpenseRow> readAll() {
        int expenseCol = header.column("expense");
        int detailCol = expenseCol + 1;
        int firstDataRow = header.rowIndex() + (hasSubHeaderRow() ? 2 : 1);

        List<GeneralExpenseRow> rows = new ArrayList<>();
        String lastMid = "";
        String lastDetail = "";
        for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            String contractCell = cell(rowIndex, "contractName");
            String contractName =
                    Objects.requireNonNullElse(FormText.singleLineName(contractCell), "");
            FormAmount.Parsed annual = FormAmount.parse(cell(rowIndex, "annual"));
            if (contractName.isEmpty() && annual == null) continue;

            String mid = scanner.text(sheet, rowIndex, expenseCol);
            String detail = scanner.text(sheet, rowIndex, detailCol);
            // 집계 행은 forward-fill도 이어받지 않는다 — `계`를 물려주면 그 아래 행의 비목까지 함께 어긋난다
            if (isSummaryRow(mid, detail, contractName) || isBlankAroundCurrency(rowIndex))
                continue;
            if (!mid.isEmpty()) lastMid = mid;
            if (!detail.isEmpty()) lastDetail = detail;

            rows.addAll(rowsAt(rowIndex, contractCell, lastMid, lastDetail));
        }
        return rows;
    }

    /**
     * 엑셀 한 행에서 데이터 행을 만듭니다.
     *
     * <p>계약명 칸에 번호가 둘 이상이면 계약 여러 건을 묶어 적은 행이므로 항목 수만큼 나눕니다. 한 건으로 합치지 않는 이유는 계약명·상대처·계약구분이 항목마다 다르고
     * (실측: 미래전략개발부 ③ 6행은 ①만 신규, ②③은 계속) 전산업무비 원장이 계약 단위이기 때문입니다.
     *
     * @param rowIndex 0-based 행 번호
     * @param contractCell 계약명 칸 원문. 번호 체계의 기준입니다
     * @param mid forward-fill을 마친 비목명
     * @param detail forward-fill을 마친 세부비목
     * @return 데이터 행 목록. 번호가 없으면 1건
     */
    private List<GeneralExpenseRow> rowsAt(
            int rowIndex, String contractCell, String mid, String detail) {
        Optional<FormEnumeration> enumeration = FormEnumeration.of(contractCell);
        if (enumeration.isEmpty()) {
            return List.of(
                    rowOf(
                            rowIndex,
                            mid,
                            detail,
                            FormText.singleLineName(contractCell),
                            cell(rowIndex, "currency"),
                            cell(rowIndex, "monthly"),
                            cell(rowIndex, "annual"),
                            cell(rowIndex, "counterparty"),
                            cell(rowIndex, "continued"),
                            cell(rowIndex, "isNew"),
                            cell(rowIndex, "infoSec"),
                            cell(rowIndex, "remarks")));
        }

        FormEnumeration items = enumeration.get();
        List<String> contractNames = items.split(contractCell);
        List<String> currencies = items.split(cell(rowIndex, "currency"));
        List<String> monthlies = items.split(cell(rowIndex, "monthly"));
        List<String> annuals = items.split(cell(rowIndex, "annual"));
        List<String> counterparties = items.split(cell(rowIndex, "counterparty"));
        List<String> continued = items.split(cell(rowIndex, "continued"));
        List<String> isNew = items.split(cell(rowIndex, "isNew"));
        List<String> infoSec = items.split(cell(rowIndex, "infoSec"));
        List<String> remarks = items.split(cell(rowIndex, "remarks"));

        List<GeneralExpenseRow> rows = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            rows.add(
                    rowOf(
                            rowIndex,
                            mid,
                            detail,
                            FormText.singleLineName(contractNames.get(i)),
                            currencies.get(i),
                            monthlies.get(i),
                            annuals.get(i),
                            counterparties.get(i),
                            continued.get(i),
                            isNew.get(i),
                            infoSec.get(i),
                            remarks.get(i)));
        }
        return rows;
    }

    /** 칸 원문을 데이터 행으로 옮깁니다. 금액 칸은 값과 그 칸이 밝힌 단위를 함께 읽습니다. */
    private GeneralExpenseRow rowOf(
            int rowIndex,
            String mid,
            String detail,
            String contractName,
            String currency,
            String monthlyCell,
            String annualCell,
            String counterparty,
            String continued,
            String isNew,
            String infoSec,
            String remarks) {
        FormAmount.Parsed annual = FormAmount.parse(annualCell);
        FormAmount.Parsed monthly = FormAmount.parse(monthlyCell);
        FormAmount.Parsed amount = annual != null ? annual : monthly;
        String resolvedContractName =
                contractName == null || contractName.isBlank()
                        ? firstNonBlank(detail, mid)
                        : contractName;
        return new GeneralExpenseRow(
                rowIndex + 1,
                mid,
                detail,
                resolvedContractName,
                currency,
                monthly == null ? null : monthly.value(),
                annual == null ? null : annual.value(),
                amount == null ? null : amount.unit(),
                counterparty,
                continued,
                isNew,
                infoSec,
                remarks);
    }

    /** 계약명이 비어 있으면 더 구체적인 세부비목, 비목 순으로 계약명을 보완합니다. */
    private static String firstNonBlank(String detail, String mid) {
        if (detail != null && !detail.isBlank()) return detail.trim();
        return mid == null ? "" : mid.trim();
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

    /**
     * 집계 행인지 판정합니다.
     *
     * <p>세 칸을 모두 보는 이유는 부점마다 `계`를 적는 자리가 다르기 때문입니다 — A:B를 병합해 비목 자리에 적기도 하고(그러면 병합 좌상단 값이 두 칸 모두로
     * 읽힙니다) 계약명 칸에 적기도 합니다.
     *
     * @param mid A열 비목명 원문 (forward-fill 이전)
     * @param detail B열 세부비목 원문 (forward-fill 이전)
     * @param contractName C열 계약명
     * @return 집계 행이면 true
     */
    private static boolean isSummaryRow(String mid, String detail, String contractName) {
        return isSummaryLabel(mid) || isSummaryLabel(detail) || isSummaryLabel(contractName);
    }

    private static boolean isSummaryLabel(String value) {
        String normalized = SheetAnchorScanner.normalize(value);
        if (SUMMARY_LABELS.contains(normalized)) return true;
        return SUMMARY_LABELS.stream()
                .anyMatch(
                        label ->
                                normalized.startsWith(label + "(")
                                        || normalized.startsWith(label + "（"));
    }

    /**
     * 통화 구분 칸의 좌우가 모두 빈 행인지 판정합니다.
     *
     * <p>양식은 통화 구분 왼쪽에 계약명, 오른쪽에 월간 소요예산을 둡니다. 둘 다 비어 있으면 계약 한 건을 적은 행이 아니라 연간 칸에 금액만 얹은 집계 행이거나 표를
     * 늘리다 만 잔재입니다. 그대로 읽으면 통화 칸도 비어 있어 `통화 구분이 비어 있습니다` 차단이 납니다.
     *
     * <p>병합은 {@link SheetAnchorScanner#text}가 좌상단 값으로 풀어 주므로, 계약명을 위 행과 세로 병합한 정상 행은 여기 걸리지 않습니다.
     *
     * @param rowIndex 0-based 행 번호
     * @return 좌우가 모두 비어 있으면 true. 통화 열을 찾지 못했거나 맨 왼쪽 열이면 false
     */
    private boolean isBlankAroundCurrency(int rowIndex) {
        Integer currencyCol = header.column("currency");
        if (currencyCol == null || currencyCol == 0) return false;
        return scanner.text(sheet, rowIndex, currencyCol - 1).isEmpty()
                && scanner.text(sheet, rowIndex, currencyCol + 1).isEmpty();
    }

    private String cell(int rowIndex, String columnId) {
        Integer col = header.column(columnId);
        return col == null ? "" : scanner.text(sheet, rowIndex, col);
    }
}
