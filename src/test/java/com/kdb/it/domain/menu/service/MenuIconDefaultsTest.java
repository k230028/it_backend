package com.kdb.it.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IMK_NM 컬럼 부재 시 쓰는 메뉴 기본 아이콘 스냅샷")
class MenuIconDefaultsTest {

    /** 아이콘 클래스 저장 규약(it_backend/CLAUDE.md §8)과 동일한 허용 패턴. */
    private static final Pattern ICON_CLASS = Pattern.compile("^[a-z0-9 -]{1,100}$");

    @Test
    @DisplayName("2026-08-13 로컬 DB 스냅샷 42건을 담는다")
    void holdsSnapshotOf42Menus() {
        assertThat(MenuIconDefaults.all()).hasSize(42);
    }

    @Test
    @DisplayName("모든 값이 아이콘 클래스 저장 규약을 만족한다")
    void everyValueMatchesStorageRule() {
        assertThat(MenuIconDefaults.all().values())
                .allSatisfy(icon -> assertThat(ICON_CLASS.matcher(icon).matches()).isTrue());
    }

    @Test
    @DisplayName("스냅샷에 있는 메뉴는 해당 아이콘을, 없는 메뉴와 null은 null을 돌려준다")
    void iconOf_returnsSnapshotValueOrNull() {
        assertThat(MenuIconDefaults.iconOf("MHED0001")).isEqualTo("pi pi-file-check");
        assertThat(MenuIconDefaults.iconOf("MAUD0003")).isEqualTo("pi pi-clock");
        // 스냅샷 이후 생성된 메뉴는 null로 내려가고 프론트 iconFor()가 DEFAULT_MENU_ICON으로 받는다.
        assertThat(MenuIconDefaults.iconOf("MNU9999999")).isNull();
        assertThat(MenuIconDefaults.iconOf(null)).isNull();
    }

    @Test
    @DisplayName("반환 맵은 불변이다")
    void allIsImmutable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> MenuIconDefaults.all().put("X", "pi pi-home"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
