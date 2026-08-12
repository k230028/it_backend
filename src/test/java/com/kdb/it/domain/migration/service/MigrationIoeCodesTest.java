package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이관이 쓰는 비목코드 기본값과 자본예산 계열 판정을 고정합니다.
 *
 * <p>기본 비목값은 어댑터(자본예산이 만드는 품목)와 오케스트레이션(부문계획 조정이 다시 만드는 품목)이 **같은 값**을 써야 조정이 같은 비목의 편성행을 교체합니다.
 * 한쪽만 바뀌면 조정 품목이 새 비목으로 생겨 원래 품목이 남고 편성액이 이중으로 잡히므로, 상수값 자체를 테스트로 못 박습니다.
 *
 * <p>{@link MigrationIoeCodes#isCapital}은 연도 스냅샷이 사업의 기존 편성률을 자본 계열/그 밖으로 나눌 때 쓰는 판정입니다.
 */
class MigrationIoeCodesTest {

    @Test
    @DisplayName("생성자는 유틸 클래스 인스턴스 생성을 차단한다")
    void constructor_인스턴스화시_예외발생() throws Exception {
        var constructor = MigrationIoeCodes.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(catchThrowable(constructor::newInstance))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("기본 비목값은 개발비 103·기계장치 101·기타무형 106이다")
    void 자본예산_기본비목값() {
        assertThat(MigrationIoeCodes.IOE_DEV).isEqualTo("103");
        assertThat(MigrationIoeCodes.IOE_HW).isEqualTo("101");
        assertThat(MigrationIoeCodes.IOE_SW).isEqualTo("106");
    }

    @Test
    @DisplayName("위임예산 품목은 국외 비목(기계장치 102·기타무형 105)을 쓴다")
    void 위임예산_국외비목값() {
        assertThat(MigrationIoeCodes.IOE_HW_OVERSEA).isEqualTo("102");
        assertThat(MigrationIoeCodes.IOE_SW_OVERSEA).isEqualTo("105");
    }

    @Test
    @DisplayName("자본예산 계열 비목코드는 101~107이다")
    void 자본계열_코드집합() {
        assertThat(MigrationIoeCodes.CAPITAL_CODES)
                .containsExactlyInAnyOrder("101", "102", "103", "104", "105", "106", "107");
    }

    @Test
    @DisplayName("자본예산 계열 비목이면 true 다")
    void isCapital_자본계열이면_true() {
        assertThat(MigrationIoeCodes.isCapital("103")).isTrue();
        assertThat(MigrationIoeCodes.isCapital("107")).isTrue();
        // 엑셀·보정값에 앞뒤 공백이 섞여 오는 경우가 있어 trim 후 판정한다
        assertThat(MigrationIoeCodes.isCapital(" 101 ")).isTrue();
    }

    @Test
    @DisplayName("일반관리비 계열·미등록·null은 false 다 (일반관리비로 취급)")
    void isCapital_그밖이면_false() {
        // 011 유지보수료는 실존하는 비목이지만 자본 계열이 아니다 — "세자리면 통과"식 완화를 막는다
        assertThat(MigrationIoeCodes.isCapital("011")).isFalse();
        assertThat(MigrationIoeCodes.isCapital("999")).isFalse();
        assertThat(MigrationIoeCodes.isCapital("")).isFalse();
        assertThat(MigrationIoeCodes.isCapital(null)).isFalse();
    }
}
