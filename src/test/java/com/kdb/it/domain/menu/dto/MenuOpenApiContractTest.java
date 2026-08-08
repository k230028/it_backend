package com.kdb.it.domain.menu.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class MenuOpenApiContractTest {

    @Test
    void menuNodePropertiesAreRequiredAndNullableFieldsAreExplicit() {
        assertThat(MenuDto.Node.class.getDeclaredFields())
                .allSatisfy(
                        field ->
                                assertThat(schema(field).requiredMode())
                                        .as(field.getName())
                                        .isEqualTo(Schema.RequiredMode.REQUIRED));

        assertThat(schema("hrkMnuId").nullable()).isTrue();
        assertThat(schema("srePth").nullable()).isTrue();
        assertThat(schema("imkNm").nullable()).isTrue();
        assertThat(schema("mnuTpC").allowableValues()).containsExactly("GRP", "LNK", "PGE", "BRD");
    }

    private static Schema schema(String fieldName) {
        try {
            return schema(MenuDto.Node.class.getDeclaredField(fieldName));
        } catch (NoSuchFieldException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Schema schema(Field field) {
        return field.getAnnotation(Schema.class);
    }
}
