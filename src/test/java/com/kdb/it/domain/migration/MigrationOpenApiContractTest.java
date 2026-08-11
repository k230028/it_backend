package com.kdb.it.domain.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.dto.MigrationDto;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 이관 응답 DTO의 OpenAPI 계약을 고정합니다. {@code ApiResponseOpenApiContractTest}와 같은 방식(swagger
 * ModelConverters로 스키마를 해석해 requiredMode·nullable·allowableValues를 확인)을 따릅니다.
 *
 * <p>이 DTO들의 {@code @Schema} 주석이 프론트 생성 타입(api.d.ts)의 SoT이므로,
 * requiredMode·nullable·allowableValues가 바뀌면 프론트가 항상 오는 값을 optional로 다루거나 없는 값을 non-null로 다루게 됩니다.
 */
class MigrationOpenApiContractTest {

    @Test
    void dryRunResponseExposesRequiredAndNullableContracts() {
        assertAllPropertiesRequired(MigrationDto.DryRunResponse.class);
        assertAllPropertiesRequired(MigrationDto.Summary.class);
        assertAllPropertiesRequired(MigrationDto.Candidate.class);
        assertAllPropertiesRequired(MigrationDto.CellDiagnostic.class, "column");

        assertEnum(
                MigrationDto.CellDiagnostic.class,
                "code",
                "ORG_UNRESOLVED",
                "ORG_AMBIGUOUS",
                "USER_UNRESOLVED",
                "USER_AMBIGUOUS",
                "CODE_UNRESOLVED",
                "REQUIRED_MISSING",
                "DUPLICATE_EXISTS",
                "LENGTH_EXCEEDED",
                "PROJECT_NOT_FOUND",
                "AMOUNT_MISMATCH",
                "RATE_OUT_OF_RANGE",
                "DATE_UNPARSEABLE");
    }

    @Test
    void commitResponseExposesRequiredAndNullableContracts() {
        assertAllPropertiesRequired(MigrationDto.CommitResponse.class, "planReqDocNo");
    }

    private static void assertAllPropertiesRequired(Class<?> type, String... nullableProperties) {
        Schema<?> schema = resolve(type);
        Set<String> properties = schema.getProperties().keySet();
        Set<String> nullable = Set.of(nullableProperties);
        assertThat(schema.getRequired()).containsExactlyInAnyOrderElementsOf(properties);
        properties.forEach(
                name ->
                        assertThat(Boolean.TRUE.equals(property(schema, name).getNullable()))
                                .as("%s.%s nullable", type.getSimpleName(), name)
                                .isEqualTo(nullable.contains(name)));
    }

    private static void assertEnum(Class<?> type, String property, String... values) {
        assertThat(
                        property(resolve(type), property).getEnum().stream()
                                .map(String::valueOf)
                                .toList())
                .containsExactly(values);
    }

    private static Schema<?> resolve(Class<?> type) {
        ResolvedSchema resolved =
                ModelConverters.getInstance()
                        .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(false));
        return resolved.schema;
    }

    private static Schema<?> property(Schema<?> schema, String name) {
        return (Schema<?>) schema.getProperties().get(name);
    }
}
