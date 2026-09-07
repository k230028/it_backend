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
                    new FormLabelReader(scanner),
                    new ResourceTableReader(scanner),
                    new FormApproverReader(scanner));

    private FormAdapterContext contextOf(byte[] bytes, Map<String, String> overrides) {
        Map<FormSheetKind, Sheet> sheets = reader.classify(reader.open(bytes, "픽스처.xls"));
        return contextOf(sheets, overrides);
    }

    private FormAdapterContext contextOf(
            Map<FormSheetKind, Sheet> sheets, Map<String, String> overrides) {
        return new FormAdapterContext(
                sheets,
                "2026",
                new RequestFormDto.FileEntry("런던지점(920)/붙임.xls", "런던지점(920)", null, null, null),
                "920",
                "런던지점",
                null,
                TestIoeIndex.snapshot(),
                overrides,
                "12345678");
    }

    @Test
    @DisplayName("해당사항 없음으로 표시한 경상사업 시트는 진단 없이 건너뛴다")
    void skipsNotApplicableRecurringProject() {
        Map<FormSheetKind, Sheet> sheets =
                reader.classify(reader.open(RequestFormFixtures.fullFormXls(), "픽스처.xls"));
        sheets.get(FormSheetKind.RECURRING).getRow(2).getCell(2).setCellValue("홍보실 해당사항 없음");

        FormAdapterOutput output = adapter.adapt(contextOf(sheets, Map.of()));

        assertThat(output.projects()).isEmpty();
        assertThat(output.diagnostics()).isEmpty();
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
        assertThat(project.getSvnDpmC()).isEqualTo("920");
        assertThat(project.getSttDtm()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(project.getEndDtm()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(project.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("경상사업 한 건의 여러 소요자원 행을 모두 품목으로 만든다")
    void buildsSingleRecurringProjectFromMultipleResourceRows() {
        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(RequestFormFixtures.singleRecurringMultiItemXls(), Map.of()));

        assertThat(output.projects()).singleElement();
        assertThat(output.projects().get(0).getAbusNm()).isEqualTo("단일 서버 교체");
        assertThat(output.projects().get(0).getItems())
                .hasSize(2)
                .extracting(ProjectDto.BitemmDto::getQty)
                .containsExactly(new BigDecimal("3"), new BigDecimal("2"));
        assertThat(output.projects().get(0).getItems())
                .extracting(ProjectDto.BitemmDto::getIoeC)
                .containsExactly("102", "102");
    }

    @Test
    @DisplayName("추진내용은 사업범위로, 미추진시 문제점은 문제점으로 옮긴다")
    void mapsOverviewFields() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), Map.of()))
                        .projects()
                        .get(0);

        assertThat(project.getAbusPulConeInf()).isEqualTo("PC, 모니터 구입");
        assertThat(project.getCpnSafCone()).isEqualTo("내용연수 경과");
        assertThat(project.getAbusPulNcsInf()).isNull();
        assertThat(project.getAbusPulDrcnInf()).isEqualTo("고장기기 교체");
        assertThat(project.getPlmDes()).isEqualTo("업무효율 저하");
    }

    @Test
    @DisplayName("상단 머리말의 확인자를 주관팀장, 작성자를 담당자로 담는다")
    void takesConfirmerAsTeamLeadAndAuthorAsStaff() {
        // 이 시트에는 `관련 조직` 블록이 없어 머리말이 유일한 근거다
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), Map.of()))
                        .projects()
                        .get(0);

        // 픽스처는 `신원석 부부장` — 직책은 떼고 이름만 담는다
        assertThat(project.getTlrUsid()).isEqualTo("신원석");
        // 영문 성명도 이름 스냅샷 컬럼 길이 안이면 그대로 담는다
        assertThat(project.getUsid()).isEqualTo("Luke Buckingham-Brown");
    }

    @Test
    @DisplayName("본부장이 포함된 확인자·작성자 이름을 전결권자 역할로 바꾸지 않는다")
    void preservesPersonNamesContainingHeadquartersTextInUserFields() {
        Map<FormSheetKind, Sheet> sheets =
                reader.classify(reader.open(RequestFormFixtures.fullFormXls(), "픽스처.xls"));
        Sheet recurring = sheets.get(FormSheetKind.RECURRING);
        recurring.getRow(1).getCell(7).setCellValue("홍길동 본부장");
        recurring.getRow(1).getCell(9).setCellValue("김영희 본부장");
        FormAdapterContext context =
                new FormAdapterContext(
                        sheets,
                        "2026",
                        new RequestFormDto.FileEntry(
                                "런던지점(920)/붙임.xls", "런던지점(920)", null, null, null),
                        "920",
                        "런던지점",
                        null,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678");

        ProjectDto.CreateRequest project = adapter.adapt(context).projects().get(0);

        assertThat(project.getTlrUsid()).isEqualTo("홍길동");
        assertThat(project.getUsid()).isEqualTo("김영희");
    }

    @Test
    @DisplayName("HW·SW 구분과 부서코드로 품목 비목을 정한다")
    void resolvesItemIoeByGroupAndDeptCode() {
        // 부서코드 `920`은 국외 점포다. 국내·국외는 통화가 아니라 부점 소속으로 갈린다
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), Map.of()))
                        .projects()
                        .get(0);

        assertThat(project.getItems().get(0).getIoeC()).isEqualTo("102");
        assertThat(project.getItems().get(1).getIoeC()).isEqualTo("105");
    }

    @Test
    @DisplayName("국내 부점이 외화로 적어도 국내 계열 비목을 쓴다")
    void usesDomesticIoeForDomesticBranchPayingInForeignCurrency() {
        // 픽스처 행은 GBP지만 부서코드가 국내(`420`)면 국내 비목이어야 한다
        Map<FormSheetKind, Sheet> sheets =
                reader.classify(reader.open(RequestFormFixtures.fullFormXls(), "픽스처.xls"));
        FormAdapterContext domestic =
                new FormAdapterContext(
                        sheets,
                        "2026",
                        new RequestFormDto.FileEntry(
                                "자금운용실(420)/붙임.xls", "자금운용실(420)", null, null, null),
                        "420",
                        "자금운용실",
                        null,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678");

        ProjectDto.CreateRequest project = adapter.adapt(domestic).projects().get(0);

        assertThat(project.getItems().get(0).getIoeC()).isEqualTo("101");
    }

    @Test
    @DisplayName("국내 경상사업의 통화 단위가 없으면 KRW 천원으로 본다")
    void defaultsBlankDomesticRecurringCurrencyToKrwThousands() {
        Map<FormSheetKind, Sheet> sheets =
                reader.classify(reader.open(RequestFormFixtures.fullFormXls(), "픽스처.xls"));
        Sheet recurring = sheets.get(FormSheetKind.RECURRING);
        recurring.getRow(8).getCell(6).setBlank();
        recurring.getRow(8).getCell(7).setCellValue(12.5d);
        FormAdapterContext domestic =
                new FormAdapterContext(
                        sheets,
                        "2026",
                        new RequestFormDto.FileEntry(
                                "자금운용실(420)/붙임.xls", "자금운용실(420)", null, null, null),
                        "420",
                        "자금운용실",
                        null,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678");

        ProjectDto.BitemmDto item = adapter.adapt(domestic).projects().get(0).getItems().get(0);

        assertThat(item.getCurC()).isEqualTo("KRW");
        assertThat(item.getAmt()).isEqualByComparingTo(new BigDecimal("12500"));
        assertThat(item.getFcAmt()).isNull();
    }

    @Test
    @DisplayName("경상사업 시트의 기본 천원 단위를 명시된 KRW 품목에도 동일하게 적용한다")
    void appliesRecurringSheetUnitToAllKrwItems() {
        Map<FormSheetKind, Sheet> sheets =
                reader.classify(
                        reader.open(RequestFormFixtures.singleRecurringMultiItemXls(), "픽스처.xls"));
        FormAdapterContext domestic =
                new FormAdapterContext(
                        sheets,
                        "2026",
                        new RequestFormDto.FileEntry(
                                "자금운용실(420)/붙임.xls", "자금운용실(420)", null, null, null),
                        "420",
                        "자금운용실",
                        null,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678");

        ProjectDto.CreateRequest project = adapter.adapt(domestic).projects().get(0);

        assertThat(project.getItems())
                .extracting(ProjectDto.BitemmDto::getAmt)
                .containsExactly(new BigDecimal("300000"), new BigDecimal("400000"));
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
    @DisplayName("비고를 관련근거내용으로 옮긴다")
    void mapsRemarksToRelatedBasisContent() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), Map.of()))
                        .projects()
                        .get(0);

        assertThat(project.getItems())
                .extracting(ProjectDto.BitemmDto::getCncdFdtnCone)
                .containsExactly("2026년 적용 환율 기준", "라이선스 갱신 근거");
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
    @DisplayName("보정 사업명의 개행을 공백으로 바꾼다")
    void replacesOverriddenProjectNameLineBreaksWithSpaces() {
        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(FormSheetKind.RECURRING, null, "abusNm"),
                        "런던지점\r\nIT기기\n구입");

        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.englishFormXls(), overrides))
                        .projects()
                        .get(0);

        assertThat(project.getAbusNm()).isEqualTo("런던지점 IT기기 구입");
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
