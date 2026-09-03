package com.kdb.it.common.iam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BranchCodesTest {

    @Test
    @DisplayName("부점코드가 9로 시작하면 국외점포다")
    void foreignWhenPrefixNine() {
        assertThat(BranchCodes.isForeign("920")).isTrue();
        assertThat(BranchCodes.isForeign("120")).isFalse();
        assertThat(BranchCodes.isForeign(null)).isFalse();
        assertThat(BranchCodes.isForeign("")).isFalse();
    }
}
