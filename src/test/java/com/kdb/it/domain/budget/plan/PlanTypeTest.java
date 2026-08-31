package com.kdb.it.domain.budget.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlanTypeTest {

    @Test
    void codeValuesResolveToThePlanType() {
        assertThat(PlanType.fromCode("01")).isEqualTo(PlanType.ESTABLISHMENT);
        assertThat(PlanType.fromCode("02")).isEqualTo(PlanType.ADJUSTMENT);
    }

    @Test
    void unknownCodeIsRejected() {
        assertThatThrownBy(() -> PlanType.fromCode("신규"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
