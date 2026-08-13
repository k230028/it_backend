package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AmountUnitResolverTest {

    @Test
    @DisplayName("1-1이 백만원으로 적힌 파일의 배수를 백만으로 역추정한다")
    void infersMillionMultiplier() {
        // 스마트워크 인프라 실측: 1-1 '26년도 합계 2699(백만원), 1-2 품목 합계 2,698,850,000원
        assertThat(
                        AmountUnitResolver.inferMultiplier(
                                new BigDecimal("2699"), new BigDecimal("2698850000")))
                .contains(AmountUnitResolver.UNIT_MILLION);
    }

    @Test
    @DisplayName("1-1이 원으로 적힌 파일의 배수를 1로 역추정한다")
    void infersWonMultiplier() {
        // 자금운용실 실측: 1-1 '26년도 합계와 1-2 품목 합계가 모두 1,211,418,360원
        assertThat(
                        AmountUnitResolver.inferMultiplier(
                                new BigDecimal("1211418360"), new BigDecimal("1211418360")))
                .contains(AmountUnitResolver.UNIT_WON);
    }

    @Test
    @DisplayName("천원 단위로 적힌 경우도 판정한다")
    void infersThousandMultiplier() {
        assertThat(
                        AmountUnitResolver.inferMultiplier(
                                new BigDecimal("1211418"), new BigDecimal("1211418360")))
                .contains(AmountUnitResolver.UNIT_THOUSAND);
    }

    @Test
    @DisplayName("어느 배수로도 맞지 않으면 빈 Optional로 남겨 불일치 경고를 내게 한다")
    void leavesUnmatchedAmountUnresolved() {
        assertThat(
                        AmountUnitResolver.inferMultiplier(
                                new BigDecimal("500"), new BigDecimal("2698850000")))
                .isEmpty();
    }

    @Test
    @DisplayName("어느 한쪽이 0이거나 null이면 판정하지 않는다")
    void skipsWhenEitherSideIsAbsent() {
        assertThat(AmountUnitResolver.inferMultiplier(null, new BigDecimal("100"))).isEmpty();
        assertThat(AmountUnitResolver.inferMultiplier(BigDecimal.ZERO, new BigDecimal("100")))
                .isEmpty();
        assertThat(AmountUnitResolver.inferMultiplier(new BigDecimal("100"), BigDecimal.ZERO))
                .isEmpty();
    }

    @Test
    @DisplayName("시트 ③의 원화 금액이 백만 이상이면 원 단위로 제안한다")
    void suggestsWonForLargeKrwAmounts() {
        // 자금운용실 실측: 연간 841,854,085원
        assertThat(
                        AmountUnitResolver.suggestGeneralExpenseMultiplier(
                                List.of(new BigDecimal("841854085"), new BigDecimal("35838000"))))
                .isEqualTo(AmountUnitResolver.UNIT_WON);
    }

    @Test
    @DisplayName("시트 ③의 원화 금액이 모두 작으면 헤더 표기대로 천원 단위로 제안한다")
    void suggestsThousandForSmallKrwAmounts() {
        assertThat(
                        AmountUnitResolver.suggestGeneralExpenseMultiplier(
                                List.of(new BigDecimal("841854"), new BigDecimal("35838"))))
                .isEqualTo(AmountUnitResolver.UNIT_THOUSAND);
    }

    @Test
    @DisplayName("원화 행이 하나도 없으면 원 단위로 제안한다")
    void suggestsWonWhenNoKrwRow() {
        assertThat(AmountUnitResolver.suggestGeneralExpenseMultiplier(List.of()))
                .isEqualTo(AmountUnitResolver.UNIT_WON);
    }

    @Test
    @DisplayName("배수를 적용해 원 단위 금액을 만든다")
    void appliesMultiplier() {
        assertThat(AmountUnitResolver.applyMultiplier(new BigDecimal("2699"), 1_000_000L))
                .isEqualByComparingTo(new BigDecimal("2699000000"));
        assertThat(AmountUnitResolver.applyMultiplier(null, 1_000L)).isNull();
    }
}
