package com.kdb.it.common.i18n.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.i18n.dto.TranslationDto;
import com.kdb.it.common.i18n.model.TranslationColumns;
import com.kdb.it.common.i18n.model.TranslationTarget;
import com.kdb.it.common.i18n.service.TranslationCatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranslationAdminControllerTest {

    @Mock TranslationCatalogService service;

    @Test
    void 메뉴번역을_조회하고_저장한다() {
        TranslationAdminController controller = new TranslationAdminController(service);
        var values =
                List.of(new TranslationDto.Value("en", TranslationColumns.MNU_NM, "Dashboard"));
        when(service.findAll(TranslationTarget.MENU, "M1")).thenReturn(values);

        assertThat(controller.getTranslations("menu", "M1").getBody()).isEqualTo(values);
        controller.updateTranslations(
                "menu", "M1", new TranslationAdminController.UpdateRequest(values));

        verify(service).apply(TranslationTarget.MENU, "M1", values);
    }

    @Test
    void 공통코드_대상은_대소문자와_공백을_흘려도_같은_대상으로_해석한다() {
        TranslationAdminController controller = new TranslationAdminController(service);

        controller.updateTranslations(
                " Common-Code ",
                "7:ABUS_TC2:108:20260101",
                new TranslationAdminController.UpdateRequest(List.of()));

        verify(service).apply(TranslationTarget.COMMON_CODE, "7:ABUS_TC2:108:20260101", List.of());
    }

    @Test
    void 알수없는_대상은_거부한다() {
        TranslationAdminController controller = new TranslationAdminController(service);

        assertThatThrownBy(() -> controller.getTranslations("project", "P1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project");
        assertThatThrownBy(
                        () ->
                                controller.updateTranslations(
                                        null,
                                        "M1",
                                        new TranslationAdminController.UpdateRequest(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
