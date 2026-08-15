package com.kdb.it.common.i18n.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ClangmSchemaContractTest {

    @Test
    void 번역엔티티는_BaseEntity와_세컬럼_복합키를_사용한다() {
        assertThat(BaseEntity.class).isAssignableFrom(Clangm.class);
        assertThat(Clangm.class.getAnnotation(Table.class).name()).isEqualTo("TPRMPP_CLANGM");
        assertThat(Clangm.class.getAnnotation(IdClass.class).value()).isEqualTo(ClangmId.class);

        Map<String, Field> idFields =
                Arrays.stream(Clangm.class.getDeclaredFields())
                        .filter(field -> field.isAnnotationPresent(Id.class))
                        .collect(Collectors.toMap(Field::getName, field -> field));

        assertThat(idFields).containsOnlyKeys("tcIdCone", "dttLanC", "tcColNm");
        assertColumn(idFields.get("tcIdCone"), "TC_ID_CONE", 255);
        assertColumn(idFields.get("dttLanC"), "DTT_LAN_C", 2);
        assertColumn(idFields.get("tcColNm"), "TC_COL_NM", 255);
    }

    @Test
    void 번역문과_구분명_컬럼은_DDL길이와_일치한다() throws Exception {
        assertColumn(Clangm.class.getDeclaredField("tcDes"), "TC_DES", 2000);
        assertColumn(Clangm.class.getDeclaredField("dttNm"), "DTT_NM", 100);
    }

    private static void assertColumn(Field field, String name, int length) {
        Column column = field.getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo(name);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.nullable()).isFalse();
    }
}
