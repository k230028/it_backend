package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.request.service.WorkbookReader;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurringProjectFormAdapterTest {

    private final WorkbookReader reader = new WorkbookReader(10_485_760L, 20, 5000);
    private final SheetAnchorScanner scanner = new SheetAnchorScanner();
    private final RecurringProjectFormAdapter adapter =
            new RecurringProjectFormAdapter(
                    new FormLabelReader(scanner), new ResourceTableReader(scanner));

    private FormAdapterContext contextOf(byte[] bytes, Map<String, String> overrides) {
        Map<FormSheetKind, Sheet> sheets = reader.classify(reader.open(bytes, "픽스처.xls"));
        return new FormAdapterContext(
                sheets,
                "2026",
                new RequestFormDto.FileEntry("런던지점/붙임.xls", "런던지점", null, null, null),
                "0930",
                "런던지점",
                null,
                TestIoeIndex.snapshot(),
                overrides,
                "12345678");
    }

    @Test
    @DisplayName("경상사업 1건과 품목 2건을 만든다")
    void buildsRecurringProjectWithItems() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), Map.of()));

        assertThat(output.projects()).hasSize(1);
        ProjectDto.CreateRequest project = output.projects().get(0);
        assertThat(project.getAbusNm()).isEqualTo("2026년 IT기계장치 구입");
        assertThat(project.getOdnYn()).isEqualTo("Y");
        assertThat(project.getBseYy()).isEqualTo("2026");
        assertThat(project.getSvnDpmC()).isEqualTo("0930");
        assertThat(project.getSttDtm()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(project.getEndDtm()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(project.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("폼 4개 항목을 사업 설명 컬럼으로 옮긴다")
    void mapsOverviewFields() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), Map.of()))
                        .projects()
                        .get(0);

        assertThat(project.getAbusCone()).isEqualTo("PC, 모니터 구입");
        assertThat(project.getCpnSafCone()).isEqualTo("내용연수 경과");
        assertThat(project.getAbusRngCone()).isEqualTo("고장기기 교체");
        assertThat(project.getPlmDes()).isEqualTo("업무효율 저하");
    }

    @Test
    @DisplayName("HW·SW 구분과 통화로 품목 비목을 정한다")
    void resolvesItemIoeByGroupAndCurrency() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), Map.of()))
                        .projects()
                        .get(0);

        // GBP 행이므로 국외 계열
        assertThat(project.getItems().get(0).getIoeC()).isEqualTo("102");
        assertThat(project.getItems().get(1).getIoeC()).isEqualTo("105");
    }

    @Test
    @DisplayName("외화 품목은 FC_AMT에만 담고 수량·순번·최종여부를 채운다")
    void fillsItemAmountsAndSequence() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), Map.of()))
                        .projects()
                        .get(0);

        ProjectDto.BitemmDto first = project.getItems().get(0);
        assertThat(first.getSno()).isEqualTo(1);
        assertThat(first.getGclNm()).isEqualTo("데스크탑(고사양)");
        assertThat(first.getQty()).isEqualByComparingTo(new BigDecimal("12"));
        assertThat(first.getCurC()).isEqualTo("GBP");
        assertThat(first.getFcAmt()).isEqualByComparingTo(new BigDecimal("23346.84"));
        assertThat(first.getAmt()).isNull();
        assertThat(first.getXcr()).isNull();
        assertThat(first.getXcrBseDt()).isEqualTo("20260101");
        assertThat(first.getDfrCleC()).isEqualTo(ResourceTableReader.CYCLE_NOT_APPLICABLE);
        assertThat(first.getLstYn()).isEqualTo("Y");
        assertThat(project.getItems().get(1).getSno()).isEqualTo(2);
    }

    @Test
    @DisplayName("계 행은 품목으로 만들지 않는다")
    void skipsTotalRow() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), Map.of()))
                        .projects()
                        .get(0);

        assertThat(project.getItems())
                .extracting(ProjectDto.BitemmDto::getGclNm)
                .doesNotContain("계", "");
    }

    @Test
    @DisplayName("사업명이 비면 필수값 누락 진단을 내고 사업을 만들지 않는다")
    void blocksWhenProjectNameMissing() {
        // 런던 제출본 실측: 사업명 공란인데 소요자원은 채워져 있다
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.englishFormXls(), Map.of()));

        assertThat(output.projects()).isEmpty();
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.REQUIRED_MISSING);
    }

    @Test
    @DisplayName("보정으로 사업명을 채우면 사업을 만든다")
    void buildsProjectWhenNameSuppliedByOverride() {
        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(FormSheetKind.RECURRING, null, "abusNm"),
                        "2026년 런던지점 IT기기 구입");

        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.englishFormXls(), overrides));

        assertThat(output.projects()).hasSize(1);
        assertThat(output.projects().get(0).getAbusNm()).isEqualTo("2026년 런던지점 IT기기 구입");
    }

    @Test
    @DisplayName("시트 ②가 없으면 빈 결과를 돌려준다")
    void returnsEmptyWhenSheetAbsent() {
        Map<FormSheetKind, Sheet> sheets =
                reader.classify(reader.open(RequestFormFixtures.capitalOnlyXlsx(), "자료1.xlsx"));
        FormAdapterContext context =
                new FormAdapterContext(
                        sheets,
                        "2026",
                        new RequestFormDto.FileEntry("a/b.xlsx", "IT기획부", null, null, null),
                        "0100",
                        "IT기획부",
                        null,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678");

        assertThat(adapter.adapt(context).projects()).isEmpty();
    }
}
