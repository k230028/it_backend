package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.migration.request.dto.AmountUnit;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 편성요청서 금액 칸을 읽습니다.
 *
 * <p>부점은 숫자만 적지 않습니다. 실측 제출본에 `2,122백만원`(리스크관리부 1-2)·`41,868,816원`(산업기술리서치센터 ③)·`20,000USD (원화기준
 * 28.6백만원)`(상하이 ②) 같은 표기가 그대로 들어 있습니다. 이 글자들 때문에 숫자 파싱이 실패하면 그 행은 <b>금액이 없는 것으로 버려져</b> 사업 소요금액이
 * 0원이 되거나 `금액이 비어 있습니다` 차단이 납니다.
 *
 * <p>단위 표기(`원`·`천원`·`백만원`)는 <b>버리지 않고 함께 돌려줍니다.</b> 셀이 스스로 단위를 밝혔으면 그것이 시트 머리말이나 어댑터 기본값보다 정확한 사실이기
 * 때문입니다. 산업기술리서치센터 ③은 머리말이 `단위: 천원`인데 그 칸만 `41,868,816원`이라, 단위를 버리고 시트 배수를 곱하면 금액이 1,000배가 됩니다.
 */
public final class FormAmount {

    private FormAmount() {
        throw new UnsupportedOperationException("유틸리티 — 인스턴스화 금지");
    }

    /** 괄호 주석. `20,000USD (원화기준 28.6백만원)`처럼 환산액을 덧붙인 표기를 떼어 냅니다. */
    private static final Pattern PARENTHETICAL = Pattern.compile("\\([^)]*\\)");

    /** 통화코드·단위 약어 같은 라틴 문자. 단위 표기를 떼어 낸 뒤에 지웁니다. */
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]+");

    /** 숫자로 남으면 안 되는 잡음 — 자릿수 구분 쉼표, 공백, 통화 기호. */
    private static final Pattern NOISE = Pattern.compile("[,\\s\\u00A0\\u3000₩$￦]+");

    /** 단위 표기와 배수. 긴 표기가 먼저여야 `백만원`이 `원`으로 잘리지 않습니다. */
    private static final String[] UNIT_WORDS = {"백만원", "천원", "천엔", "원", "엔"};

    private static final AmountUnit[] UNIT_VALUES = {
        AmountUnit.MILLION, AmountUnit.THOUSAND, AmountUnit.THOUSAND, AmountUnit.WON, AmountUnit.WON
    };

    /**
     * 금액 칸의 값과, 셀이 스스로 밝힌 단위를 읽습니다.
     *
     * @param raw 셀 원문
     * @return 파싱 결과. 숫자를 찾지 못하면 null
     */
    public static Parsed parse(String raw) {
        if (raw == null) return null;
        String text = PARENTHETICAL.matcher(raw).replaceAll(" ");

        AmountUnit unit = null;
        for (int i = 0; i < UNIT_WORDS.length; i++) {
            if (!text.contains(UNIT_WORDS[i])) continue;
            unit = UNIT_VALUES[i];
            text = text.replace(UNIT_WORDS[i], " ");
            break;
        }

        text = LATIN.matcher(text).replaceAll(" ");
        text = NOISE.matcher(text).replaceAll("");
        if (text.isEmpty()) return null;
        try {
            return new Parsed(new BigDecimal(text), unit);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 금액 값만 읽습니다. 단위 표기를 보지 않는 자리(수량·단가)에서 씁니다.
     *
     * @param raw 셀 원문
     * @return 금액. 숫자를 찾지 못하면 null
     */
    public static BigDecimal value(String raw) {
        Parsed parsed = parse(raw);
        return parsed == null ? null : parsed.value();
    }

    /**
     * 금액 칸 파싱 결과입니다.
     *
     * @param value 셀에 적힌 수. 단위를 곱하지 않은 그대로입니다
     * @param unit 셀이 밝힌 단위. 단위 표기가 없으면 null이고, 그때는 호출자가 시트·어댑터 기본 단위를 씁니다
     */
    public record Parsed(BigDecimal value, AmountUnit unit) {}
}
