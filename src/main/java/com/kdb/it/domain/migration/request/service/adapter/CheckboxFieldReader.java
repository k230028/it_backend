package com.kdb.it.domain.migration.request.service.adapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 체크박스 목록에서 항목별로 체크된 문구를 골라냅니다.
 *
 * <p>POI 접근은 {@link FormCheckboxReader}가 끝내고 여기는 좌표·문구만 다룹니다. 두 책임을 나눈 이유는 <b>선택 규칙을 단위 테스트할 수
 * 있게</b> 하려는 것입니다 — POI로는 양식 컨트롤이 든 워크북을 만들 수 없어(생성 API가 없습니다) 픽스처를 못 만듭니다.
 *
 * <p>한 행에 항목이 둘 놓이는 배치가 있습니다(실측: `중복 여부 … 법규상 완료시기 …`). 그래서 라벨 열부터 <b>그 행의 다음 라벨 열 직전까지</b>로 범위를
 * 끊습니다. 체크박스는 라벨 셀 안쪽에서 오른쪽으로 밀려 그려지는 경우가 있어 시작 경계는 라벨 열을 포함합니다.
 */
public final class CheckboxFieldReader {

    private CheckboxFieldReader() {
        throw new UnsupportedOperationException("유틸리티 — 인스턴스화 금지");
    }

    /** `기타 ( )`처럼 뒤에 빈 기입란이 달린 문구의 꼬리 괄호. 코드값명과 맞추려면 떼야 합니다. */
    private static final Pattern TRAILING_BLANK_PARENTHESIS =
            Pattern.compile("[(（][\\s\\u00A0\\u3000]*[)）]\\s*$");

    /** `비중복(N)`·`중복(Y)`에서 Y/N을 뽑는 패턴. */
    private static final Pattern YN_IN_PARENTHESIS = Pattern.compile("[(（]\\s*([YNyn])\\s*[)）]");

    /**
     * 라벨 행에서 체크된 문구를 왼쪽부터 순서대로 고릅니다.
     *
     * @param checkboxes 시트 전체의 체크박스
     * @param rowIndex 라벨이 있는 0-based 행 번호
     * @param fromColumn 라벨 열 (포함)
     * @param toColumnExclusive 같은 행 다음 라벨의 열. 없으면 {@link Integer#MAX_VALUE}
     * @return 정규화한 문구 목록. 하나도 체크되지 않았으면 빈 목록
     */
    public static List<String> checkedCaptions(
            List<FormCheckbox> checkboxes, int rowIndex, int fromColumn, int toColumnExclusive) {
        List<FormCheckbox> matched = new ArrayList<>();
        for (FormCheckbox box : checkboxes) {
            if (!box.checked() || box.rowIndex() != rowIndex) continue;
            if (box.colIndex() < fromColumn || box.colIndex() >= toColumnExclusive) continue;
            matched.add(box);
        }
        matched.sort(Comparator.comparingInt(FormCheckbox::colIndex));

        List<String> captions = new ArrayList<>();
        for (FormCheckbox box : matched) {
            String caption = normalize(box.caption());
            if (!caption.isEmpty() && !captions.contains(caption)) captions.add(caption);
        }
        return List.copyOf(captions);
    }

    /**
     * 그 행·범위에 체크박스가 하나라도 놓여 있는지 봅니다.
     *
     * <p>"체크박스 항목인데 아무것도 안 골랐다"와 "애초에 체크박스가 없는 항목이다"를 가르는 데 씁니다. 앞은 미기재 안내가 맞고 뒤는 셀 값을 읽어야 합니다.
     *
     * @param checkboxes 시트 전체의 체크박스
     * @param rowIndex 라벨이 있는 0-based 행 번호
     * @param fromColumn 라벨 열 (포함)
     * @param toColumnExclusive 같은 행 다음 라벨의 열
     * @return 체크박스가 하나라도 있으면 true
     */
    public static boolean hasCheckbox(
            List<FormCheckbox> checkboxes, int rowIndex, int fromColumn, int toColumnExclusive) {
        for (FormCheckbox box : checkboxes) {
            if (box.rowIndex() != rowIndex) continue;
            if (box.colIndex() >= fromColumn && box.colIndex() < toColumnExclusive) return true;
        }
        return false;
    }

    /**
     * 여러 문구를 한 컬럼에 담을 표기로 잇습니다.
     *
     * <p>코드값명을 그대로 저장하는 항목(업무구분·사업유형·기술유형·주사용자)은 복수 선택이 가능하고 컬럼이 100~300자라 쉼표로 잇습니다.
     *
     * @param captions 체크된 문구
     * @return 쉼표로 이은 문구. 비었으면 null
     */
    public static String joined(List<String> captions) {
        return captions.isEmpty() ? null : String.join(", ", captions);
    }

    /**
     * `비중복(N)`·`중복(Y)` 표기를 Y/N으로 접습니다.
     *
     * @param captions 체크된 문구
     * @return `Y` 또는 `N`. 판단할 수 없으면 null
     */
    public static String toDuplicateYn(List<String> captions) {
        for (String caption : captions) {
            Matcher matcher = YN_IN_PARENTHESIS.matcher(caption);
            if (matcher.find())
                return java.util.Objects.requireNonNull(matcher.group(1))
                        .toUpperCase(java.util.Locale.ROOT);
        }
        return null;
    }

    /**
     * 체크 문구에서 꼬리의 빈 괄호와 앞뒤 공백을 떼어냅니다.
     *
     * <p>`기타 ( )`는 코드값명 `기타`와 같은 것을 가리키지만 괄호를 그대로 두면 매칭도 저장값도 어긋납니다. 반면 `비중복(N)`·`부문(본부장) 보고`처럼
     * <b>내용이 있는 괄호</b>는 의미의 일부라 남깁니다.
     *
     * @param caption 도형에서 읽은 원문
     * @return 정규화한 문구. null이면 빈 문자열
     */
    public static String normalize(String caption) {
        if (caption == null) return "";
        String collapsed = caption.replaceAll("[\\s\\u00A0\\u3000]+", " ").trim();
        return TRAILING_BLANK_PARENTHESIS.matcher(collapsed).replaceAll("").trim();
    }
}
