package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.AmountUnitResolver;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.request.service.WorkbookReader;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import java.math.BigDecimal;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeneralExpenseFormAdapterTest {

    private final WorkbookReader reader = new WorkbookReader(10_485_760L, 20, 5000);
    private final GeneralExpenseFormAdapter adapter =
            new GeneralExpenseFormAdapter(new SheetAnchorScanner());

    private FormAdapterContext contextOf(byte[] workbookBytes, Long multiplier) {
        return contextOf(workbookBytes, multiplier, Map.of());
    }

    private FormAdapterContext contextOf(
            byte[] workbookBytes, Long multiplier, Map<String, String> overrides) {
        Map<FormSheetKind, Sheet> sheets = reader.classify(reader.open(workbookBytes, "픽스처.xls"));
        RequestFormDto.FileEntry entry =
                new RequestFormDto.FileEntry("자금운용실/요청서.xls", "자금운용실", null, multiplier, "571");
        return new FormAdapterContext(
                sheets,
                "2026",
                entry,
                "0210",
                "자금운용실",
                null,
                TestIoeIndex.snapshot(),
                overrides,
                "12345678");
    }

    @Test
    @DisplayName("비목명과 세부비목 쌍으로 비목코드를 확정해 전산업무비를 만든다")
    void buildsCostFromDetailPair() {
        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), 1L));

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
        assertThat(first.getCgprId()).isEqualTo("12345678");
        assertThat(first.getXcrBseDt()).isEqualTo("20260101");
        assertThat(first.getTmnYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("양식 표기가 공통코드와 달라도 대조표로 되돌려 확정한다")
    void resolvesIoeThroughLexicon() {
        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), 1L));

        // 양식은 `국외전산유지보수료`, 공통코드 014는 `국외유지보수료`
        assertThat(output.costs().get(1).getIoeC()).isEqualTo("014");
    }

    @Test
    @DisplayName("A·B열 병합으로 빈 행은 위 값을 이어받는다")
    void forwardFillsMergedCategoryColumns() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.englishFormXls(), 1L));

        assertThat(output.costs()).hasSize(2);
        assertThat(output.costs().get(1).getIoeC()).isEqualTo("013");
        assertThat(output.costs().get(1).getCttNm()).isEqualTo("AML Screening");
    }

    @Test
    @DisplayName("계속·신규 표시를 사업구분코드로 바꾼다")
    void mapsContinuedAndNew() {
        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), 1L));

        assertThat(output.costs().get(0).getAbusTc()).isEqualTo("20");
        assertThat(output.costs().get(1).getAbusTc()).isEqualTo("10");
    }

    @Test
    @DisplayName("월간 값이 있으면 지급주기를 월로, 없으면 년으로 정한다")
    void derivesPaymentCycleFromMonthlyColumn() {
        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), 1L));

        assertThat(output.costs().get(0).getDfrCleC()).isEqualTo("M");
        assertThat(output.costs().get(1).getDfrCleC()).isEqualTo("Y");
    }

    @Test
    @DisplayName("외화 행은 FC_AMT만 채우고 원화금액은 서버 재계산에 맡긴다")
    void leavesForeignKrwAmountToServer() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.englishFormXls(), 1L));

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
                adapter.adapt(
                        contextOf(
                                RequestFormFixtures.fullFormXls(),
                                AmountUnitResolver.UNIT_THOUSAND));

        assertThat(thousand.costs().get(0).getCostTotXpAmt())
                .isEqualByComparingTo(new BigDecimal("841854085000"));
    }

    @Test
    @DisplayName("배수를 지정하지 않으면 제안값을 내고 확인 경고를 남긴다")
    void suggestsMultiplierWhenAbsent() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), null));

        assertThat(output.suggestedGeneralExpenseMultiplier())
                .isEqualTo(AmountUnitResolver.UNIT_WON);
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.UNIT_UNCERTAIN);
    }

    @Test
    @DisplayName("정보보호 표기의 로마숫자 X를 N으로 접는다")
    void normalizesRomanNumeralX() {
        FormAdapterOutput output =
                adapter.adapt(contextOf(RequestFormFixtures.englishFormXls(), 1L));

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
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), 1L, overrides));

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
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls(), 1L, overrides));

        assertThat(output.costs().get(0).getIoeC()).isNull();
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.CODE_UNRESOLVED);
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
                        new RequestFormDto.FileEntry("a/b.xlsx", "IT기획부", null, 1L, null),
                        "0100",
                        "IT기획부",
                        null,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678");

        assertThat(adapter.adapt(context).costs()).isEmpty();
    }
}
