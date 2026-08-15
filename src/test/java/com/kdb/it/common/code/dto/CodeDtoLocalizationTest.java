package com.kdb.it.common.code.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.code.repository.CcodemResponseRow;
import com.kdb.it.common.i18n.model.TranslationColumns;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeDtoLocalizationTest {

    @Test
    void 번역된_컬럼만_덮어쓰고_식별필드는_원본을_유지한다() {
        CcodemResponseRow row =
                new CcodemResponseRow(
                        "PRJ_TP",
                        "001",
                        "신규개발",
                        "사업유형",
                        "신규",
                        "설명",
                        "A",
                        "TYPE",
                        "유형설명",
                        null,
                        1,
                        "20260101",
                        "20991231",
                        "N",
                        LocalDateTime.MIN,
                        "TEST",
                        LocalDateTime.MIN,
                        "TEST");

        CodeDto.Response response =
                CodeDto.Response.fromRow(
                        row,
                        Map.of(
                                TranslationColumns.CO_C_NM,
                                "Project type",
                                TranslationColumns.CDVA_NM,
                                "New development",
                                TranslationColumns.CO_C_INTN_CONE,
                                "Type description"));

        assertThat(response.getCNm()).isEqualTo("Project type");
        assertThat(response.getCdvaNm()).isEqualTo("New development");
        assertThat(response.getCTpDes()).isEqualTo("Type description");
        assertThat(response.getCTp()).isEqualTo("TYPE");
        assertThat(response.getCdvaDtlC()).isEqualTo("A");
    }
}
