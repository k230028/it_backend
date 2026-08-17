package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.dto.MigrationDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 진단 코드 목록과 응답 스키마의 정합 게이트 — BE-38.
 *
 * <p>{@code MigrationDto.CellDiagnostic.code}의 {@code allowableValues}는 프론트 생성 타입({@code
 * it_frontend/app/types/api.d.ts})의 union을 만드는 SoT입니다. 발행하는 코드가 그 목록에 없으면 프론트 타입에서 누락되고, 목록에만 있고
 * 발행되지 않는 코드는 죽은 분기를 남깁니다. 실제로 {@code CREATE_NOT_SUPPORTED}가 발행되면서도 목록에서 빠져 있던 적이 있습니다(MIG-23).
 *
 * <p>{@link MigrationDiagnosticCode}가 발행 가능한 코드의 단일 출처이므로, 이 테스트는 두 목록이 정확히 같은 집합인지만 봅니다.
 */
class MigrationDiagnosticCodeContractTest {

    @Test
    @DisplayName("발행 가능한 진단 코드와 응답 스키마 allowableValues가 정확히 일치한다")
    void 진단_코드_목록이_스키마와_일치한다() {
        Set<String> declared =
                Arrays.stream(MigrationDiagnosticCode.values())
                        .map(Enum::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> published = new LinkedHashSet<>(Arrays.asList(allowableValuesOfCode()));

        assertThat(published)
                .withFailMessage(
                        """
                        진단 코드 목록 불일치:
                          스키마에만 있음: %s
                          enum에만 있음:  %s

                        MigrationDiagnosticCode에 코드를 추가·삭제하면 MigrationDto.CellDiagnostic.code의 allowableValues도 함께 고치십시오.
                        """
                                .formatted(
                                        difference(published, declared),
                                        difference(declared, published)))
                .isEqualTo(declared);
    }

    /**
     * {@code CellDiagnostic.code}에 붙은 {@code @Schema}의 {@code allowableValues}를 읽는다.
     *
     * <p>레코드 컴포넌트에 단 어노테이션은 컴포넌트·필드·접근자로 전파되며 어디에 남는지는 어노테이션의 {@code @Target}에 달려 있으므로 세 위치를 차례로
     * 확인합니다.
     */
    private static String[] allowableValuesOfCode() {
        Schema schema = schemaOfRecordComponent(MigrationDto.CellDiagnostic.class, "code");
        assertThat(schema).as("CellDiagnostic.code에 @Schema가 있어야 합니다").isNotNull();
        assertThat(schema.allowableValues())
                .as("CellDiagnostic.code에 allowableValues가 있어야 합니다")
                .isNotEmpty();
        return schema.allowableValues();
    }

    private static Schema schemaOfRecordComponent(Class<?> type, String name) {
        for (RecordComponent component : type.getRecordComponents()) {
            if (!component.getName().equals(name)) {
                continue;
            }
            Schema onComponent = component.getAnnotation(Schema.class);
            if (onComponent != null) {
                return onComponent;
            }
            Schema onAccessor = component.getAccessor().getAnnotation(Schema.class);
            if (onAccessor != null) {
                return onAccessor;
            }
            try {
                return type.getDeclaredField(name).getAnnotation(Schema.class);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("레코드 필드를 찾지 못했습니다: " + name, e);
            }
        }
        throw new IllegalStateException("레코드 컴포넌트를 찾지 못했습니다: " + name);
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> out = new LinkedHashSet<>(left);
        out.removeAll(right);
        return out;
    }
}
