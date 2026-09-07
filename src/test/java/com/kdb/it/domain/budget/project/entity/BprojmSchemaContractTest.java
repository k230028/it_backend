package com.kdb.it.domain.budget.project.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.log.entity.BprojmL;
import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BprojmSchemaContractTest {

    private static final String[] NEW_FIELDS = {
        "abusPulConeInf", "abusXptEffInf", "abusPulDrcnInf", "abusPulNcsInf"
    };

    @Test
    void 프로젝트와로그는새CLOB컬럼에매핑된다() throws Exception {
        assertLobMappings(Bprojm.class);
        assertLobMappings(BprojmL.class);
    }

    @Test
    void 프로젝트API는새JSON필드명을사용한다() {
        assertFields(ProjectDto.CreateRequest.class);
        assertFields(ProjectDto.UpdateRequest.class);
        assertFields(ProjectDto.Response.class);
    }

    private static void assertLobMappings(Class<?> type) throws Exception {
        assertFields(type);
        assertColumn(type, "abusPulConeInf", "ABUS_PUL_CONE_INF");
        assertColumn(type, "abusXptEffInf", "ABUS_XPT_EFF_INF");
        assertColumn(type, "abusPulDrcnInf", "ABUS_PUL_DRCN_INF");
        assertColumn(type, "abusPulNcsInf", "ABUS_PUL_NCS_INF");
    }

    private static void assertFields(Class<?> type) {
        assertThat(Arrays.stream(type.getDeclaredFields()).map(Field::getName))
                .contains(NEW_FIELDS)
                .doesNotContain("abusCone", "dgogPpoCone", "abusRngCone", "abusNcsCone");
    }

    private static void assertColumn(Class<?> type, String fieldName, String columnName)
            throws Exception {
        Field field = type.getDeclaredField(fieldName);
        assertThat(field.isAnnotationPresent(Lob.class)).isTrue();
        assertThat(field.getAnnotation(Column.class).name()).isEqualTo(columnName);
    }
}
