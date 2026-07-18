package com.kdb.it.common.notification.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NotificationMessageFormatter")
class NotificationMessageFormatterTest {

    @Test
    @DisplayName("null과 공백은 빈 문자열로 정규화한다")
    void abbreviate_blank_returnsEmptyString() {
        assertThat(NotificationMessageFormatter.abbreviate(null, 10)).isEmpty();
        assertThat(NotificationMessageFormatter.abbreviate("   ", 10)).isEmpty();
    }

    @Test
    @DisplayName("최대 길이를 초과하면 말줄임표를 포함해 지정 길이로 자른다")
    void abbreviate_longText_truncatesWithEllipsis() {
        assertThat(NotificationMessageFormatter.abbreviate("123456", 5)).isEqualTo("1234…");
    }

    @Test
    @DisplayName("최대 길이가 1 이하이면 말줄임표 없이 안전하게 자른다")
    void abbreviate_maxLengthOne_truncatesWithoutEllipsis() {
        assertThat(NotificationMessageFormatter.abbreviate("123", 1)).isEqualTo("1");
    }
}
