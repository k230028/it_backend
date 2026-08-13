package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.request.service.FormLexicon;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * 폼 레이아웃 시트에서 라벨로 값을 읽습니다.
 *
 * <p>시트 ②와 시트 1-1이 같은 방식(A열 대분류·C열 소분류에 라벨, 오른쪽에 값)을 쓰므로 두 어댑터가 공유합니다. 라벨은 {@link FormLexicon}의
 * 국문·영문 표기를 모두 시도합니다.
 */
@Component
@RequiredArgsConstructor
public class FormLabelReader {

    /** 라벨이 놓이는 열. A열은 대분류, C열은 소분류입니다. */
    private static final int[] LABEL_COLUMNS = {0, 2};

    private final SheetAnchorScanner scanner;

    /**
     * 라벨 오른쪽의 값을 읽습니다.
     *
     * @param sheet 대상 시트
     * @param canonicalLabel 국문 정본 라벨
     * @return 값. 라벨을 못 찾거나 오른쪽에 값이 없으면 null
     */
    public String value(Sheet sheet, String canonicalLabel) {
        List<String> aliases = FormLexicon.labelAliases(canonicalLabel);
        Optional<Integer> row = findRow(sheet, aliases);
        if (row.isEmpty()) return null;
        return scanner.valueRightOf(sheet, row.get(), labelColumnOf(sheet, row.get(), aliases))
                .orElse(null);
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
        List<String> aliases = FormLexicon.labelAliases(canonicalLabel);
        Optional<Integer> row = findRow(sheet, aliases);
        if (row.isEmpty()) return null;
        String joined =
                scanner.joinedValueRightOf(
                        sheet,
                        row.get(),
                        row.get() + maxRows,
                        labelColumnOf(sheet, row.get(), aliases));
        return joined.isEmpty() ? null : joined;
    }

    private Optional<Integer> findRow(Sheet sheet, List<String> aliases) {
        return scanner.findLabelRow(sheet, LABEL_COLUMNS, aliases.toArray(String[]::new));
    }

    /** 라벨이 실제로 놓인 열을 찾습니다. 못 찾으면 첫 라벨 열로 봅니다. */
    private int labelColumnOf(Sheet sheet, int rowIndex, List<String> aliases) {
        for (int colIndex : LABEL_COLUMNS) {
            String text = SheetAnchorScanner.normalize(scanner.text(sheet, rowIndex, colIndex));
            for (String alias : aliases) {
                if (text.equals(SheetAnchorScanner.normalize(alias))) return colIndex;
            }
        }
        return LABEL_COLUMNS[0];
    }
}
