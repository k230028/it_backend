package com.kdb.it.domain.budget.document.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.log.entity.BgdocmL;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BgdocmSchemaContractTest {

    @Test
    void 문서와로그는문서상세항목코드에매핑된다() throws Exception {
        assertMapping(Bgdocm.class);
        assertMapping(BgdocmL.class);
    }

    private static void assertMapping(Class<?> type) throws Exception {
        assertThat(Arrays.stream(type.getDeclaredFields()).map(Field::getName))
                .contains("docDtlItmC");
        Field field = type.getDeclaredField("docDtlItmC");
        Column column = field.getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo("DOC_DTL_ITM_C");
        assertThat(column.length()).isEqualTo(2);
    }
}
