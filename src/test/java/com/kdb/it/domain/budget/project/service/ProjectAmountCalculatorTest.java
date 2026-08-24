package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectAmountCalculatorTest {

    private final ProjectAmountCalculator calculator = new ProjectAmountCalculator();

    @Test
    @DisplayName("원화와 외화 품목의 현재·예정·지급 금액을 독립적으로 합산한다")
    void calculate_sumsCurrentPlannedAndPaidAmountsIndependently() {
        ProjectAmountSummary summary =
                calculator.calculate(
                        List.of(
                                item("KRW", "100.000", "500.000", null),
                                item("USD", "140000.000", "50.000", "1400.0000")),
                        new BigDecimal("20.000"));

        assertThat(summary.currentRequestAmt()).isEqualByComparingTo("140100.000");
        assertThat(summary.plannedAmt()).isEqualByComparingTo("70500.000");
        assertThat(summary.paidAmt()).isEqualByComparingTo("20.000");
        assertThat(summary.totalRequiredAmt()).isEqualByComparingTo("210620.000");
    }

    @Test
    @DisplayName("지급금액이 null이면 0으로 합산한다")
    void calculate_treatsNullPaidAmountAsZero() {
        ProjectAmountSummary summary =
                calculator.calculate(List.of(item("KRW", "100", "500", null)), null);

        assertThat(summary.currentRequestAmt()).isEqualByComparingTo("100.000");
        assertThat(summary.plannedAmt()).isEqualByComparingTo("500.000");
        assertThat(summary.paidAmt()).isEqualByComparingTo("0.000");
        assertThat(summary.totalRequiredAmt()).isEqualByComparingTo("600.000");
    }

    @Test
    @DisplayName("품목의 null 금액은 0으로 합산한다")
    void calculate_treatsNullItemAmountsAsZero() {
        ProjectAmountSummary summary =
                calculator.calculate(List.of(item("KRW", null, null, null)), BigDecimal.ZERO);

        assertThat(summary.currentRequestAmt()).isEqualByComparingTo("0");
        assertThat(summary.plannedAmt()).isEqualByComparingTo("0");
        assertThat(summary.totalRequiredAmt()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("외화 품목의 환율이 없으면 계산을 거절한다")
    void calculate_rejectsForeignItemWithoutExchangeRate() {
        assertThatThrownBy(
                        () -> calculator.calculate(List.of(item("USD", "100", "50", null)), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("외화 품목의 유효한 환율이 필요합니다.");
    }

    @Test
    @DisplayName("외화 품목의 환율이 0 이하이면 계산을 거절한다")
    void calculate_rejectsForeignItemWithNonPositiveExchangeRate() {
        assertThatThrownBy(() -> calculator.calculate(List.of(item("USD", "100", "50", "0")), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("외화 품목의 유효한 환율이 필요합니다.");
    }

    private Bitemm item(String curC, String amt, String mplAmt, String xcr) {
        return Bitemm.builder()
                .curC(curC)
                .amt(amt == null ? null : new BigDecimal(amt))
                .mplAmt(mplAmt == null ? null : new BigDecimal(mplAmt))
                .xcr(xcr == null ? null : new BigDecimal(xcr))
                .build();
    }
}
