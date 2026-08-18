package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDecisionKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.request.service.WorkbookReader;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GeneralExpenseFormAdapterTest {

    private final WorkbookReader reader = new WorkbookReader(10_485_760L, 20, 5000);
    private final SheetAnchorScanner scanner = new SheetAnchorScanner();
    private final MigrationIoeCatalogReader catalogReader = currencyCatalogReader();
    private final GeneralExpenseFormAdapter adapter =
            new GeneralExpenseFormAdapter(scanner, new FormApproverReader(scanner), catalogReader);

    /** 통화 공통코드(`CUR_C`)만 답하는 카탈로그 리더. 실 DB의 통화 목록을 흉내 냅니다. */
    private static MigrationIoeCatalogReader currencyCatalogReader() {
        MigrationIoeCatalogReader mock = Mockito.mock(MigrationIoeCatalogReader.class);
        Mockito.when(mock.candidates(CommonCodeGroups.CURRENCY, false))
                .thenReturn(
                        List.of(
                                new MigrationDto.Candidate("KRW", "원화"),
                                new MigrationDto.Candidate("USD", "미국 달러"),
                                new MigrationDto.Candidate("GBP", "영국 파운드"),
                                new MigrationDto.Candidate("JPY", "일본 엔")));
        return mock;
    }

    private FormAdapterContext contextOf(byte[] workbookBytes, AmountUnit unit) {
        return contextOf(workbookBytes, unit, Map.of());
    }

    private FormAdapterContext contextOf(
            byte[] workbookBytes, AmountUnit unit, Map<String, String> overrides) {
        return contextOf(workbookBytes, unit, overrides, "0210");
    }

    /** 부서코드로 국내·국외를 가른다. `9`로 시작하면 국외 부점이다(런던 `920`). */
    private FormAdapterContext contextOf(
            byte[] workbookBytes, AmountUnit unit, Map<String, String> overrides, String deptCode) {
        Map<FormSheetKind, Sheet> sheets = reader.classify(reader.open(workbookBytes, "픽스처.xls"));
        RequestFormDto.FileEntry entry =
                new RequestFormDto.FileEntry("자금운용실/요청서.xls", "자금운용실", null, unit, "571");
        return new FormAdapterContext(
                sheets,
                "2026",
                entry,
                deptCode,
                "자금운용실",
                null,
                TestIoeIndex.snapshot(),
                overrides,
                "12345678");
    }

    /** 비목 공통코드가 하나도 없는 맥락. 후보를 실을 수 없는 미해석 경로를 확인합니다. */
    private FormAdapterContext emptyIoeContext() {
        CodeRepository emptyRepository = Mockito.mock(CodeRepository.class);
        Mockito.when(emptyRepository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N"))
                .thenReturn(List.of());
        Map<FormSheetKind, Sheet> sheets =
                reader.classify(
                        reader.open(RequestFormFixtures.generalExpenseIoeBranchesXls(), "픽스처.xls"));
        return new FormAdapterContext(
                sheets,
                "2026",
                new RequestFormDto.FileEntry("자금운용실/요청서.xls", "자금운용실", null, AmountUnit.WON, "571"),
                "0210",
                "자금운용실",
                null,
                new IoeHierarchyIndex(emptyRepository).snapshot(),
                Map.of(),
                "12345678");
    }

    @Test
    @DisplayName("비목명과 세부비목 쌍으로 비목코드를 확정해 전산업무비를 만든다")
    void buildsCostFromDetailPair() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), AmountUnit.WON));

        assertThat(output.costs()).hasSize(2);
        CostDto.CreateRequest first = output.costs().get(0);
        assertThat(first.getIoeC()).isEqualTo("010");
        assertThat(first.getCttNm()).isEqualTo("블룸버그 회선사용료");
        assertThat(first.getCttOppNm()).isEqualTo("Bloomberg");
        assertThat(first.getCurC()).isEqualTo("KRW");
        assertThat(first.getCostTotXpAmt()).isEqualByComparingTo(new BigDecimal("841854085"));
        assertThat(first.getFcAmt()).isNull();
        assertThat(first.getBseYy()).isEqualTo("2026");
        assertThat(first.getCostSvnDpmC()).isEqualTo("0210");
        assertThat(first.getBgUntAbusC()).isEqualTo("571");
        // 담당자는 상단 머리말의 작성자다. 업로드 사용자를 담당자로 박지 않는다.
        // 픽스처는 `최민호 대리` — 직책은 인사 정보라 담당자 컬럼에 담지 않는다
        assertThat(first.getCgprId()).isEqualTo("최민호");
        assertThat(first.getXcrBseDt()).isEqualTo("20260101");
        assertThat(first.getTmnYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("양식 표기가 공통코드와 달라도 대조표로 되돌려 확정한다")
    void resolvesIoeThroughLexicon() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), AmountUnit.WON));

        // 양식은 `국외전산유지보수료`, 공통코드 014는 `국외유지보수료`
        assertThat(output.costs().get(1).getIoeC()).isEqualTo("014");
    }

    @Test
    @DisplayName("A·B열 병합으로 빈 행은 위 값을 이어받는다")
    void forwardFillsMergedCategoryColumns() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.englishFormXls(), AmountUnit.WON));

        assertThat(output.costs()).hasSize(3);
        assertThat(output.costs().get(1).getIoeC()).isEqualTo("013");
        assertThat(output.costs().get(1).getCttNm()).isEqualTo("AML Screening");
    }

    @Test
    @DisplayName("대조표에 있는 중분류가 후보 하나로 좁혀지면 확인을 묻지 않는다")
    void confirmsLexiconGroupWithoutWarning() {
        // 런던 실측: 세부비목 칸에 중분류 `Machinery`를 그대로 적은 행. 대조표에 등록된 어휘이고
        // 국외 부점이라 `국외기계장치` 하나로 좁혀지므로 사용자가 고를 것이 없다
        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(
                                RequestFormFixtures.englishFormXls(),
                                AmountUnit.WON,
                                Map.of(),
                                "920"));

        CostDto.CreateRequest machinery = output.costs().get(2);
        assertThat(machinery.getCttNm()).isEqualTo("Tape backup software");
        assertThat(machinery.getIoeC()).isEqualTo("102");
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.CODE_DEFAULTED);
    }

    @Test
    @DisplayName("계속·신규 표시를 사업구분코드로 바꾼다")
    void mapsContinuedAndNew() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), AmountUnit.WON));

        assertThat(output.costs().get(0).getAbusTc()).isEqualTo("20");
        assertThat(output.costs().get(1).getAbusTc()).isEqualTo("10");
    }

    @Test
    @DisplayName("월간 값이 있으면 지급주기를 월로, 없으면 년으로 정한다")
    void derivesPaymentCycleFromMonthlyColumn() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), AmountUnit.WON));

        assertThat(output.costs().get(0).getDfrCleC()).isEqualTo("M");
        assertThat(output.costs().get(1).getDfrCleC()).isEqualTo("Y");
    }

    @Test
    @DisplayName("외화 행은 FC_AMT만 채우고 원화금액은 서버 재계산에 맡긴다")
    void leavesForeignKrwAmountToServer() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.englishFormXls(), AmountUnit.WON));

        CostDto.CreateRequest gbpRow = output.costs().get(0);
        assertThat(gbpRow.getCurC()).isEqualTo("GBP");
        assertThat(gbpRow.getFcAmt()).isEqualByComparingTo(new BigDecimal("5177.28"));
        assertThat(gbpRow.getCostTotXpAmt()).isNull();
        assertThat(gbpRow.getXcr()).isNull();
    }

    @Test
    @DisplayName("지정 배수를 원화 행에만 적용한다")
    void appliesMultiplierToKrwRowsOnly() {
        FormAdapterOutput thousand =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), AmountUnit.THOUSAND));

        assertThat(thousand.costs().get(0).getCostTotXpAmt())
                .isEqualByComparingTo(new BigDecimal("841854085000"));
    }

    @Test
    @DisplayName("배수를 지정하지 않으면 제안값을 내고 확인 경고를 남긴다")
    void suggestsMultiplierWhenAbsent() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), null));

        assertThat(output.suggestedGeneralExpenseUnit()).isEqualTo(AmountUnit.WON);
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.UNIT_UNCERTAIN);
    }

    @Test
    @DisplayName("정보보호 표기의 로마숫자 X를 N으로 접는다")
    void normalizesRomanNumeralX() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.englishFormXls(), AmountUnit.WON));

        assertThat(output.costs().get(0).getSectSysUtzYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("보정값이 있으면 자동 해석보다 우선한다")
    void overrideWinsOverResolution() {
        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(FormSheetKind.GENERAL_EXPENSE, 6, "ioeC"),
                        "011");

        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(RequestFormFixtures.fullFormXls(), AmountUnit.WON, overrides));

        assertThat(output.costs().get(0).getIoeC()).isEqualTo("011");
    }

    @Test
    @DisplayName("존재하지 않는 비목코드로 보정하면 미해석 진단을 낸다")
    void rejectsUnknownOverrideCode() {
        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(FormSheetKind.GENERAL_EXPENSE, 6, "ioeC"),
                        "999");

        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(RequestFormFixtures.fullFormXls(), AmountUnit.WON, overrides));

        assertThat(output.costs().get(0).getIoeC()).isNull();
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.CODE_UNRESOLVED);
    }

    @Test
    @DisplayName("이 어댑터는 시트 ③에만 반응한다")
    void triggersOnGeneralExpenseSheetOnly() {
        assertThat(adapter.trigger()).isEqualTo(FormSheetKind.GENERAL_EXPENSE);
    }

    @Test
    @DisplayName("중분류 기본값으로 정하면 대안 후보와 함께 확인을 요청한다")
    void warnsWhenGroupDefaultChosen() {
        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(
                                RequestFormFixtures.generalExpenseIoeBranchesXls(),
                                AmountUnit.WON));

        // `개발비`는 국내·국외 구분이 없어 기본값 103으로 정하고 감리/컨설팅 104를 대안으로 남긴다
        assertThat(output.costs().get(0).getIoeC()).isEqualTo("103");
        assertThat(output.diagnostics())
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.CODE_DEFAULTED)
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.excelRow()).isEqualTo(6);
                            assertThat(d.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .containsExactly("103", "104");
                        });
    }

    @Test
    @DisplayName("중분류로도 좁혀지지 않으면 후보를 실어 중의적 진단을 낸다")
    void reportsAmbiguousWithCandidates() {
        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(
                                RequestFormFixtures.generalExpenseIoeBranchesXls(),
                                AmountUnit.WON));

        assertThat(output.costs().get(1).getIoeC()).isNull();
        assertThat(output.diagnostics())
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.CODE_AMBIGUOUS)
                .singleElement()
                .satisfies(
                        d ->
                                // 국내 전산제비 3건(회선사용료·유지보수료·전산소모품비)이 후보로 남는다
                                assertThat(d.candidates())
                                        .extracting(MigrationDto.Candidate::code)
                                        .containsExactly("010", "011", "012"));
    }

    @Test
    @DisplayName("비목 카탈로그가 비어 있으면 고를 후보가 없어 미해석으로 낸다")
    void reportsUnresolvedWhenCatalogEmpty() {
        FormAdapterOutput output = adapter.adapt(emptyIoeContext());

        assertThat(output.diagnostics())
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.CODE_UNRESOLVED)
                .isNotEmpty()
                .allSatisfy(d -> assertThat(d.candidates()).isEmpty());
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.CODE_AMBIGUOUS);
    }

    @Test
    @DisplayName("JPY 행은 양식이 천엔 단위라 엔으로 펴서 담는다")
    void expandsJpyThousandUnit() {
        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(
                                RequestFormFixtures.generalExpenseIoeBranchesXls(),
                                AmountUnit.WON));

        CostDto.CreateRequest jpy = output.costs().get(2);
        assertThat(jpy.getCurC()).isEqualTo("JPY");
        assertThat(jpy.getFcAmt()).isEqualByComparingTo(new BigDecimal("1500000"));
        assertThat(jpy.getCostTotXpAmt()).isNull();
    }

    @Test
    @DisplayName("연간 금액이 빈 원화 행은 단위 추정 표본에서 뺀다")
    void excludesBlankAmountRowFromUnitSuggestion() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.generalExpenseIoeBranchesXls(), null));

        assertThat(output.costs().get(3).getCostTotXpAmt()).isNull();
        assertThat(output.suggestedGeneralExpenseUnit()).isNotNull();
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.UNIT_UNCERTAIN);
    }

    @Test
    @DisplayName("헤더만 있고 데이터 행이 없으면 빈 결과를 돌려준다")
    void returnsEmptyWhenNoDataRows() {
        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(
                                RequestFormFixtures.generalExpenseHeaderOnlyXls(), AmountUnit.WON));

        assertThat(output.costs()).isEmpty();
        assertThat(output.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("시트 ③이 없으면 빈 결과를 돌려준다")
    void returnsEmptyWhenSheetAbsent() {
        Map<FormSheetKind, Sheet> sheets =
                reader.classify(reader.open(RequestFormFixtures.capitalOnlyXlsx(), "자료1.xlsx"));
        FormAdapterContext context =
                new FormAdapterContext(
                        sheets,
                        "2026",
                        new RequestFormDto.FileEntry(
                                "a/b.xlsx", "IT기획부", null, AmountUnit.WON, null),
                        "0100",
                        "IT기획부",
                        null,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678");

        assertThat(adapter.adapt(context).costs()).isEmpty();
    }

    @Test
    @DisplayName("통화 칸이 비면 행 단위로 차단하고 통화 후보를 준다")
    void blocksRowWithBlankCurrency() {
        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(
                                RequestFormFixtures.generalExpenseBadCurrencyXls(),
                                AmountUnit.WON));

        assertThat(output.diagnostics())
                .filteredOn(d -> "curC".equals(d.field()))
                .anySatisfy(
                        d -> {
                            assertThat(d.excelRow()).isEqualTo(6);
                            assertThat(d.subject()).isEqualTo("통화 없는 계약");
                            assertThat(d.code())
                                    .isEqualTo(RequestFormDiagnosticCode.CODE_UNRESOLVED);
                            assertThat(d.severity()).isEqualTo(MigrationDto.Severity.BLOCKER);
                            assertThat(d.decision()).isEqualTo(RequestFormDecisionKind.SELECT);
                            assertThat(d.candidates())
                                    .extracting(MigrationDto.Candidate::code)
                                    .contains("KRW", "USD");
                        });
    }

    @Test
    @DisplayName("통화코드가 아닌 표기도 행 단위로 차단한다")
    void blocksRowWithUnknownCurrencyLabel() {
        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(
                                RequestFormFixtures.generalExpenseBadCurrencyXls(),
                                AmountUnit.WON));

        assertThat(output.diagnostics())
                .filteredOn(d -> "curC".equals(d.field()))
                .anySatisfy(
                        d -> {
                            assertThat(d.excelRow()).isEqualTo(7);
                            assertThat(d.message()).contains("원화");
                        });
    }

    @Test
    @DisplayName("통화코드는 대소문자를 가리지 않고 확정한다")
    void resolvesCurrencyCaseInsensitively() {
        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(
                                RequestFormFixtures.generalExpenseBadCurrencyXls(),
                                AmountUnit.WON));

        assertThat(output.costs())
                .filteredOn(cost -> "정상 외화 계약".equals(cost.getCttNm()))
                .singleElement()
                .satisfies(
                        cost -> {
                            assertThat(cost.getCurC()).isEqualTo("USD");
                            assertThat(cost.getFcAmt())
                                    .isEqualByComparingTo(new BigDecimal("12000"));
                            assertThat(cost.getCostTotXpAmt()).isNull();
                        });
    }

    @Test
    @DisplayName("통화 보정값이 있으면 시트값보다 우선하고 진단을 내지 않는다")
    void currencyOverrideWinsOverSheetValue() {
        Map<String, String> overrides =
                Map.of(
                        FormAdapterContext.overrideKey(FormSheetKind.GENERAL_EXPENSE, 6, "curC"),
                        "KRW");

        FormAdapterOutput output =
                adapter.adapt(
                        contextOf(
                                RequestFormFixtures.generalExpenseBadCurrencyXls(),
                                AmountUnit.WON,
                                overrides));

        assertThat(output.diagnostics())
                .filteredOn(d -> "curC".equals(d.field()))
                .extracting(RequestFormDto.FormDiagnostic::excelRow)
                .doesNotContain(6);
        assertThat(output.costs())
                .filteredOn(cost -> "통화 없는 계약".equals(cost.getCttNm()))
                .singleElement()
                .satisfies(
                        cost -> {
                            assertThat(cost.getCurC()).isEqualTo("KRW");
                            assertThat(cost.getCostTotXpAmt())
                                    .isEqualByComparingTo(new BigDecimal("5000000"));
                        });
    }

    @Test
    @DisplayName("원화 행이 하나도 없으면 금액 단위 확인을 묻지 않는다")
    void skipsUnitWarningWhenNoKrwRow() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.englishFormXls(), null));

        assertThat(output.costs()).isNotEmpty();
        assertThat(output.costs())
                .extracting(CostDto.CreateRequest::getCurC)
                .containsOnly("GBP");
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.UNIT_UNCERTAIN);
    }
}
