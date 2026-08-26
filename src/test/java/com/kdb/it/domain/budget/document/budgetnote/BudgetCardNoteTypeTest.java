package com.kdb.it.domain.budget.document.budgetnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 예산 카드 고정 유형 파싱 계약을 검증합니다. */
class BudgetCardNoteTypeTest {

    @Test
    @DisplayName("카드 유형은 앞뒤 공백과 대소문자를 정규화한다")
    void require_정규화() {
        assertThat(BudgetCardNoteType.require(" cost ")).isEqualTo(BudgetCardNoteType.COST);
    }

    @Test
    @DisplayName("null·공백·미지원 카드 유형은 거부한다")
    void require_잘못된유형_거부() {
        assertThatThrownBy(() -> BudgetCardNoteType.require(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BudgetCardNoteType.require(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BudgetCardNoteType.require("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
