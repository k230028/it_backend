package com.kdb.it.domain.budget.document.formguide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Set;

class FormGuideCatalogTest {

    @Test
    void require_returnsTheInformationProjectEntryForItsStableGuideId() {
        assertThat(FormGuideCatalog.require("info.basic.abusNm"))
                .extracting(
                        FormGuideCatalog.Entry::scope,
                        FormGuideCatalog.Entry::section,
                        FormGuideCatalog.Entry::fieldLabel,
                        FormGuideCatalog.Entry::controlType)
                .containsExactly(FormGuideScope.INFO, "기본 정보", "사업명", "AutoComplete");
    }

    @Test
    void require_rejectsAnIdOutsideTheFixedCatalog() {
        assertThatThrownBy(() -> FormGuideCatalog.require("info.unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 길라잡이 ID입니다");
    }

    @Test
    void entries_separatesInformationAndOrdinaryProjectFieldsIncludingSharedResourceColumns() {
        assertThat(guideIds(FormGuideScope.INFO))
                .contains(
                        "info.basic.abusNm",
                        "info.overview.prjDes",
                        "info.classification.bzDtt",
                        "info.criteria.lblFsgTlm",
                        "info.schedule.sttDt",
                        "info.resource.ioe",
                        "info.resource.introDate");
        assertThat(guideIds(FormGuideScope.COST))
                .contains(
                        "cost.basic.abusNm",
                        "cost.overview.plm",
                        "cost.department.svnDpmCgpr",
                        "cost.resource.ioe",
                        "cost.resource.paymentCycle");
        assertThat(guideIds(FormGuideScope.INFO)).allMatch(id -> id.startsWith("info."));
        assertThat(guideIds(FormGuideScope.COST)).allMatch(id -> id.startsWith("cost."));
    }

    private Set<String> guideIds(FormGuideScope scope) {
        return FormGuideCatalog.entries(scope).stream()
                .map(FormGuideCatalog.Entry::guideId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
