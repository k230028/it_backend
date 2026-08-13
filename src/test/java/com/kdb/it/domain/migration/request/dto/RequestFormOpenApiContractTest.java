package com.kdb.it.domain.migration.request.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.dto.MigrationDto;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 편성요청서 반입 DTO의 OpenAPI 계약을 고정합니다. {@code MigrationOpenApiContractTest}와 같은 방식(swagger
 * ModelConverters로 스키마를 해석해 requiredMode·nullable·allowableValues를 확인)을 따릅니다.
 *
 * <p>어노테이션 존재만 확인하지 않고 실제로 생성되는 스키마를 봅니다 — {@code @Schema}는 {@code RECORD_COMPONENT}를 타깃에 포함하지 않아
 * 레코드 컴포넌트에서 리플렉션으로 직접 읽히지 않습니다. 게다가 프론트 생성 타입의 SoT는 어노테이션이 아니라 그 결과 스키마입니다.
 */
class RequestFormOpenApiContractTest {

    @Test
    @DisplayName("응답 DTO는 모든 속성이 required이고 null 가능 속성만 nullable이다")
    void responseExposesRequiredAndNullableContracts() {
        assertAllPropertiesRequired(RequestFormDto.ImportResponse.class);
        assertAllPropertiesRequired(RequestFormDto.ImportSummary.class);
        assertAllPropertiesRequired(RequestFormDto.CreatedRecord.class);
        assertAllPropertiesRequired(
                RequestFormDto.FileResult.class, "suggestedGeneralExpenseMultiplier");
        assertAllPropertiesRequired(
                RequestFormDto.FormDiagnostic.class, "sheet", "excelRow", "field");
    }

    @Test
    @DisplayName("요청 DTO도 모든 속성이 required이고 보정 가능한 값만 nullable이다")
    void requestExposesRequiredAndNullableContracts() {
        assertAllPropertiesRequired(RequestFormDto.ImportManifest.class);
        assertAllPropertiesRequired(
                RequestFormDto.FileEntry.class,
                "deptCodeOverride",
                "generalExpenseMultiplier",
                "bgUntAbusC");
        assertAllPropertiesRequired(RequestFormDto.CellOverride.class, "sheet", "excelRow");
    }

    @Test
    @DisplayName("진단 코드 enum 값 집합이 고정되어 있다")
    void diagnosticCodeEnumIsFixed() {
        assertEnum(
                RequestFormDto.FormDiagnostic.class,
                "code",
                "FILE_UNREADABLE",
                "SHEET_NOT_FOUND",
                "ANCHOR_NOT_FOUND",
                "ORG_UNRESOLVED",
                "ORG_AMBIGUOUS",
                "USER_UNRESOLVED",
                "USER_AMBIGUOUS",
                "CODE_UNRESOLVED",
                "CODE_AMBIGUOUS",
                "REQUIRED_MISSING",
                "DUPLICATE_EXISTS",
                "LENGTH_EXCEEDED",
                "UNIT_UNCERTAIN",
                "AMOUNT_MISMATCH",
                "OPTIONAL_MISSING",
                "SUBSTITUTE_DROPPED",
                "DATE_UNPARSEABLE");
    }

    @Test
    @DisplayName("시트 종류 enum 값 집합이 고정되어 있다")
    void sheetKindEnumIsFixed() {
        assertEnum(
                RequestFormDto.CellOverride.class,
                "sheet",
                "CAPITAL_OVERVIEW",
                "CAPITAL_RESOURCE",
                "RECURRING",
                "GENERAL_EXPENSE");
    }

    @Test
    @DisplayName("파일 상태 enum 값 집합이 고정되어 있다")
    void fileStatusEnumIsFixed() {
        assertEnum(RequestFormDto.FileResult.class, "status", "APPLIED", "BLOCKED", "FAILED");
    }

    @Test
    @DisplayName("모든 진단 코드가 심각도를 선언하고 blocks()가 그와 일치한다")
    void everyDiagnosticCodeDeclaresSeverity() {
        for (RequestFormDiagnosticCode code : RequestFormDiagnosticCode.values()) {
            assertThat(code.severity()).as("%s", code).isNotNull();
            assertThat(code.blocks())
                    .as("%s", code)
                    .isEqualTo(code.severity() == MigrationDto.Severity.BLOCKER);
        }
    }

    @Test
    @DisplayName("진단 팩터리는 코드의 심각도를 그대로 따르고 후보 null을 빈 목록으로 접는다")
    void diagnosticFactoryCopiesSeverityFromCode() {
        RequestFormDto.FormDiagnostic blocker =
                RequestFormDto.FormDiagnostic.of(
                        null, null, "ioeC", RequestFormDiagnosticCode.CODE_UNRESOLVED, "메시지", null);
        RequestFormDto.FormDiagnostic warning =
                RequestFormDto.FormDiagnostic.of(
                        null,
                        null,
                        "bzDttNm",
                        RequestFormDiagnosticCode.OPTIONAL_MISSING,
                        "메시지",
                        null);

        assertThat(blocker.severity()).isEqualTo(MigrationDto.Severity.BLOCKER);
        assertThat(warning.severity()).isEqualTo(MigrationDto.Severity.WARNING);
        assertThat(blocker.candidates()).isEmpty();
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
