package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.request.dto.AmountUnit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AmountUnitResolverTest {

    @Test
    @DisplayName("1-1이 백만원으로 적힌 파일의 단위를 백만원으로 역추정한다")
    void infersMillionUnit() {
        // 스마트워크 인프라 실측: 1-1 '26년도 합계 2699(백만원), 1-2 품목 합계 2,698,850,000원
        assertThat(
                        AmountUnitResolver.inferUnit(
                                new BigDecimal("2699"), new BigDecimal("2698850000")))
                .contains(AmountUnit.MILLION);
    }

    @Test
    @DisplayName("1-1이 원으로 적힌 파일의 단위를 원으로 역추정한다")
    void infersWonUnit() {
        // 자금운용실 실측: 1-1 '26년도 합계와 1-2 품목 합계가 모두 1,211,418,360원
        assertThat(
                        AmountUnitResolver.inferUnit(
                                new BigDecimal("1211418360"), new BigDecimal("1211418360")))
                .contains(AmountUnit.WON);
    }

    @Test
    @DisplayName("천원 단위로 적힌 경우도 판정한다")
    void infersThousandUnit() {
        assertThat(
                        AmountUnitResolver.inferUnit(
                                new BigDecimal("1211418"), new BigDecimal("1211418360")))
                .contains(AmountUnit.THOUSAND);
    }

    @Test
    @DisplayName("어느 단위로도 맞지 않으면 빈 Optional로 남겨 불일치 경고를 내게 한다")
    void leavesUnmatchedAmountUnresolved() {
        assertThat(
                        AmountUnitResolver.inferUnit(
                                new BigDecimal("500"), new BigDecimal("2698850000")))
                .isEmpty();
    }

    @Test
    @DisplayName("어느 한쪽이 0이거나 null이면 판정하지 않는다")
    void skipsWhenEitherSideIsAbsent() {
        assertThat(AmountUnitResolver.inferUnit(null, new BigDecimal("100"))).isEmpty();
        assertThat(AmountUnitResolver.inferUnit(BigDecimal.ZERO, new BigDecimal("100"))).isEmpty();
        assertThat(AmountUnitResolver.inferUnit(new BigDecimal("100"), BigDecimal.ZERO)).isEmpty();
    }

    @Test
    @DisplayName("시트 ③의 원화 금액이 백만 이상이면 원 단위로 제안한다")
    void suggestsWonForLargeKrwAmounts() {
        // 자금운용실 실측: 연간 841,854,085원
        assertThat(
                        AmountUnitResolver.suggestGeneralExpenseUnit(
                                List.of(new BigDecimal("841854085"), new BigDecimal("35838000"))))
                .isEqualTo(AmountUnit.WON);
    }

    @Test
    @DisplayName("시트 ③의 원화 금액이 모두 작으면 헤더 표기대로 천원 단위로 제안한다")
    void suggestsThousandForSmallKrwAmounts() {
        assertThat(
                        AmountUnitResolver.suggestGeneralExpenseUnit(
                                List.of(new BigDecimal("841854"), new BigDecimal("35838"))))
                .isEqualTo(AmountUnit.THOUSAND);
    }

    @Test
    @DisplayName("원화 행이 하나도 없으면 원 단위로 제안한다")
    void suggestsWonWhenNoKrwRow() {
        assertThat(AmountUnitResolver.suggestGeneralExpenseUnit(List.of()))
                .isEqualTo(AmountUnit.WON);
    }

    @Test
    @DisplayName("단위가 배수와 표기명을 함께 들고 있어 매직 넘버를 쓰지 않는다")
    void unitCarriesMultiplierAndLabel() {
        assertThat(AmountUnit.WON.multiplier()).isEqualTo(1L);
        assertThat(AmountUnit.THOUSAND.multiplier()).isEqualTo(1_000L);
        assertThat(AmountUnit.MILLION.multiplier()).isEqualTo(1_000_000L);
        assertThat(AmountUnit.MILLION.label()).isEqualTo("백만원(KRW)");
        // 배수가 원화 행에만 걸린다는 것을 경고 문구에서 바로 읽도록 표기명에 통화를 붙인다
        assertThat(AmountUnit.WON.label()).isEqualTo("원(KRW)");
        assertThat(AmountUnit.THOUSAND.label()).isEqualTo("천원(KRW)");
    }

    @Test
    @DisplayName("단위가 금액을 원 단위로 편다")
    void convertsToWon() {
        assertThat(AmountUnit.MILLION.toWon(new BigDecimal("2699")))
                .isEqualByComparingTo(new BigDecimal("2699000000"));
        assertThat(AmountUnit.WON.toWon(null)).isNull();
    }
}
