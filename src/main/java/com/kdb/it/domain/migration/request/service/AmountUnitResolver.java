package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.migration.request.dto.AmountUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 편성요청서의 금액 단위를 판정합니다.
 *
 * <p>1-1 요약표의 헤더는 `백만원`이라고 적혀 있지만 실제 기재 단위가 부점마다 다릅니다(실측: 한 파일은 `2637`, 다른 파일은 `1014981660`). 반면
 * 1-2 소요자원은 `수량 × 단가`라 항상 원 단위입니다. 그래서 <b>1-2를 금액 원본으로 삼고</b> 1-1은 단위를 역추정해 대사하는 데만 씁니다.
 *
 * <p>시트 ③은 대사할 상대 시트가 없어 자동 판정이 불가능합니다. 여기서는 기본값만 제안하고 최종 단위는 미리보기에서 사람이 확정합니다.
 */
public final class AmountUnitResolver {

    private AmountUnitResolver() {
        throw new UnsupportedOperationException("계산 유틸 — 인스턴스화 금지");
    }

    /**
     * 허용 상대 오차.
     *
     * <p>백만원 단위로 적으면 100만원 미만이 반올림으로 사라집니다. 실측 사례(`2699`백만원 대 2,698,850,000원)의 오차가 0.0056%이고 품목이
     * 많을수록 반올림이 누적되므로 0.5%를 둡니다. 이보다 크게 어긋나면 단위 문제가 아니라 기재 오류로 보아 판정하지 않습니다.
     */
    private static final BigDecimal TOLERANCE_RATIO = new BigDecimal("0.005");

    /**
     * 시트 ③의 원화 금액이 이 값 이상이면 원 단위로 봅니다.
     *
     * <p>천원 단위로 적은 연간 계약금액이 100만(=10억원)을 넘는 경우는 실무상 없고, 원 단위로 적은 금액이 100만원 미만인 경우도 드뭅니다.
     */
    private static final BigDecimal WON_THRESHOLD = new BigDecimal("1000000");

    /**
     * 1-1 요약표 기재값과 1-2 품목 합계를 대사해 요약표의 기재 단위를 역추정합니다.
     *
     * @param declared 1-1 `'26년도 합계` 기재값
     * @param actual 1-2 품목 합계 (원 단위)
     * @return 단위. 어느 단위로도 허용 오차 안에 들지 않거나 어느 한쪽이 없으면 빈 Optional
     */
    public static Optional<AmountUnit> inferUnit(BigDecimal declared, BigDecimal actual) {
        if (declared == null || actual == null) return Optional.empty();
        if (declared.signum() == 0 || actual.signum() == 0) return Optional.empty();

        BigDecimal allowed = actual.abs().multiply(TOLERANCE_RATIO);
        for (AmountUnit unit : AmountUnit.values()) {
            BigDecimal scaled = unit.toWon(declared);
            if (scaled.subtract(actual).abs().compareTo(allowed) <= 0) return Optional.of(unit);
        }
        return Optional.empty();
    }

    /**
     * 시트 ③의 단위 기본값을 제안합니다.
     *
     * <p>헤더는 `천원`이라고 적혀 있지만 원 단위로 적어 내는 부점이 있습니다(실측). 원화 행의 최댓값으로 갈라 제안하고 최종 확정은 미리보기에서 사람이 합니다 — 이
     * 판정은 제안일 뿐이라 호출자가 항상 `UNIT_UNCERTAIN` 경고를 함께 냅니다.
     *
     * @param krwAnnualAmounts 시트 ③ 원화 행의 연간 금액 목록. 외화 행은 넣지 않습니다
     * @return 제안 단위. 원화 행이 없으면 원 단위
     */
    public static AmountUnit suggestGeneralExpenseUnit(List<BigDecimal> krwAnnualAmounts) {
        BigDecimal max = BigDecimal.ZERO;
        for (BigDecimal amount : krwAnnualAmounts) {
            if (amount != null && amount.abs().compareTo(max) > 0) max = amount.abs();
        }
        if (max.signum() == 0) return AmountUnit.WON;
        return max.compareTo(WON_THRESHOLD) >= 0 ? AmountUnit.WON : AmountUnit.THOUSAND;
    }
}
