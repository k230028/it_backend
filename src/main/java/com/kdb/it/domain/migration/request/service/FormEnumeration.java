package com.kdb.it.domain.migration.request.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한 행에 여러 건을 번호로 묶어 적은 셀을 항목별로 나눕니다.
 *
 * <p>부점은 같은 비목의 계약 여러 건을 행마다 적지 않고 <b>한 행 안에 번호로</b> 몰아 적습니다(실측: 미래전략개발부 ③ 6·14행의 `①②③`, 발행시장실 ③
 * 13행의 `1. 2. 3.`). 계약명·상대처·금액·계속/신규·정보보호 여부가 모두 같은 번호 체계로 나뉘어 있어, 셀을 통째로 읽으면 금액은 숫자가 아니게 되고 정보보호
 * 표기는 `① X ② X ③ X`가 되어 해석에 실패합니다.
 *
 * <p>번호 체계는 <b>계약명 칸을 기준으로</b> 정합니다. 다른 칸은 번호가 빠져 있거나(비고에 ②만 적음) 순서가 어긋날 수 있으므로 위치가 아니라 <b>번호로</b>
 * 맞춥니다. 번호가 아예 없는 칸(통화 구분 등)은 모든 항목이 같은 값을 씁니다.
 */
public final class FormEnumeration {

    /** 원문자 번호 `①`(U+2460) ~ `⑳`(U+2473). 줄 중간에 나와도 번호로 봅니다. */
    private static final Pattern CIRCLED = Pattern.compile("[\\u2460-\\u2473]");

    /** 원문자 `①`의 코드포인트. 번호를 뽑을 때 뺍니다. */
    private static final int CIRCLED_BASE = 0x2460;

    /**
     * `1.`·`2)` 같은 아라비아 숫자 번호. <b>줄 첫머리에만</b> 인정합니다.
     *
     * <p>줄 중간까지 보면 `28.6백만원`의 `8.`이나 날짜 표기가 번호로 잡힙니다. 뒤에 숫자가 오는 경우(`2.5`)를 배제해 소수점도 거릅니다.
     */
    private static final Pattern NUMBERED =
            Pattern.compile("(?m)^[ \\t\\u00A0\\u3000]*(\\d{1,2})[.)](?!\\d)");

    /** 열거로 인정할 최소 항목 수. 번호가 하나뿐이면 그냥 한 건짜리 행입니다. */
    private static final int MINIMUM_ITEMS = 2;

    private final boolean circled;
    private final List<Integer> numbers;

    private FormEnumeration(boolean circled, List<Integer> numbers) {
        this.circled = circled;
        this.numbers = numbers;
    }

    /**
     * 기준 칸에서 번호 체계를 읽습니다.
     *
     * <p>원문자를 먼저 봅니다 — 줄 중간에 나와도 확실한 번호라 오검출이 없습니다. 원문자가 없을 때만 줄머리의 아라비아 숫자를 봅니다.
     *
     * @param keyCell 번호 체계의 기준이 될 칸 원문 (시트 ③은 계약명 칸)
     * @return 항목이 둘 이상이면 번호 체계. 아니면 빈 Optional
     */
    public static Optional<FormEnumeration> of(String keyCell) {
        if (keyCell == null || keyCell.isBlank()) return Optional.empty();
        List<Integer> circledNumbers = numbersOf(keyCell, true);
        if (circledNumbers.size() >= MINIMUM_ITEMS) {
            return Optional.of(new FormEnumeration(true, circledNumbers));
        }
        List<Integer> plainNumbers = numbersOf(keyCell, false);
        if (plainNumbers.size() >= MINIMUM_ITEMS) {
            return Optional.of(new FormEnumeration(false, plainNumbers));
        }
        return Optional.empty();
    }

    /**
     * 항목 수를 돌려줍니다.
     *
     * @return 기준 칸에서 읽은 번호 개수
     */
    public int size() {
        return numbers.size();
    }

    /**
     * 셀을 항목별 값으로 나눕니다.
     *
     * @param cell 셀 원문. null·공백이면 전부 빈 문자열
     * @return 기준 칸의 번호 순서에 맞춘 값 목록. 항목 수와 길이가 같습니다
     */
    public List<String> split(String cell) {
        List<String> values = new ArrayList<>(numbers.size());
        if (cell == null || cell.isBlank()) {
            for (int i = 0; i < numbers.size(); i++) values.add("");
            return values;
        }
        Map<Integer, String> byNumber = sliceByNumber(cell);
        if (byNumber.isEmpty()) {
            // 번호가 없는 칸(통화 구분·비목명 등)은 행 전체에 걸린 값이다
            String shared = cell.trim();
            for (int i = 0; i < numbers.size(); i++) values.add(shared);
            return values;
        }
        for (Integer number : numbers) values.add(byNumber.getOrDefault(number, ""));
        return values;
    }

    /** 셀 안의 번호 위치를 찾아 번호 → 그 뒤 조각으로 자릅니다. */
    private Map<Integer, String> sliceByNumber(String cell) {
        Matcher matcher = (circled ? CIRCLED : NUMBERED).matcher(cell);
        List<Integer> found = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        while (matcher.find()) {
            found.add(numberAt(matcher));
            starts.add(matcher.start());
            ends.add(matcher.end());
        }
        Map<Integer, String> byNumber = new LinkedHashMap<>();
        for (int i = 0; i < found.size(); i++) {
            int to = i + 1 < found.size() ? starts.get(i + 1) : cell.length();
            byNumber.putIfAbsent(found.get(i), cell.substring(ends.get(i), to).trim());
        }
        return byNumber;
    }

    private static List<Integer> numbersOf(String cell, boolean circled) {
        Matcher matcher = (circled ? CIRCLED : NUMBERED).matcher(cell);
        List<Integer> numbers = new ArrayList<>();
        while (matcher.find()) {
            int number = numberAt(matcher);
            if (!numbers.contains(number)) numbers.add(number);
        }
        return numbers;
    }

    private static int numberAt(Matcher matcher) {
        String matched = matcher.group();
        char first = matched.charAt(0);
        if (first >= CIRCLED_BASE && first <= CIRCLED_BASE + 19) return first - CIRCLED_BASE + 1;
        return Integer.parseInt(matcher.group(1));
    }
}
