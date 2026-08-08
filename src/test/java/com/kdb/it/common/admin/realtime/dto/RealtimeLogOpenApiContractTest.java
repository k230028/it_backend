package com.kdb.it.common.admin.realtime.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RealtimeLogOpenApiContractTest {

    @Test
    void responsePropertiesAreRequiredAndNullableFieldsAreExplicit() {
        assertRequired(RealtimeLogDto.FeedRow.class);
        assertRequired(RealtimeLogDto.Snapshot.class);

        assertThat(schema(RealtimeLogDto.FeedRow.class, "chgUsid").nullable()).isTrue();
        assertThat(schema(RealtimeLogDto.FeedRow.class, "guid").nullable()).isTrue();
        assertThat(schema(RealtimeLogDto.FeedRow.class, "chgTp").allowableValues())
                .containsExactly("C", "U", "D");
        assertThat(schema(RealtimeLogDto.FeedRow.class, "delYn").allowableValues())
                .containsExactly("Y", "N");
    }

    private static void assertRequired(Class<?> recordType) {
        assertThat(recordType.getRecordComponents())
                .allSatisfy(
                        component ->
                                assertThat(schema(component).requiredMode())
                                        .as(
                                                "%s.%s",
                                                recordType.getSimpleName(), component.getName())
                                        .isEqualTo(Schema.RequiredMode.REQUIRED));
    }

    private static Schema schema(Class<?> recordType, String componentName) {
        return Arrays.stream(recordType.getRecordComponents())
                .filter(component -> component.getName().equals(componentName))
                .findFirst()
                .map(RealtimeLogOpenApiContractTest::schema)
                .orElseThrow();
    }

    private static Schema schema(RecordComponent component) {
        return component.getAccessor().getAnnotation(Schema.class);
    }
}
