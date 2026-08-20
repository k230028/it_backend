package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * 시트 상단 머리말의 확인자와 담당자를 읽습니다.
 *
 * <p>경상사업(②)과 일반관리비(③)에는 1-1의 `관련 조직` 블록이 없습니다. 대신 시트 맨 위에 확인자·작성자를 한 번 적으며, 그것이 그 시트의 <b>주관팀장과
 * 담당자</b>입니다.
 *
 * <p>1-1(정보화사업)에는 쓰지 않습니다 — 그쪽은 `관련 조직` 블록의 `팀장`·`실무자`가 정본이고, 실측 파일에서 두 곳이 어긋났습니다(확인자 `허인선 팀장` 대 관련
 * 조직 팀장 `윤소정`). 제출 담당자와 사업 주관자가 다른 경우입니다.
 *
 * <p>적혀 있지 않으면 <b>비워 둡니다.</b> 업로드 사용자 같은 다른 사람으로 대신 채우면 원장에 사실이 아닌 담당자가 남습니다.
 */
@Component
@RequiredArgsConstructor
public class FormApproverReader {

    /** 머리말을 찾을 상단 행 수. 실측 제출본은 모두 2행 안에 적습니다. */
    private static final int HEADER_SCAN_ROWS = 5;

    /** 머리말을 찾을 최대 열 수. 실측은 K열(10)까지 나옵니다. */
    private static final int HEADER_SCAN_COLUMNS = 20;

    /** 라벨 오른쪽에서 이름을 찾을 때 훑는 최대 열 수. */
    private static final int VALUE_SCAN_WIDTH = 6;

    private final SheetAnchorScanner scanner;

    /**
     * 확인자 이름을 읽습니다. 그 시트의 주관팀장입니다.
     *
     * @param sheet 대상 시트
     * @return 이름. 적혀 있지 않으면 null
     */
    public String confirmer(Sheet sheet) {
        return nameAfterLabel(sheet, "(확인자)");
    }

    /**
     * 담당자 이름을 읽습니다.
     *
     * <p>시기별 양식이 `담당자`·`실무자`·`작성자`를 섞어 쓰므로 이 순서로 우선합니다.
     *
     * @param sheet 대상 시트
     * @return 이름. 적혀 있지 않으면 null
     */
    public String author(Sheet sheet) {
        for (String label : new String[] {"담당자", "실무자", "작성자"}) {
            String name = nameAfterLabel(sheet, label);
            if (name != null) return name;
        }
        return null;
    }

    /** 양식에 적힌 전결권자 직책을 공통 표기로 맞춥니다. */
    static String normalizeApproverRole(String raw) {
        String normalized = SheetAnchorScanner.normalize(raw);
        if (normalized.contains("본부장")) return "지역본부장";
        if (normalized.contains("부장")) return "부장";
        if (normalized.contains("팀장")) return "팀장";
        return normalized;
    }

    /**
     * 라벨 뒤의 이름을 찾습니다.
     *
     * <p>두 표기를 모두 받습니다 — 라벨과 이름이 <b>같은 칸</b>에 붙어 있는 형태(`(작성자) Luke Buckingham-Brown 과장`, 런던 시트 ③
     * 실측)와, 라벨 칸 오른쪽에 이름이 따로 있는 형태(런던 시트 ②)입니다.
     */
    private String nameAfterLabel(Sheet sheet, String label) {
        String key = SheetAnchorScanner.normalize(label);
        for (int rowIndex = 0; rowIndex < HEADER_SCAN_ROWS; rowIndex++) {
            for (int colIndex = 0; colIndex < HEADER_SCAN_COLUMNS; colIndex++) {
                String text = scanner.text(sheet, rowIndex, colIndex);
                if (text.isEmpty()) continue;
                if (!startsWithLabel(text, key)) continue;

                String inline = afterLabel(text, label);
                if (!inline.isEmpty()) return normalizeReadValue(inline);
                return normalizeReadValue(nameRightOf(sheet, rowIndex, colIndex));
            }
        }
        return null;
    }

    /** 사람 이름은 그대로 두고, 공백 없이 적힌 역할 값만 공통 표기로 맞춥니다. */
    private static String normalizeReadValue(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        String normalizedSource = SheetAnchorScanner.normalize(trimmed);
        if (normalizedSource.contains("본부장")) return "지역본부장";
        if (trimmed.indexOf(' ') < 0 && trimmed.indexOf('\t') < 0) {
            String normalizedRole = normalizeApproverRole(trimmed);
            if (normalizedRole.equals("부장") || normalizedRole.equals("팀장")) return normalizedRole;
        }
        return value;
    }

    /** 라벨 칸의 병합 영역을 건너뛴 다음 열부터 이름을 찾습니다. 다음 라벨을 만나면 이름이 없는 것으로 봅니다. */
    private String nameRightOf(Sheet sheet, int rowIndex, int labelColIndex) {
        int from = scanner.mergedEndColumn(sheet, rowIndex, labelColIndex) + 1;
        for (int colIndex = from; colIndex < from + VALUE_SCAN_WIDTH; colIndex++) {
            String value = scanner.text(sheet, rowIndex, colIndex);
            if (value.isEmpty()) continue;
            return isPersonLabel(value) ? null : value;
        }
        return null;
    }

    private static boolean startsWithLabel(String text, String normalizedLabel) {
        String normalized = SheetAnchorScanner.normalize(text);
        if (normalized.startsWith(normalizedLabel)) return true;
        return normalized.startsWith("(" + normalizedLabel + ")");
    }

    private static String afterLabel(String text, String label) {
        String trimmed = text.trim();
        int labelIndex = trimmed.indexOf(label);
        if (labelIndex < 0) return "";
        String remainder = trimmed.substring(labelIndex + label.length()).trim();
        if (remainder.startsWith(")")) remainder = remainder.substring(1).trim();
        if (remainder.startsWith(":")) remainder = remainder.substring(1).trim();
        return remainder;
    }

    private static boolean isPersonLabel(String value) {
        for (String label : new String[] {"확인자", "담당자", "실무자", "작성자"}) {
            if (startsWithLabel(value, label)) return true;
        }
        return false;
    }
}
