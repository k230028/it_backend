package com.kdb.it.domain.budget.cost.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BtermmOrgNameByteValidationTest {

    @Test
    @DisplayName("주관부서명은 ASCII 100바이트까지 그대로 보존한다")
    void 주관부서명_ASCII_100바이트_보존() {
        Btermm target = Btermm.builder().tmnMngNo("TER-2026-0001").sno(1).build();
        String value = "a".repeat(100);

        target.assignSvnOrgNames(value, null);

        assertThat(target.getSvnDpmNm()).isSameAs(value);
    }

    @Test
    @DisplayName("주관부서명은 99바이트에 1바이트 문자를 더한 경계를 허용한다")
    void 주관부서명_99바이트_1바이트_경계허용() {
        Btermm target = Btermm.builder().tmnMngNo("TER-2026-0001").sno(1).build();
        String value = "a".repeat(99) + "!";

        assertThatCode(() -> target.assignSvnOrgNames(value, null)).doesNotThrowAnyException();
        assertThat(target.getSvnDpmNm()).isEqualTo(value);
    }

    @Test
    @DisplayName("주관부서명은 ASCII 101바이트를 저장 전에 거부한다")
    void 주관부서명_ASCII_101바이트_거부() {
        Btermm target = Btermm.builder().tmnMngNo("TER-2026-0001").sno(1).build();

        assertThatThrownBy(() -> target.assignSvnOrgNames("a".repeat(101), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100바이트");
    }

    @Test
    @DisplayName("주관부서명은 UTF-8 기준 한글 33자는 허용하고 34자는 거부한다")
    void 주관부서명_한글_바이트경계검증() {
        Btermm target = Btermm.builder().tmnMngNo("TER-2026-0001").sno(1).build();

        assertThatCode(() -> target.assignSvnOrgNames("가".repeat(33), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> target.assignSvnOrgNames("가".repeat(34), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("102바이트");
    }

    @Test
    @DisplayName("혼합 문자열은 정확히 100바이트까지 허용하고 101바이트에서 거부한다")
    void 주관부서명_혼합문자열_바이트경계검증() {
        Btermm target = Btermm.builder().tmnMngNo("TER-2026-0001").sno(1).build();

        assertThatCode(() -> target.assignSvnOrgNames("a".repeat(94) + "가나", null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> target.assignSvnOrgNames("a".repeat(95) + "가나", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("101바이트");
    }

    @Test
    @DisplayName("주관팀명도 surrogate pair 기준 100바이트 경계를 보존한다")
    void 주관팀명_서러게이트쌍_바이트경계검증() {
        Btermm target = Btermm.builder().tmnMngNo("TER-2026-0001").sno(1).build();

        assertThatCode(() -> target.assignSvnOrgNames(null, "😀".repeat(25)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> target.assignSvnOrgNames(null, "😀".repeat(26)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("104바이트");
    }
}
