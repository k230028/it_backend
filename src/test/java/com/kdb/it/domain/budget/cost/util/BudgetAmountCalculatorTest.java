package com.kdb.it.domain.budget.cost.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BudgetAmountCalculator 단위 테스트.
 *
 * <p>CONTEXT.md 결정 B / C / D 4개 케이스 + isForeignRow 보조 검증.
 */
class BudgetAmountCalculatorTest {

    @Test
    @DisplayName("생성자는 유틸 클래스 인스턴스 생성을 차단한다")
    void constructor_인스턴스화시_예외발생() throws Exception {
        var constructor = BudgetAmountCalculator.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(constructor::newInstance))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName(
            "외화 정상: USD fcAmt=1000.000 × xcr=1300.5000 → krw=1300500.0000, fcAmt=1000.000 (클라 위조 999.999 무시)")
    void reconcileAmount_외화정상_서버재계산() {
        BigDecimal fcAmt = new BigDecimal("1000.000");
        BigDecimal krwClient = new BigDecimal("999.999"); // 클라 위조
        String curC = "USD";
        BigDecimal xcr = new BigDecimal("1300.5000");

        BigDecimal[] result = BudgetAmountCalculator.reconcileAmount(fcAmt, krwClient, curC, xcr);

        assertThat(result).hasSize(2);
        assertThat(result[0]).isEqualByComparingTo(new BigDecimal("1300500.0000"));
        assertThat(result[1]).isEqualByComparingTo(new BigDecimal("1000.000"));
    }

    @Test
    @DisplayName("원화 KRW: krwAmt=5000000 그대로 보존, fcAmt=null (결정 B)")
    void reconcileAmount_원화KRW_클라값보존_fcAmtNull() {
        BigDecimal krwClient = new BigDecimal("5000000");

        BigDecimal[] result = BudgetAmountCalculator.reconcileAmount(null, krwClient, "KRW", null);

        assertThat(result[0]).isEqualByComparingTo(new BigDecimal("5000000"));
        assertThat(result[1]).isNull();
    }

    @Test
    @DisplayName("외화이나 xcr=0: krw 클라값 보존, fcAmt=null (결정 D — 데이터 불완전)")
    void reconcileAmount_외화xcr0_클라값보존_fcAmtNull() {
        BigDecimal fcAmt = new BigDecimal("1000");
        BigDecimal krwClient = new BigDecimal("999");

        BigDecimal[] result =
                BudgetAmountCalculator.reconcileAmount(fcAmt, krwClient, "USD", BigDecimal.ZERO);

        assertThat(result[0]).isEqualByComparingTo(new BigDecimal("999"));
        assertThat(result[1]).isNull();
    }

    @Test
    @DisplayName("외화이나 fcAmt=null: krw 클라값 보존, fcAmt=null (결정 D)")
    void reconcileAmount_외화fcAmtNull_클라값보존_fcAmtNull() {
        BigDecimal krwClient = new BigDecimal("12345");

        BigDecimal[] result =
                BudgetAmountCalculator.reconcileAmount(
                        null, krwClient, "USD", new BigDecimal("1300"));

        assertThat(result[0]).isEqualByComparingTo(new BigDecimal("12345"));
        assertThat(result[1]).isNull();
    }

    @Test
    @DisplayName("외화이나 환율이 null이면 원화 입력값을 보존한다")
    void reconcileAmount_외화환율Null_클라값보존() {
        BigDecimal[] result =
                BudgetAmountCalculator.reconcileAmount(
                        new BigDecimal("10"), new BigDecimal("999"), "USD", null);

        assertThat(result[0]).isEqualByComparingTo("999");
        assertThat(result[1]).isNull();
    }

    @Test
    @DisplayName("외화 환율이 음수이면 불완전 입력으로 보고 원화 입력값을 보존한다")
    void reconcileAmount_외화환율음수_클라값보존() {
        BigDecimal[] result =
                BudgetAmountCalculator.reconcileAmount(
                        new BigDecimal("10"), new BigDecimal("999"), "USD", new BigDecimal("-1"));

        assertThat(result[0]).isEqualByComparingTo("999");
        assertThat(result[1]).isNull();
    }

    @Test
    @DisplayName("외화 재계산 결과는 소수 넷째 자리에서 HALF_UP 반올림한다")
    void reconcileAmount_외화소수경계_셋째자리반올림() {
        BigDecimal[] result =
                BudgetAmountCalculator.reconcileAmount(
                        new BigDecimal("1.2345"), BigDecimal.ZERO, "USD", BigDecimal.ONE);

        assertThat(result[0]).isEqualTo(new BigDecimal("1.235"));
        assertThat(result[1]).isEqualByComparingTo("1.2345");
    }

    @Test
    @DisplayName("통화코드가 null이면 원화 행으로 처리한다")
    void reconcileAmount_통화코드Null_원화값보존() {
        BigDecimal[] result =
                BudgetAmountCalculator.reconcileAmount(
                        new BigDecimal("10"), new BigDecimal("777"), null, new BigDecimal("1300"));

        assertThat(result[0]).isEqualByComparingTo("777");
        assertThat(result[1]).isNull();
    }

    @Test
    @DisplayName("isForeignRow: null/KRW이면 false, 그 외 통화코드면 true")
    void isForeignRow_판정() {
        assertThat(BudgetAmountCalculator.isForeignRow(null)).isFalse();
        assertThat(BudgetAmountCalculator.isForeignRow("KRW")).isFalse();
        assertThat(BudgetAmountCalculator.isForeignRow("USD")).isTrue();
        assertThat(BudgetAmountCalculator.isForeignRow("JPY")).isTrue();
    }
}
