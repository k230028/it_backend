package com.kdb.it.domain.menu.entity;

import com.kdb.it.domain.log.entity.CmenumL;
import com.kdb.it.domain.menu.dto.MenuDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MenuSchemaContractTest {

    @Test
    @DisplayName("메뉴 스키마 모델은 시스템상위메뉴ID를 노출하지 않는다")
    void menuModelsDoNotExposeSystemParentMenuId() {
        assertThat(fieldNames(Cmenud.class)).doesNotContain("sysHrkMnuId");
        assertThat(fieldNames(Cmenum.class)).doesNotContain("sysHrkMnuId");
        assertThat(fieldNames(CmenumL.class)).doesNotContain("sysHrkMnuId");
        assertThat(fieldNames(MenuDto.Route.class)).doesNotContain("sysHrkMnuId");
    }

    private List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .toList();
    }
}
