package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectAmountPolicyTest {

    @Test
    @DisplayName("정규화: null 금액은 0.000으로 본다")
    void normalize_null금액_0으로정규화() {
        BigDecimal result = ProjectAmountPolicy.normalize(null, "총소요금액");

        assertThat(result).isEqualByComparingTo("0");
        assertThat(result.scale()).isEqualTo(3);
    }

    @Test
    @DisplayName("정규화: 소수 넷째 자리는 HALF_UP으로 반올림한다")
    void normalize_소수넷째자리_HALF_UP반올림() {
        BigDecimal result = ProjectAmountPolicy.normalize(new BigDecimal("1.0005"), "총소요금액");

        assertThat(result).isEqualByComparingTo("1.001");
    }

    @Test
    @DisplayName("정규화: NUMBER(18,3) 범위를 넘으면 필드명을 담아 거부한다")
    void normalize_저장범위초과_필드명담은예외() {
        BigDecimal overMax = new BigDecimal("1000000000000000");

        assertThatThrownBy(() -> ProjectAmountPolicy.normalize(overMax, "총소요금액"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("총소요금액");
    }

    @Test
    @DisplayName("정규화: 음수도 절댓값 기준으로 저장 범위를 검증한다")
    void normalize_음수저장범위초과_예외() {
        BigDecimal underMin = new BigDecimal("-1000000000000000");

        assertThatThrownBy(() -> ProjectAmountPolicy.normalize(underMin, "예정금액"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유틸 클래스: 인스턴스화를 금지한다")
    void constructor_인스턴스화금지() throws Exception {
        // private 생성자는 리플렉션으로만 접근 가능하며 호출 즉시 거부되어야 한다
        Constructor<ProjectAmountPolicy> constructor =
                ProjectAmountPolicy.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
