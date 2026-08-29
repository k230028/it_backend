package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Utf8ByteLimitTest {

    @Test
    @DisplayName("length는 null과 빈 문자열을 0으로, 문자 종류별 UTF-8 바이트 수를 반환한다")
    void length_문자종류별_바이트수반환() {
        assertThat(Utf8ByteLimit.length(null)).isZero();
        assertThat(Utf8ByteLimit.length("")).isZero();
        assertThat(Utf8ByteLimit.length("abc")).isEqualTo(3);
        assertThat(Utf8ByteLimit.length("가")).isEqualTo(3);
        assertThat(Utf8ByteLimit.length("😀")).isEqualTo(4);
    }

    @Test
    @DisplayName("truncate는 null 입력을 그대로 반환한다")
    void truncate_null입력_그대로반환() {
        assertThat(Utf8ByteLimit.truncate(null, 10)).isNull();
    }

    @Test
    @DisplayName("truncate는 빈 문자열을 그대로 반환한다")
    void truncate_빈문자열_그대로반환() {
        assertThat(Utf8ByteLimit.truncate("", 10)).isEmpty();
        assertThat(Utf8ByteLimit.truncate("", -1)).isEmpty();
    }

    @Test
    @DisplayName("truncate는 정확히 바이트 경계에 맞는 문자열을 그대로 반환한다")
    void truncate_정확한경계_원본반환() {
        String value = "a".repeat(97) + "가";

        assertThat(Utf8ByteLimit.length(value)).isEqualTo(100);
        assertThat(Utf8ByteLimit.truncate(value, 100)).isSameAs(value);
    }

    @Test
    @DisplayName("truncate는 1바이트 초과 문자열을 코드포인트 경계에서 자른다")
    void truncate_1바이트초과_코드포인트경계절단() {
        String value = "a".repeat(98) + "가";

        String fitted = Utf8ByteLimit.truncate(value, 100);

        assertThat(fitted).isEqualTo("a".repeat(98));
        assertThat(Utf8ByteLimit.length(fitted)).isEqualTo(98);
    }

    @Test
    @DisplayName("truncate는 한글 3바이트 경계에서 코드포인트를 쪼개지 않는다")
    void truncate_한글경계_코드포인트보존() {
        String fitted = Utf8ByteLimit.truncate("가나다", 8);

        assertThat(fitted).isEqualTo("가나");
        assertThat(Utf8ByteLimit.length(fitted)).isEqualTo(6);
    }

    @Test
    @DisplayName("truncate는 4바이트 서러게이트 쌍을 쪼개 대체문자를 만들지 않는다")
    void truncate_서러게이트쌍_대체문자없음() {
        String fitted = Utf8ByteLimit.truncate("😀😀", 7);

        assertThat(fitted).isEqualTo("😀");
        assertThat(fitted).doesNotContain("�");
    }

    @Test
    @DisplayName("truncate는 0바이트 제한이면 빈 문자열을 반환한다")
    void truncate_제한0_빈문자열반환() {
        assertThat(Utf8ByteLimit.truncate("가나다", 0)).isEmpty();
    }

    @Test
    @DisplayName("생성자는 바이트 유틸리티 인스턴스 생성을 차단한다")
    void constructor_인스턴스화시_예외발생() throws Exception {
        var constructor = Utf8ByteLimit.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(catchThrowable(constructor::newInstance))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
