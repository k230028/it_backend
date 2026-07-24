package com.kdb.it.common.code;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CodeDefaults — NOT NULL 코드 컬럼 빈값 정규화")
class CodeDefaultsTest {

    @Test
    @DisplayName("null과 공백은 해당없음('0')으로 정규화된다")
    void normalizesBlankToNotApplicable() {
        assertThat(CodeDefaults.orNotApplicable(null)).isEqualTo("0");
        assertThat(CodeDefaults.orNotApplicable("")).isEqualTo("0");
        assertThat(CodeDefaults.orNotApplicable("   ")).isEqualTo("0");
    }

    @Test
    @DisplayName("값이 있으면 원본을 그대로 반환한다")
    void keepsExistingCode() {
        assertThat(CodeDefaults.orNotApplicable("M")).isEqualTo("M");
        assertThat(CodeDefaults.orNotApplicable("20")).isEqualTo("20");
    }
}
