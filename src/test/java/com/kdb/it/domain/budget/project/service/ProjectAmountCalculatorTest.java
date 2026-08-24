package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    @Test
    @DisplayName("현재·원화 예정·지급·총소요금액을 소수 셋째 자리 HALF_UP으로 정규화한다")
    void calculate_normalizesEverySummaryAmountToScaleThree() {
        ProjectAmountSummary summary =
                calculator.calculate(
                        List.of(item("KRW", "1.2345", "2.3455", null)), new BigDecimal("3.4565"));

        assertThat(summary.currentRequestAmt()).isEqualTo(new BigDecimal("1.235"));
        assertThat(summary.plannedAmt()).isEqualTo(new BigDecimal("2.346"));
        assertThat(summary.paidAmt()).isEqualTo(new BigDecimal("3.457"));
        assertThat(summary.totalRequiredAmt()).isEqualTo(new BigDecimal("7.038"));
    }

    @Test
    @DisplayName("NUMBER(18,3)의 정확한 최댓값을 보존한다")
    void calculate_acceptsExactNumberColumnMaximum() {
        ProjectAmountSummary summary =
                calculator.calculate(List.of(item("KRW", "999999999999999.999", null, null)), null);

        assertThat(summary.currentRequestAmt()).isEqualTo(new BigDecimal("999999999999999.999"));
    }

    @Test
    @DisplayName("넷째 자리 반올림 carry가 컬럼 최댓값 안이면 허용한다")
    void calculate_acceptsRoundingCarryAtNumberColumnBoundary() {
        ProjectAmountSummary summary =
                calculator.calculate(
                        List.of(item("KRW", "999999999999999.9985", null, null)), null);

        assertThat(summary.currentRequestAmt()).isEqualTo(new BigDecimal("999999999999999.999"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("overflowCases")
    @DisplayName("NUMBER(18,3) 범위를 넘는 사업 금액을 필드별 400 예외로 거부한다")
    void calculate_rejectsNumberColumnOverflow(
            String ignored, List<Bitemm> items, BigDecimal paidAmt, String fieldLabel) {
        assertThatThrownBy(() -> calculator.calculate(items, paidAmt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(fieldLabel + "이 저장 가능한 NUMBER(18,3) 범위를 넘습니다.");
    }

    private static Stream<Arguments> overflowCases() {
        return Stream.of(
                Arguments.of(
                        "당해 요청금액 반올림 overflow",
                        List.of(itemOf("999999999999999.9995", null)),
                        null,
                        "당해 요청금액"),
                Arguments.of(
                        "예정금액 반올림 overflow",
                        List.of(itemOf(null, "999999999999999.9995")),
                        null,
                        "예정금액"),
                Arguments.of(
                        "지급금액 반올림 overflow",
                        List.of(),
                        new BigDecimal("999999999999999.9995"),
                        "지급금액"),
                Arguments.of(
                        "합산 총소요금액 overflow",
                        List.of(itemOf("999999999999999.999", "0.001")),
                        BigDecimal.ZERO,
                        "총소요금액"));
    }

    private static Bitemm itemOf(String amount, String plannedAmount) {
        return Bitemm.builder()
                .curC("KRW")
                .amt(amount == null ? null : new BigDecimal(amount))
                .mplAmt(plannedAmount == null ? null : new BigDecimal(plannedAmount))
                .build();
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
