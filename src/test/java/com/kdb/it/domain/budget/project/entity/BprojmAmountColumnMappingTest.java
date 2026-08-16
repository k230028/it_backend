package com.kdb.it.domain.budget.project.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.log.entity.BprojmL;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 정보화사업 금액 컬럼 3종이 마스터(BPROJM)와 변경로그(BPROJL) 양쪽에 같은 이름·같은 컬럼으로 매핑되는지 고정한다.
 *
 * <p>AuditLogPersister가 @Column 필드를 필드명으로 매칭해 복사하므로, 한쪽에만 필드를 추가하면 변경이력에서 값이 조용히 누락된다.
 */
class BprojmAmountColumnMappingTest {

    private static final List<String> AMOUNT_FIELDS = List.of("totRqmAmt", "mplAmt", "dfrAmt");

    @Test
    @DisplayName("금액 3종이 BPROJM에 NUMBER(18,3)으로 매핑된다")
    void bprojmHasAmountColumns() {
        assertThat(columnNameOf(Bprojm.class, "totRqmAmt")).isEqualTo("TOT_RQM_AMT");
        assertThat(columnNameOf(Bprojm.class, "mplAmt")).isEqualTo("MPL_AMT");
        assertThat(columnNameOf(Bprojm.class, "dfrAmt")).isEqualTo("DFR_AMT");
        for (String name : AMOUNT_FIELDS) {
            Column column = fieldOf(Bprojm.class, name).getAnnotation(Column.class);
            assertThat(fieldOf(Bprojm.class, name).getType()).isEqualTo(BigDecimal.class);
            assertThat(column.precision()).isEqualTo(18);
            assertThat(column.scale()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("변경로그(BPROJL)에 같은 이름·같은 컬럼으로 대응 필드가 있다")
    void bprojmLogMirrorsAmountColumns() {
        for (String name : AMOUNT_FIELDS) {
            assertThat(columnNameOf(BprojmL.class, name))
                    .as("BprojmL.%s 컬럼 매핑", name)
                    .isEqualTo(columnNameOf(Bprojm.class, name));
        }
    }

    private static Field fieldOf(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new AssertionError(type.getSimpleName() + "." + name + " 필드가 없습니다", e);
        }
    }

    private static String columnNameOf(Class<?> type, String name) {
        return fieldOf(type, name).getAnnotation(Column.class).name();
    }
}
