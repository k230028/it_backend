package com.kdb.it.domain.migration.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.dto.SheetKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 어댑터 공유 헬퍼({@code ymToFirstDay}·{@code ymToLastDay}·{@code ymToYyyymm}·{@code ratePercent}·{@code
 * flag}·{@code amount})의 파싱·환산 규칙을 고정합니다. 세 어댑터(자본예산·전산일반관리비·이후 위임예산)가 공유하는 코드라 여기서 한 번만 검증합니다.
 */
class AdapterSupportTest {

    @Test
    @DisplayName("'26.05는 5월 1일과 5월 31일로, '26.02는 2월 28일로 바뀐다 (2026년은 평년)")
    void 연월을_월초와_월말로_바꾼다() {
        assertThat(AdapterSupport.ymToFirstDay("'26.05")).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(AdapterSupport.ymToLastDay("'26.05")).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(AdapterSupport.ymToLastDay("'26.02")).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("작은따옴표 없는 두 자리 연도와 한 자리 월(26.5), 말미 '월' 표기('26.12월)도 파싱한다")
    void 다양한_표기를_파싱한다() {
        assertThat(AdapterSupport.ymToFirstDay("26.5")).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(AdapterSupport.ymToFirstDay("'26.12월")).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    @DisplayName("파싱 불가 입력은 예외 없이 null을 반환한다")
    void 파싱불가_입력은_null이다() {
        assertThat(AdapterSupport.ymToFirstDay("미정")).isNull();
        assertThat(AdapterSupport.ymToFirstDay("")).isNull();
        assertThat(AdapterSupport.ymToFirstDay(null)).isNull();
        assertThat(AdapterSupport.ymToLastDay("미정")).isNull();
        assertThat(AdapterSupport.ymToYyyymm("미정")).isNull();
    }

    @Test
    @DisplayName("연월을 BSE_YM 6자리로 바꾼다")
    void 연월을_yyyymm으로_바꾼다() {
        assertThat(AdapterSupport.ymToYyyymm("'26.12")).isEqualTo("202612");
    }

    @Test
    @DisplayName("조정비율을 편성률로 바꾸고 0~100으로 잘라낸다")
    void 조정비율을_편성률로_바꾼다() {
        assertThat(AdapterSupport.ratePercent("0.7")).isEqualTo(70);
        assertThat(AdapterSupport.ratePercent("1")).isEqualTo(100);
        assertThat(AdapterSupport.ratePercent("")).isEqualTo(100);
        assertThat(AdapterSupport.ratePercent("-0.5")).isEqualTo(0);
        assertThat(AdapterSupport.ratePercent("1.5")).isEqualTo(100);
    }

    @Test
    @DisplayName("조정비율을 소수 배수로 그대로 읽고 빈 값·파싱 실패는 배수 1로 둔다")
    void 조정비율을_소수배수로_읽는다() {
        assertThat(AdapterSupport.rateFraction("0.7")).isEqualByComparingTo("0.7");
        assertThat(AdapterSupport.rateFraction("1")).isEqualByComparingTo("1");
        assertThat(AdapterSupport.rateFraction("")).isEqualByComparingTo("1");
        assertThat(AdapterSupport.rateFraction(null)).isEqualByComparingTo("1");
        assertThat(AdapterSupport.rateFraction("미정")).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("O·Y·○는 Y로, 빈 값과 그 외 문자는 N으로 바꾼다")
    void 플래그를_YN으로_바꾼다() {
        assertThat(AdapterSupport.flag("O")).isEqualTo("Y");
        assertThat(AdapterSupport.flag("Y")).isEqualTo("Y");
        assertThat(AdapterSupport.flag("○")).isEqualTo("Y");
        assertThat(AdapterSupport.flag("")).isEqualTo("N");
        assertThat(AdapterSupport.flag("아무거나")).isEqualTo("N");
        assertThat(AdapterSupport.flag(null)).isEqualTo("N");
    }

    @Test
    @DisplayName("시트 종류별 금액 배수가 다르게 적용된다 — 일반관리비 x1,000 / 자본예산·부문계획 x1,000,000 / 위임예산 x1")
    void 시트별_금액배수를_적용한다() {
        assertThat(AdapterSupport.amount("15401", SheetKind.COST))
                .isEqualByComparingTo(new BigDecimal("15401000"));
        assertThat(AdapterSupport.amount("16888", SheetKind.CAPITAL_PROJECT))
                .isEqualByComparingTo(new BigDecimal("16888000000"));
        assertThat(AdapterSupport.amount("16888", SheetKind.PLAN_ADJUSTMENT))
                .isEqualByComparingTo(new BigDecimal("16888000000"));
        assertThat(AdapterSupport.amount("16888", SheetKind.DELEGATED_BUDGET))
                .isEqualByComparingTo(new BigDecimal("16888"));
    }
}
