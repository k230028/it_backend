package com.kdb.it.domain.log.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.domain.log.annotation.LogTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 번역 로그 엔티티가 마스터·로그 테이블 계약을 지키는지 고정합니다. */
class ClangmLSchemaContractTest {

    /** 마스터에서 로그로 복사되어야 하는 업무 컬럼입니다. */
    private static final List<String> BUSINESS_COLUMNS =
            List.of("TC_ID_CONE", "DTT_LAN_C", "TC_COL_NM", "TC_DES", "DTT_NM");

    @Test
    void 번역로그는_로그기반클래스를_상속하고_로그테이블에_매핑된다() {
        assertThat(BaseLogEntity.class).isAssignableFrom(ClangmL.class);
        assertThat(ClangmL.class.getAnnotation(Table.class).name()).isEqualTo("TPRMPP_CLANGL");
    }

    @Test
    void 마스터에_로그대상_어노테이션이_연결되어_있다() {
        LogTarget logTarget = Clangm.class.getAnnotation(LogTarget.class);

        assertThat(logTarget).isNotNull();
        assertThat(logTarget.entity()).isEqualTo(ClangmL.class);
    }

    @Test
    void 업무컬럼은_마스터와_컬럼명과_길이가_일치한다() {
        Map<String, Column> masterColumns = columnsOf(Clangm.class);
        Map<String, Column> logColumns = columnsOf(ClangmL.class);

        for (String columnName : BUSINESS_COLUMNS) {
            assertThat(masterColumns).as("마스터 컬럼 " + columnName).containsKey(columnName);
            assertThat(logColumns).as("로그 컬럼 " + columnName).containsKey(columnName);
            assertThat(logColumns.get(columnName).length())
                    .as(columnName + " 길이")
                    .isEqualTo(masterColumns.get(columnName).length());
        }
    }

    private static Map<String, Column> columnsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .collect(
                        Collectors.toMap(
                                field -> field.getAnnotation(Column.class).name(),
                                field -> field.getAnnotation(Column.class)));
    }
}
