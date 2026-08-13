package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.request.service.FormLexicon;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * 폼 레이아웃 시트에서 라벨로 값을 읽습니다.
 *
 * <p>시트 ②와 시트 1-1이 같은 방식(라벨 셀 오른쪽에 값)을 쓰므로 두 어댑터가 공유합니다. 라벨은 {@link FormLexicon}의 국문·영문 표기를 모두
 * 시도합니다.
 *
 * <p>라벨이 놓이는 열을 고정하지 않고 행 전체를 훑습니다 — 실측 양식이 한 행에 라벨을 여러 개 늘어놓고(`주관부문/본부 … 팀장 … IT팀장`) 그 열이 2·5·8열로
 * 흩어져 있습니다.
 *
 * <p>값을 읽을 때는 <b>다음 라벨을 만나면 멈춥니다.</b> 그러지 않으면 값이 빈 항목이 오른쪽 라벨을 값으로 집어옵니다(실측: `중복 여부`가 값 대신 옆 칸의
 * `법규상 완료시기`를 가져옴).
 */
@Component
@RequiredArgsConstructor
public class FormLabelReader {

    /**
     * 양식이 쓰는 라벨 어휘입니다.
     *
     * <p>값 탐색을 멈출 지점을 알기 위해 필요합니다. 읽는 항목뿐 아니라 <b>오른쪽에 나란히 놓이는 라벨</b>(팀장·전결권자·종료일자 등)도 넣어야 그 앞 항목이
     * 라벨을 값으로 오인하지 않습니다. 양식에 새 라벨이 생기면 여기에 추가합니다.
     */
    private static final Set<String> FORM_LABELS = formLabels();

    /** 라벨 오른쪽에서 값을 찾을 때 훑는 최대 열 수. */
    private static final int VALUE_SCAN_WIDTH = 16;

    private final SheetAnchorScanner scanner;

    /**
     * 라벨 오른쪽의 값을 읽습니다.
     *
     * @param sheet 대상 시트
     * @param canonicalLabel 국문 정본 라벨
     * @return 값. 라벨을 못 찾거나 오른쪽에 값이 없으면 null
     */
    public String value(Sheet sheet, String canonicalLabel) {
        Optional<Anchor> anchor = findAnchor(sheet, canonicalLabel);
        if (anchor.isEmpty()) return null;
        return valueRightOf(sheet, anchor.get().rowIndex(), anchor.get().colIndex()).orElse(null);
    }

    /**
     * 여러 행에 걸친 값을 줄바꿈으로 이어 읽습니다.
     *
     * @param sheet 대상 시트
     * @param canonicalLabel 국문 정본 라벨
     * @param maxRows 라벨 행부터 훑을 최대 행 수
     * @return 이어 붙인 값. 값이 하나도 없으면 null
     */
    public String multiRowValue(Sheet sheet, String canonicalLabel, int maxRows) {
        Optional<Anchor> anchor = findAnchor(sheet, canonicalLabel);
        if (anchor.isEmpty()) return null;

        List<String> lines = new java.util.ArrayList<>();
        int last = Math.min(anchor.get().rowIndex() + maxRows - 1, sheet.getLastRowNum());
        for (int rowIndex = anchor.get().rowIndex(); rowIndex <= last; rowIndex++) {
            Optional<String> line = valueRightOf(sheet, rowIndex, anchor.get().colIndex());
            if (line.isEmpty()) continue;
            if (lines.isEmpty() || !lines.get(lines.size() - 1).equals(line.get())) {
                lines.add(line.get());
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    /** 라벨이 놓인 (행, 열)을 찾습니다. 행 전체를 훑습니다. */
    private Optional<Anchor> findAnchor(Sheet sheet, String canonicalLabel) {
        Set<String> aliases = new LinkedHashSet<>();
        for (String alias : FormLexicon.labelAliases(canonicalLabel)) {
            aliases.add(SheetAnchorScanner.normalize(alias));
        }

        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                String text = SheetAnchorScanner.normalize(scanner.text(sheet, rowIndex, colIndex));
                if (!text.isEmpty() && aliases.contains(text)) {
                    return Optional.of(new Anchor(rowIndex, colIndex));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 라벨 오른쪽에서 값을 읽습니다. 다음 라벨을 만나면 값이 없는 것으로 봅니다.
     *
     * @return 값. 없으면 빈 Optional
     */
    private Optional<String> valueRightOf(Sheet sheet, int rowIndex, int labelColIndex) {
        for (int colIndex = labelColIndex + 1;
                colIndex <= labelColIndex + VALUE_SCAN_WIDTH;
                colIndex++) {
            String text = scanner.text(sheet, rowIndex, colIndex);
            if (text.isEmpty()) continue;
            if (isLabel(text)) return Optional.empty();
            return Optional.of(text);
        }
        return Optional.empty();
    }

    /** 양식 라벨이거나 `(개요)` 같은 괄호 소제목이면 값이 아닙니다. */
    private static boolean isLabel(String text) {
        if (text.startsWith("(") && text.endsWith(")")) return true;
        return FORM_LABELS.contains(SheetAnchorScanner.normalize(text));
    }

    private static Set<String> formLabels() {
        Set<String> labels = new LinkedHashSet<>();
        for (String label :
                List.of(
                        // 1-1 개요
                        "사업명",
                        "사업 개요",
                        "사업 범위 (전산 요구사항)",
                        "진행 상황",
                        "추진경과",
                        "향후계획",
                        "사업구분",
                        "업무구분",
                        "사업유형",
                        "디지털 기술 유형",
                        "주 사용자",
                        "편성 기준",
                        "중복 여부",
                        "법규상 완료시기",
                        "관련 조직",
                        "주관부문/본부",
                        "주관부서/팀",
                        "팀장",
                        "실무자(정/부)",
                        "IT팀장",
                        "IT실무자(정/부)",
                        "보고 상태",
                        "최종보고",
                        "전결권자",
                        "사업 추진 가능성",
                        "추진가능성",
                        "추진시기 및 소요예산",
                        "시작일자 (YY/MM)",
                        "종료일자 (YY/MM)",
                        "소요예산",
                        "총 사업금액(전체기간)",
                        // 시트 ②
                        "구분",
                        "소요 자원",
                        "계")) {
            labels.add(SheetAnchorScanner.normalize(label));
            for (String alias : FormLexicon.labelAliases(label)) {
                labels.add(SheetAnchorScanner.normalize(alias));
            }
        }
        return Set.copyOf(labels);
    }

    /**
     * 라벨이 놓인 좌표입니다.
     *
     * @param rowIndex 0-based 행 번호
     * @param colIndex 0-based 열 번호
     */
    private record Anchor(int rowIndex, int colIndex) {}
}
