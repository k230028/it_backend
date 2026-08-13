package com.kdb.it.domain.migration.request.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 시트 ③ `전산 일반관리비 편성요청서`의 금액 기재 단위입니다.
 *
 * <p>배수를 숫자로 주고받지 않고 enum으로 둡니다. 숫자에 {@code @Schema(allowableValues=…)}를 붙이면 OpenAPI가 값 집합을 문자열
 * enum으로 내보내 프론트 생성 타입이 {@code "1" | "1000" | "1000000"}이 되고, 서버의 {@code Long}과 어긋납니다. 값 집합이 정해진
 * 도메인 개념이므로 타입으로 표현하는 편이 매직 넘버보다 읽기도 쉽습니다.
 */
@Schema(name = "RequestFormAmountUnit", description = "일반관리비 금액 기재 단위")
public enum AmountUnit {
    /** 원 단위. 양식 헤더는 천원이지만 원으로 적어 내는 부점이 있습니다(실측). */
    WON(1L, "원"),
    /** 천원 단위. 양식 헤더 표기입니다. */
    THOUSAND(1_000L, "천원"),
    /** 백만원 단위. */
    MILLION(1_000_000L, "백만원");

    private final long multiplier;
    private final String label;

    AmountUnit(long multiplier, String label) {
        this.multiplier = multiplier;
        this.label = label;
    }

    /**
     * 원 단위로 바꿀 때 곱할 배수를 반환합니다.
     *
     * @return 배수 (1·1,000·1,000,000)
     */
    public long multiplier() {
        return multiplier;
    }

    /**
     * 사용자에게 보여줄 단위 이름을 반환합니다.
     *
     * @return `원`·`천원`·`백만원`
     */
    public String label() {
        return label;
    }

    /**
     * 이 단위로 적힌 금액을 원 단위로 폅니다.
     *
     * @param raw 시트 기재값
     * @return 원 단위 금액. `raw`가 null이면 null
     */
    public BigDecimal toWon(BigDecimal raw) {
        if (raw == null) return null;
        return raw.multiply(BigDecimal.valueOf(multiplier), MathContext.DECIMAL64);
    }
}
