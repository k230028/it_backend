package com.kdb.it.domain.migration.request.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

/**
 * 편성요청서 시트에서 라벨·헤더를 찾아 값 위치를 정합니다.
 *
 * <p>실 제출본은 사업범위·추진경과 칸에 사용자가 행을 끼워 넣어 같은 항목이 파일마다 다른 행에 있습니다(실측 38행 대 41행). 그래서 고정 좌표 대신 라벨 텍스트로
 * 행을 찾고, 표는 헤더 텍스트 조합으로 열을 찾습니다. 비교는 항상 {@link #normalize(String)}를 거칩니다 — 양식에 `비 목 명`처럼 글자 사이 공백이나
 * 전각 공백이 섞여 있습니다.
 */
@Component
public class SheetAnchorScanner {

    /** 라벨 오른쪽에서 값을 찾을 때 훑는 최대 열 수. 양식의 가장 넓은 표가 12열이라 여유를 둡니다. */
    private static final int VALUE_SCAN_WIDTH = 16;

    private final DataFormatter formatter = new DataFormatter();

    /**
     * 셀 텍스트를 읽습니다. 병합 영역 안이면 좌상단 셀의 값을 돌려줍니다.
     *
     * <p>POI는 병합 영역의 좌상단이 아닌 셀을 빈 값으로 돌려주는데 양식이 병합을 많이 써서 그대로 두면 값이 사라집니다. 수식 셀은 캐시된 계산 결과를 쓰고 오류값은
     * 오류 코드 문자열(`#REF!` 등)을 그대로 남깁니다 — 빈 문자열로 접으면 "값 없음"과 구분되지 않아 깨진 수식을 아무도 알아채지 못합니다.
     *
     * @param sheet 대상 시트
     * @param rowIndex 0-based 행 번호. 음수면 빈 문자열
     * @param colIndex 0-based 열 번호. 음수면 빈 문자열
     * @return 셀 텍스트. 값이 없으면 빈 문자열
     */
    public String text(Sheet sheet, int rowIndex, int colIndex) {
        if (rowIndex < 0 || colIndex < 0) return "";
        Cell cell = cellAt(sheet, rowIndex, colIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            Cell anchor = mergedAnchor(sheet, rowIndex, colIndex);
            if (anchor == null) return "";
            cell = anchor;
        }
        if (cell.getCellType() == CellType.ERROR
                || (cell.getCellType() == CellType.FORMULA
                        && cell.getCachedFormulaResultType() == CellType.ERROR)) {
            return FormulaError.forInt(cell.getErrorCellValue()).getString();
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return formatCachedResult(cell);
        }
        return formatter.formatCellValue(cell).trim();
    }

    /**
     * 수식 셀의 <b>캐시된 계산 결과</b>를 문자열로 만듭니다.
     *
     * <p>{@code DataFormatter.formatCellValue(cell)}에 평가기를 주지 않으면 수식 셀은 계산 결과가 아니라 수식 원문을
     * 돌려줍니다(`SUM(E10*F10)`). 그 값이 숫자 파싱에 실패해 행이 통째로 버려지므로, 소요예산을 수식으로 적어 낸 제출본이 진단 한 줄 없이 사라집니다(실측:
     * 런던지점 시트 ②의 소요자원 7행 전량).
     *
     * <p>평가기로 <b>다시 계산하지 않습니다</b>. 참조가 깨진 수식이나 지원하지 않는 함수가 있으면 평가가 예외로 끝나 파일 전체를 잃는데, 부점이 저장한 시점의
     * 값은 이미 캐시에 들어 있어 그대로 쓰면 됩니다. 오류 결과는 호출부에서 이미 걸러 이 메서드로 오지 않습니다.
     *
     * @param cell 수식 셀
     * @return 캐시 결과를 셀 서식으로 그린 문자열. 숫자·문자열·논리값이 아니면 빈 문자열
     */
    private String formatCachedResult(Cell cell) {
        return switch (cell.getCachedFormulaResultType()) {
            case NUMERIC ->
                    formatter
                            .formatRawCellContents(
                                    cell.getNumericCellValue(),
                                    cell.getCellStyle().getDataFormat(),
                                    cell.getCellStyle().getDataFormatString())
                            .trim();
            case STRING -> cell.getRichStringCellValue().getString().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /**
     * 라벨이 있는 행을 찾습니다.
     *
     * <p>행 번호 0부터 마지막 번호까지가 아니라 <b>실재하는 행만</b> 오름차순으로 훑습니다. `.xls` 제출본에는 서식만 남은 빈 행이 시트 맨
     * 아래(65534행)에 붙어 있는 경우가 있어, 번호로 돌면 존재하지 않는 6만여 행마다 병합영역 목록을 새로 만들어 훑게 됩니다. 라벨은 병합 영역의 좌상단 셀에 있고
     * 그 행에는 행 레코드가 있으므로, 실재하는 행만 봐도 같은 행을 찾습니다.
     *
     * @param sheet 대상 시트
     * @param labelColumns 라벨이 있을 수 있는 0-based 열 번호들 (보통 `{0, 2}` — A열 대분류, C열 소분류)
     * @param labelAliases 같은 항목의 국문·영문 표기. 하나라도 일치하면 그 행
     * @return 0-based 행 번호. 없으면 빈 Optional
     */
    public Optional<Integer> findLabelRow(Sheet sheet, int[] labelColumns, String... labelAliases) {
        List<String> normalizedAliases = new ArrayList<>();
        for (String alias : labelAliases) normalizedAliases.add(normalize(alias));

        for (Row row : sheet) {
            for (int colIndex : labelColumns) {
                String candidate = normalize(text(sheet, row.getRowNum(), colIndex));
                if (!candidate.isEmpty() && normalizedAliases.contains(candidate)) {
                    return Optional.of(row.getRowNum());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 라벨 셀 오른쪽에서 처음 만나는 값을 읽습니다.
     *
     * @param sheet 대상 시트
     * @param rowIndex 라벨이 있는 행
     * @param labelColIndex 라벨이 있는 열. 이 열의 바로 오른쪽부터 훑습니다
     * @return 첫 비어 있지 않은 값. 없으면 빈 Optional
     */
    public Optional<String> valueRightOf(Sheet sheet, int rowIndex, int labelColIndex) {
        for (int colIndex = mergedEndColumn(sheet, rowIndex, labelColIndex) + 1;
                colIndex <= labelColIndex + VALUE_SCAN_WIDTH;
                colIndex++) {
            String value = text(sheet, rowIndex, colIndex);
            if (!value.isEmpty() && !isSubheadingLabel(value)) return Optional.of(value);
        }
        return Optional.empty();
    }

    /**
     * 셀이 속한 병합 영역의 마지막 열을 반환합니다. 값 탐색의 시작점을 정하는 데 씁니다.
     *
     * <p>실 제출본은 라벨 칸을 두 열에 걸쳐 병합합니다(`사업명`이 A:B). 병합 영역 안의 셀은 {@link #text}가 좌상단 값을 돌려주므로, 라벨 바로 오른쪽
     * 칸을 값으로 읽으면 <b>라벨 자신</b>이 나옵니다. 그 값을 다음 라벨로 판정해 탐색을 접으면 항목이 통째로 비고, 1-1 시트를 낸 파일에서 사업이 하나도 생기지
     * 않습니다(실측: IT기획부·자금운용실 두 건 모두).
     *
     * @param sheet 대상 시트
     * @param rowIndex 0-based 행 번호
     * @param colIndex 0-based 열 번호
     * @return 병합 영역의 마지막 열. 병합이 아니면 {@code colIndex} 그대로
     */
    public int mergedEndColumn(Sheet sheet, int rowIndex, int colIndex) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIndex, colIndex)) return region.getLastColumn();
        }
        return colIndex;
    }

    /**
     * 여러 행에 걸친 값을 줄바꿈으로 이어 붙입니다.
     *
     * <p>사업범위·추진경과처럼 한 항목이 여러 행에 나뉘어 적히는 칸에 씁니다. 병합 때문에 같은 값이 연속으로 반복되면 한 번만 담습니다.
     *
     * @param sheet 대상 시트
     * @param startRow 시작 행 (포함)
     * @param endRowExclusive 끝 행 (제외)
     * @param labelColIndex 라벨 열. 각 행에서 이 열의 오른쪽 값을 읽습니다
     * @return 줄바꿈으로 이은 값. 어느 행에도 값이 없으면 빈 문자열
     */
    public String joinedValueRightOf(
            Sheet sheet, int startRow, int endRowExclusive, int labelColIndex) {
        List<String> lines = new ArrayList<>();
        int last = Math.min(endRowExclusive - 1, sheet.getLastRowNum());
        for (int rowIndex = startRow; rowIndex <= last; rowIndex++) {
            Optional<String> value = valueRightOf(sheet, rowIndex, labelColIndex);
            if (value.isEmpty()) continue;
            String line = value.get();
            if (lines.isEmpty() || !lines.get(lines.size() - 1).equals(line)) lines.add(line);
        }
        return String.join("\n", lines);
    }

    /**
     * 표 헤더를 찾아 컬럼 id 별 열 번호 맵을 만듭니다.
     *
     * <p>양식은 헤더가 1~2행에 걸치고 병합이 섞여 있어, 헤더 후보 행과 그 다음 행을 함께 훑어 열별 표기를 모읍니다. `requiredColumns`가 모두 잡힌
     * 첫 행만 헤더로 인정합니다 — 부분 일치를 허용하면 데이터 행의 문구가 헤더로 오인됩니다.
     *
     * <p>여기에 더해 **필수 컬럼 중 최소 하나는 후보 행 자체에** 있어야 합니다. 다음 행까지 훑는 규칙만 두면 헤더 바로 위의 빈 행도 아래 행의 라벨로 조건을
     * 채워 헤더로 잡히고, 데이터 시작 행이 한 칸씩 밀립니다(실측: 9행 헤더가 8행으로 잡힘).
     *
     * @param sheet 대상 시트
     * @param fromRow 이 행부터 아래로 찾습니다. 두 번째 헤더 블록을 찾을 때 첫 블록 다음 행을 넘깁니다
     * @param columnAliases 컬럼 id 별 국문·영문 표기 목록
     * @param requiredColumns 모두 잡혀야 헤더로 인정할 컬럼 id
     * @return 헤더 위치와 열 맵. 없으면 빈 Optional
     */
    public Optional<HeaderMap> findHeader(
            Sheet sheet,
            int fromRow,
            Map<String, List<String>> columnAliases,
            String... requiredColumns) {
        // 실재하는 행만 훑는 이유는 findLabelRow와 같다
        for (Row row : sheet) {
            int rowIndex = row.getRowNum();
            if (rowIndex < fromRow) continue;
            HeaderMatch match = matchHeaderRow(sheet, rowIndex, columnAliases);
            if (match.covers(requiredColumns) && match.hasOwnLabel(requiredColumns)) {
                return Optional.of(new HeaderMap(rowIndex, match.columnIndex()));
            }
        }
        return Optional.empty();
    }

    private HeaderMatch matchHeaderRow(
            Sheet sheet, int rowIndex, Map<String, List<String>> columnAliases) {
        Map<String, Integer> columnIndex = new LinkedHashMap<>();
        Set<String> onOwnRow = new LinkedHashSet<>();
        int lastColumn = Math.max(lastColumnOf(sheet, rowIndex), lastColumnOf(sheet, rowIndex + 1));
        for (int colIndex = 0; colIndex <= lastColumn; colIndex++) {
            String primary = normalize(text(sheet, rowIndex, colIndex));
            String secondary = normalize(text(sheet, rowIndex + 1, colIndex));
            for (Map.Entry<String, List<String>> entry : columnAliases.entrySet()) {
                if (columnIndex.containsKey(entry.getKey())) continue;
                for (String alias : entry.getValue()) {
                    String normalizedAlias = normalize(alias);
                    if (normalizedAlias.isEmpty()) continue;
                    if (primary.startsWith(normalizedAlias)) {
                        columnIndex.put(entry.getKey(), colIndex);
                        onOwnRow.add(entry.getKey());
                        break;
                    }
                    if (secondary.startsWith(normalizedAlias)) {
                        columnIndex.put(entry.getKey(), colIndex);
                        break;
                    }
                }
            }
        }
        return new HeaderMatch(columnIndex, onOwnRow);
    }

    /**
     * 한 후보 행의 매칭 결과입니다.
     *
     * @param columnIndex 컬럼 id 별 열 번호 (후보 행과 다음 행을 합쳐 찾은 것)
     * @param onOwnRow 후보 행 **자체**에서 찾은 컬럼 id
     */
    private record HeaderMatch(Map<String, Integer> columnIndex, Set<String> onOwnRow) {

        boolean covers(String[] requiredColumns) {
            if (columnIndex.isEmpty()) return false;
            for (String required : requiredColumns) {
                if (!columnIndex.containsKey(required)) return false;
            }
            return true;
        }

        boolean hasOwnLabel(String[] requiredColumns) {
            for (String required : requiredColumns) {
                if (onOwnRow.contains(required)) return true;
            }
            return false;
        }
    }

    /**
     * 비교용 정규화입니다. 모든 공백(전각 포함)을 제거합니다.
     *
     * <p>공백을 하나로 줄이는 게 아니라 아예 제거합니다 — 양식에 `비 목 명`처럼 글자마다 공백을 넣은 라벨이 있어 압축만으로는 `비목명`과 맞지 않습니다.
     *
     * @param raw 원본 문자열. null이면 빈 문자열
     * @return 정규화된 문자열
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[\\s\\u00A0\\u3000]+", "");
    }

    /**
     * 괄호로 감싼 소제목 표기인지 판정합니다.
     *
     * <p>`(개요)`·`(현황)`처럼 라벨 오른쪽에 또 다른 라벨이 오는 배치가 있어, 값을 찾을 때 이 표기는 건너뜁니다.
     */
    private boolean isSubheadingLabel(String value) {
        return value.startsWith("(") && value.endsWith(")");
    }

    private Cell cellAt(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? null : row.getCell(colIndex);
    }

    private Cell mergedAnchor(Sheet sheet, int rowIndex, int colIndex) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIndex, colIndex)) {
                return cellAt(sheet, region.getFirstRow(), region.getFirstColumn());
            }
        }
        return null;
    }

    private int lastColumnOf(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? -1 : row.getLastCellNum();
    }

    /**
     * 찾은 헤더의 위치와 열 맵입니다.
     *
     * @param rowIndex 헤더 행의 0-based 번호. 데이터는 다음 행부터입니다
     * @param columnIndex 컬럼 id 별 0-based 열 번호
     */
    public record HeaderMap(int rowIndex, Map<String, Integer> columnIndex) {

        /**
         * 컬럼 id의 열 번호를 반환합니다.
         *
         * @param id 컬럼 id
         * @return 0-based 열 번호. 그 컬럼이 헤더에 없으면 null
         */
        public Integer column(String id) {
            return columnIndex.get(id);
        }
    }
}
