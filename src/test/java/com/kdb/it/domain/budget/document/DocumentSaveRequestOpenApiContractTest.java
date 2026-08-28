package com.kdb.it.domain.budget.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.document.budgetnote.BudgetCardNoteDto;
import com.kdb.it.domain.budget.document.formguide.FormGuideDto;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

/** 문서 관리 저장 요청의 OpenAPI 스키마 이름 충돌을 방지한다. */
class DocumentSaveRequestOpenApiContractTest {

    @Test
    void saveRequestsExposeDistinctSchemaNames() {
        assertThat(schemaName(FormGuideDto.SaveRequest.class)).isEqualTo("FormGuideSaveRequest");
        assertThat(schemaName(BudgetCardNoteDto.SaveRequest.class))
                .isEqualTo("BudgetCardNoteSaveRequest");
    }

    private static String schemaName(Class<?> type) {
        Schema schema = type.getAnnotation(Schema.class);
        return schema == null ? null : schema.name();
    }
}
