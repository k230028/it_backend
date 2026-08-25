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
    void entries_returnsTheExactInformationProjectFieldSet() {
        assertThat(guideIds(FormGuideScope.INFO))
                .containsExactlyInAnyOrder(
                        "info.basic.bgYy",
                        "info.basic.pulDtt",
                        "info.basic.abusNm",
                        "info.overview.prjDes",
                        "info.overview.saf",
                        "info.overview.ncs",
                        "info.overview.xptEff",
                        "info.overview.plm",
                        "info.scope.prjRng",
                        "info.progress.pulPsg",
                        "info.progress.hrfPln",
                        "info.classification.bzDtt",
                        "info.classification.prjTp",
                        "info.classification.tchnTp",
                        "info.classification.mnUsr",
                        "info.criteria.dplYn",
                        "info.criteria.lblFsgTlm",
                        "info.department.svnHdq",
                        "info.department.svnDpm",
                        "info.department.svnDpmTlr",
                        "info.department.svnDpmCgpr",
                        "info.department.itDpm",
                        "info.department.itDpmTlr",
                        "info.department.itDpmCgpr",
                        "info.schedule.dfrAmt",
                        "info.schedule.rprSts",
                        "info.schedule.sttDt",
                        "info.schedule.endDt",
                        "info.schedule.prjPulPtt",
                        "info.resource.ioe",
                        "info.resource.item",
                        "info.resource.quantity",
                        "info.resource.currency",
                        "info.resource.gclAmt",
                        "info.resource.laterAmt",
                        "info.resource.basis",
                        "info.resource.introDate",
                        "info.resource.paymentCycle",
                        "info.resource.infoProtection",
                        "info.resource.integratedInfra");
    }

    @Test
    void entries_returnsTheExactOrdinaryProjectFieldSet() {
        assertThat(guideIds(FormGuideScope.COST))
                .containsExactlyInAnyOrder(
                        "cost.basic.bgYy",
                        "cost.basic.pulDtt",
                        "cost.basic.abusNm",
                        "cost.overview.plm",
                        "cost.overview.prjDes",
                        "cost.overview.saf",
                        "cost.scope.prjRng",
                        "cost.department.svnHdq",
                        "cost.department.svnDpm",
                        "cost.department.svnDpmTlr",
                        "cost.department.svnDpmCgpr",
                        "cost.resource.ioe",
                        "cost.resource.item",
                        "cost.resource.quantity",
                        "cost.resource.currency",
                        "cost.resource.gclAmt",
                        "cost.resource.laterAmt",
                        "cost.resource.basis",
                        "cost.resource.introDate",
                        "cost.resource.paymentCycle",
                        "cost.resource.infoProtection",
                        "cost.resource.integratedInfra");
    }

    @Test
    void resourceEntries_doNotContainDynamicRowNumbers() {
        assertThat(guideIds(FormGuideScope.INFO)).allMatch(id -> id.startsWith("info."));
        assertThat(guideIds(FormGuideScope.COST)).allMatch(id -> id.startsWith("cost."));
        assertThat(guideIds(FormGuideScope.INFO))
                .filteredOn(id -> id.startsWith("info.resource."))
                .noneMatch(id -> id.matches("info\\.resource\\.\\d+\\..+"));
        assertThat(guideIds(FormGuideScope.COST))
                .filteredOn(id -> id.startsWith("cost.resource."))
                .noneMatch(id -> id.matches("cost\\.resource\\.\\d+\\..+"));
    }

    private Set<String> guideIds(FormGuideScope scope) {
        return FormGuideCatalog.entries(scope).stream()
                .map(FormGuideCatalog.Entry::guideId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
