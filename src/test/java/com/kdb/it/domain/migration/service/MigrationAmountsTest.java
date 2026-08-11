package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.kdb.it.domain.migration.dto.SheetKind;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 시트 종류별 금액 배수를 고정합니다 — 검증기·어댑터가 공유하는 단일 출처입니다. */
class MigrationAmountsTest {

    @Test
    @DisplayName("생성자는 유틸 클래스 인스턴스 생성을 차단한다")
    void constructor_인스턴스화시_예외발생() throws Exception {
        var constructor = MigrationAmounts.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(catchThrowable(constructor::newInstance))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("일반관리비는 천원 단위라 1,000을 곱한다")
    void 일반관리비는_1000배수() {
        assertThat(MigrationAmounts.amountMultiplier(SheetKind.COST))
                .isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    @DisplayName("자본예산은 백만원 단위라 1,000,000을 곱한다")
    void 자본예산은_100만배수() {
        assertThat(MigrationAmounts.amountMultiplier(SheetKind.CAPITAL_PROJECT))
                .isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    @DisplayName("부문계획도 백만원 단위라 1,000,000을 곱한다")
    void 부문계획은_100만배수() {
        assertThat(MigrationAmounts.amountMultiplier(SheetKind.PLAN_ADJUSTMENT))
                .isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    @DisplayName("위임예산은 이미 원 단위라 1을 곱한다")
    void 위임예산은_1배수() {
        assertThat(MigrationAmounts.amountMultiplier(SheetKind.DELEGATED_BUDGET))
                .isEqualByComparingTo(BigDecimal.ONE);
    }
}
