package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DateFormatUtilTest {

    @Test
    @DisplayName("ISO 날짜(YYYY-MM-DD)를 yyyyMMdd 8자리로 정규화")
    void toYmd8_isoDate_strips() {
        assertThat(DateFormatUtil.toYmd8("2026-06-04")).isEqualTo("20260604");
    }

    @Test
    @DisplayName("이미 yyyyMMdd면 불변")
    void toYmd8_alreadyYmd_unchanged() {
        assertThat(DateFormatUtil.toYmd8("20260604")).isEqualTo("20260604");
    }

    @Test
    @DisplayName("null/빈값은 그대로 반환")
    void toYmd8_nullOrBlank_passthrough() {
        assertThat(DateFormatUtil.toYmd8(null)).isNull();
        assertThat(DateFormatUtil.toYmd8("")).isEqualTo("");
        assertThat(DateFormatUtil.toYmd8("  ")).isEqualTo("  ");
    }

    @Test
    @DisplayName("타임스탬프 등 8자 초과 시 앞 8자만")
    void toYmd8_longValue_truncates() {
        assertThat(DateFormatUtil.toYmd8("2026-06-04T00:00:00")).isEqualTo("20260604");
    }
}
