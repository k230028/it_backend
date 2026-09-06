package com.kdb.it.domain.budget.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 통합검색이 사용하는 사업 디렉터리 응답의 OpenAPI 필드 계약을 고정합니다. */
class ProjectDirectoryOpenApiContractTest {

    @Test
    @DisplayName("사업 디렉터리는 경상 여부와 신청서 상태 필드를 노출한다")
    void responseExposesOrdinaryAndApplicationStatusProperties() {
        Schema<?> schema =
                ModelConverters.getInstance()
                        .resolveAsResolvedSchema(
                                new AnnotatedType(ProjectDirectoryDto.Response.class)
                                        .resolveAsRef(false))
                        .schema;

        assertThat(schema.getProperties()).containsKeys("odnYn", "apfSts", "apfStsC");
        assertThat(property(schema, "odnYn").getType()).isEqualTo("string");
        assertThat(property(schema, "apfSts").getType()).isEqualTo("string");
        assertThat(property(schema, "apfStsC").getType()).isEqualTo("string");
    }

    private static Schema<?> property(Schema<?> schema, String name) {
        return (Schema<?>) schema.getProperties().get(name);
    }
}
