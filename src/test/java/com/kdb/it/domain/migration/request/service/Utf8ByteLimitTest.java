package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Utf8ByteLimitTest {

    @Test
    @DisplayName("length는 null을 0으로, 문자 종류별 UTF-8 바이트 수를 반환한다")
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
    @DisplayName("truncate는 제한 이하의 문자열을 절단 없이 그대로 반환한다")
    void truncate_제한이하_원본반환() {
        String value = "가나다";

        assertThat(Utf8ByteLimit.truncate(value, 9)).isSameAs(value);
    }

    @Test
    @DisplayName("truncate는 한글 3바이트 경계에서 코드포인트를 쪼개지 않는다")
    void truncate_한글경계_코드포인트보존() {
        // given: "가나다"는 9바이트, 8바이트 제한이면 세 번째 글자가 경계에 걸린다
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
    @DisplayName("truncate는 음수 제한의 빈 문자열도 빈 문자열로 반환한다")
    void truncate_음수제한_빈문자열반환() {
        // given: 빈 문자열(0바이트)이 음수 제한을 넘어 절단 루프를 통과하는 방어적 경계
        assertThat(Utf8ByteLimit.truncate("", -1)).isEmpty();
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
