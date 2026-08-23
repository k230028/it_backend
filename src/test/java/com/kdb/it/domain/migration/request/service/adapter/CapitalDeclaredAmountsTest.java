package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.request.support.FormDiagnostics;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 1-1 선언 금액 산출의 미적재 6조건을 시트를 직접 만들어 확인합니다.
 *
 * <p>공용 픽스처는 정상 파일 하나를 재현한 것이라 "요약표가 없는 파일", "배수를 못 정하는 파일", "총액 표기를 해석 못하는 파일", "총액 칸의 단위 해석이 모호한
 * 파일", "총액이 요약표 합계보다 작은 파일", "산출값이 컬럼 용량을 넘는 파일" 같은 변형을 담지 못합니다. 조건 ①(요약표 없음)·②(배수 미확정)는 1-1만 담아 품목
 * 합계를 0으로 비워 확인하고, 조건 ③(총액 표기 해석 실패)·⑥(총액 단위 모호)·④(지급금액 음수)·⑤(컬럼 용량 초과)는 1-2를 함께 만들어 배수를 먼저 확정한 뒤에야
 * 그 분기에 닿습니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapitalDeclaredAmountsTest {

    @Mock private OrgIdentityResolver.Index orgIndex;
    @Mock private MigrationIoeCatalogReader catalogReader;

    private final SheetAnchorScanner scanner = new SheetAnchorScanner();
    private CapitalProjectFormAdapter adapter;

    // 필드 초기화식은 Mockito가 @Mock을 주입하기 전(테스트 인스턴스 생성 시점)에 실행되어 catalogReader가
    // 아직 null이다. CapitalProjectFormAdapterTest와 같이 @BeforeEach에서 조립해 주입 이후로 미룬다.
    @BeforeEach
    void setUp() {
        adapter =
                new CapitalProjectFormAdapter(
                        new CapitalOverviewReader(
                                scanner, new FormLabelReader(scanner), new FormCheckboxReader()),
                        new ResourceTableReader(scanner),
                        catalogReader);
    }

    @Test
    @DisplayName("1-2가 없으면 1-1의 비목별 합계로 품목과 선언 금액을 만든다")
    void createsItemsFromOverviewSummaryWhenResourceSheetIsAbsent() {
        FormAdapterOutput output = adapt(overviewWithSummaryItem());

        assertThat(output.projects())
                .singleElement()
                .satisfies(
                        project -> {
                            assertThat(project.getAbusNm()).isEqualTo("노후인프라 중장기 실행방안 수립");
                            assertThat(project.getItems())
                                    .singleElement()
                                    .satisfies(
                                            item -> {
                                                assertThat(item.getIoeC()).isEqualTo("008");
                                                assertThat(item.getGclNm())
                                                        .isEqualTo("노후인프라 중장기 실행방안 수립");
                                                assertThat(item.getQty()).isEqualByComparingTo("1");
                                                assertThat(item.getCurC()).isEqualTo("KRW");
                                                assertThat(item.getAmt())
                                                        .isEqualByComparingTo("1155000000");
                                                assertThat(item.getMplAmt())
                                                        .isEqualByComparingTo("0");
                                                assertThat(item.getCncdFdtnCone()).isNull();
                                                assertThat(item.getBseYm()).isNull();
                                                assertThat(item.getDfrCleC()).isNull();
                                                assertThat(item.getSectSysUtzYn()).isNull();
                                                assertThat(item.getItrInfrYn()).isNull();
                                            });
                        });
        assertThat(output.projectAmounts())
                .singleElement()
                .satisfies(
                        amounts -> {
                            assertThat(amounts.isPresent()).isTrue();
                            assertThat(amounts.totRqmAmt()).isEqualByComparingTo("1155000000");
                            assertThat(amounts.mplAmt()).isEqualByComparingTo("0");
                            assertThat(amounts.dfrAmt()).isEqualByComparingTo("0");
                        });
        assertThat(output.diagnostics())
                .noneMatch(diagnostic -> "declaredAmounts".equals(diagnostic.field()));
    }

    @Test
    @DisplayName("1-1 합성 품목의 전체 금액은 당해와 이후 금액을 모두 포함한다")
    void includesLaterAmountInSyntheticItemTotal() {
        FormAdapterOutput output = adapt(overviewWithSummaryItem(100d, 300d, "400 백만원"));

        assertThat(output.projects().get(0).getItems())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.getAmt()).isEqualByComparingTo("400000000");
                            assertThat(item.getMplAmt()).isEqualByComparingTo("300000000");
                        });
        assertThat(output.projectAmounts())
                .singleElement()
                .satisfies(
                        amounts -> {
                            assertThat(amounts.isPresent()).isTrue();
                            assertThat(amounts.totRqmAmt()).isEqualByComparingTo("400000000");
                            assertThat(amounts.mplAmt()).isEqualByComparingTo("300000000");
                            assertThat(amounts.dfrAmt()).isEqualByComparingTo("0");
                        });
    }

    @Test
    @DisplayName("[조건②] 품목이 없어 배수를 못 정하면 금액을 적재하지 않고 경고만 낸다")
    void skipsAmountsWhenUnitUnresolved() {
        FormAdapterOutput output = adapt(overviewOnly("2,000백만원", 1_265_624_700d, 0d));

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("기재 단위를 1-2 품목 합계로 확정하지 못했습니다");
    }

    @Test
    @DisplayName("신양식의 계 행도 선언 금액 요약표로 읽는다")
    void readsSummaryRowLabeledSimpleTotal() {
        FormAdapterOutput output = adapt(overviewOnly("2,000백만원", 1_265_624_700d, 0d, "계"));

        assertThat(amountWarning(output))
                .contains("기재 단위를 1-2 품목 합계로 확정하지 못했습니다")
                .doesNotContain("요약표를 찾지 못했습니다");
    }

    @Test
    @DisplayName("[조건①] 요약표가 없어도 금액을 적재하지 않고 경고만 낸다")
    void skipsAmountsWhenSummaryAbsent() {
        FormAdapterOutput output = adapt(overviewOnly("2,000백만원", null, null));

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("요약표를 찾지 못했습니다");
    }

    @Test
    @DisplayName("[조건③] 총 사업금액 표기를 해석하지 못하면 금액을 적재하지 않고 경고만 낸다")
    void skipsAmountsWhenWholePeriodUnparseable() {
        // 품목 합계 1,265,624,700원이 '26년도 합계와 원 단위로 대사되어 배수는 확정되지만,
        // `총 사업금액(전체기간)`은 "원" 접미사는 인식해도 그 앞의 "2억"을 숫자로 파싱하지 못한다
        FormAdapterOutput output =
                adaptWithResource("2억원", 1_265_624_700d, 0d, "기계장치(HW)", 1_265_624_700d);

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("금액으로 해석하지 못했습니다");
    }

    @Test
    @DisplayName("[조건④] 지급금액이 음수면 금액을 적재하지 않고 경고만 낸다")
    void skipsAmountsWhenDeferredNegative() {
        // 총 사업금액(전체기간) 1백만원이 '26년도 합계 2,000,000원(품목 합계와 원 단위로 대사)보다 작다
        FormAdapterOutput output =
                adaptWithResource("1백만원", 2_000_000d, 0d, "기계장치(HW)", 2_000_000d);

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("보다 작습니다");
    }

    @Test
    @DisplayName("[조건⑤] 산출한 금액이 컬럼 용량을 넘으면 적재하지 않고 경고만 낸다")
    void skipsAmountsWhenOverColumnCapacity() {
        // 품목 합계 1,500,000,000원과 '26년도 합계 1,500이 백만원 단위로 대사되어 배수는 MILLION으로 확정된다.
        // `총 사업금액(전체기간)`은 접미사 없이 1,000,000,000이라 적혀 있어 그 배수로 폴백하면 1e15가 되어
        // NUMBER(18,3)의 정수부 15자리 상한에 닿는다. 원 단위 그대로(candidateB=1,000,000,000)로 읽으면
        // 요약표 합계(1,500,000,000원)에도 못 미쳐 음수가 되므로 조건⑥(모호)에는 걸리지 않고 오직
        // 컬럼 용량 문제로만 확정된다
        FormAdapterOutput output =
                adaptWithResource("1000000000", 1_500d, 0d, "기계장치(HW)", 1_500_000_000d);

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("저장 가능한 범위를 넘습니다");
        // 다른 조건의 문구로 새지 않았는지 함께 본다
        assertThat(amountWarning(output)).doesNotContain("보다 작습니다");
        assertThat(amountWarning(output)).doesNotContain("확정할 수 없습니다");
        // 미적재는 경고일 뿐이라 사업은 그대로 만들어 파일을 막지 않는다
        assertThat(output.projects()).hasSize(1);
        assertThat(output.diagnostics()).noneMatch(diagnostic -> diagnostic.code().blocks());
    }

    @Test
    @DisplayName("[조건⑥] 배수를 적용한 해석과 원 단위 해석이 둘 다 성립하면 모호하다고 보아 적재하지 않는다")
    void skipsAmountsWhenFallbackInterpretationIsAmbiguous() {
        // '26년도 합계 1,000(raw)이 품목 합계 1,000,000원과 THOUSAND 배수로 대사된다.
        // `총 사업금액(전체기간)`은 접미사 없이 2,000,000이라 적혀 있어 폴백이 발동한다.
        // candidateA(배수 적용)=20억, candidateB(원 단위 그대로)=2,000,000 모두 지급금액이
        // 0 이상이라 어느 해석이 맞는지 확정할 수 없다
        FormAdapterOutput output = adaptWithResource("2000000", 1000d, 0d, "기계장치(HW)", 1_000_000d);

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("확정할 수 없습니다");
        // 다른 조건의 문구로 새지 않았는지 함께 본다
        assertThat(amountWarning(output)).doesNotContain("보다 작습니다");
        // 미적재는 경고일 뿐이라 사업은 그대로 만들어 파일을 막지 않는다
        assertThat(output.projects()).hasSize(1);
        assertThat(output.diagnostics()).noneMatch(diagnostic -> diagnostic.code().blocks());
    }

    @Test
    @DisplayName("[조건⑥ 대조] 원 단위 해석이 성립하지 않으면 모호하지 않아 그대로 적재한다")
    void loadsAmountsWhenFallbackInterpretationIsUnambiguous() {
        // '26년도 합계 1,200(raw)이 품목 합계 12억원과 MILLION 배수로 대사된다.
        // `총 사업금액(전체기간)`은 접미사 없이 2,000이라 적혀 있어 폴백이 발동하지만,
        // 원 단위 그대로 해석(candidateB=2,000)하면 지급금액이 음수가 되어 성립하지 않으므로
        // 배수를 적용한 해석(candidateA=20억) 하나로만 확정된다
        FormAdapterOutput output = adaptWithResource("2000", 1200d, 0d, "기계장치(HW)", 1_200_000_000d);

        ProjectAmounts amounts = output.projectAmounts().get(0);
        assertThat(amounts.isPresent()).isTrue();
        assertThat(amounts.totRqmAmt()).isEqualByComparingTo("2000000000");
        assertThat(amounts.mplAmt()).isEqualByComparingTo("0");
        assertThat(amounts.dfrAmt()).isEqualByComparingTo("800000000");
    }

    /**
     * MIG-14 — 조건⑥이 닫지 못한 잔여 구멍.
     *
     * <p>조건⑥은 두 해석이 <b>둘 다 성립할 때</b>만 막는다. 원 단위 해석의 지급금액이 음수면 모호로 판정되지 않아 배수가 곱해진 총액이 그대로 적재됐다. 정상
     * 다년도 사업의 비율은 실측 한~두 자릿수, 단위가 섞이면 5자릿수 이상이므로 업무 확정 상한 100배로 가른다.
     */
    @Test
    @DisplayName("[조건⑦] 폴백 총액이 요약표 합계의 100배를 넘으면 단위 혼재로 보아 적재하지 않는다")
    void skipsAmountsWhenFallbackRatioExceedsLimit() {
        // '26년도 합계 1,000(raw)이 품목 합계 10억원과 MILLION 배수로 대사된다.
        // `총 사업금액(전체기간)`은 접미사 없이 2,000,000이라 적혀 있어 폴백이 발동하고,
        // candidateA(배수 적용)=2조원 → 요약표 합계 10억원의 2,000배다. 원 단위 해석
        // (candidateB=2,000,000)은 지급금액이 음수라 조건⑥에는 걸리지 않는다.
        FormAdapterOutput output =
                adaptWithResource("2000000", 1000d, 0d, "기계장치(HW)", 1_000_000_000d);

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("100배를 넘습니다");
        // 조건⑥·④의 문구로 새지 않았는지 함께 본다
        assertThat(amountWarning(output)).doesNotContain("확정할 수 없습니다");
        assertThat(amountWarning(output)).doesNotContain("보다 작습니다");
        // 미적재는 경고일 뿐이라 사업은 그대로 만들어 파일을 막지 않는다
        assertThat(output.projects()).hasSize(1);
        assertThat(output.diagnostics()).noneMatch(diagnostic -> diagnostic.code().blocks());
    }

    @Test
    @DisplayName("[조건⑦ 대조] 100배 이내면 정상 다년도 사업으로 보아 그대로 적재한다")
    void loadsAmountsWhenFallbackRatioIsWithinLimit() {
        // 같은 폴백 경로에서 candidateA=20억원, 요약표 합계=10억원이라 비율이 2배다.
        // 정상 다년도 사업의 실측 비율(한~두 자릿수)이 막히지 않는지 확인한다.
        FormAdapterOutput output = adaptWithResource("2000", 1000d, 0d, "기계장치(HW)", 1_000_000_000d);

        ProjectAmounts amounts = output.projectAmounts().get(0);
        assertThat(amounts.isPresent()).isTrue();
        assertThat(amounts.totRqmAmt()).isEqualByComparingTo("2000000000");
    }

    @Test
    @DisplayName("[조건⑥ 예외] 배수가 원 단위(WON)면 두 해석이 같은 값이라 모호 판정을 하지 않는다")
    void doesNotFlagAmbiguityWhenResolvedUnitIsWon() {
        // '26년도 합계 2,000,000(raw)이 품목 합계 2,000,000원과 WON 배수(1배)로 대사된다.
        // 배수가 1이면 candidateA와 candidateB가 항상 같은 값이라 모호할 수 없다.
        // 이 예외가 없으면 폴백 경로의 정상 파일(WON 단위)이 전부 미적재로 막힌다
        FormAdapterOutput output =
                adaptWithResource("5000000", 2_000_000d, 0d, "기계장치(HW)", 2_000_000d);

        ProjectAmounts amounts = output.projectAmounts().get(0);
        assertThat(amounts.isPresent()).isTrue();
        assertThat(amounts.totRqmAmt()).isEqualByComparingTo("5000000");
        assertThat(amounts.mplAmt()).isEqualByComparingTo("0");
        assertThat(amounts.dfrAmt()).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("[조건②] 외화 품목 때문에 대사하지 못하면 단위 문구가 아니라 외화 문구를 낸다")
    void reportsForeignCurrencyCauseWhenItemsAreNotKrw() {
        // 외화 행은 AMT가 비고 FC_AMT만 채워져 1-2 원화 합계에서 빠진다. 합계가 0이 되어 어느 배수로도
        // 대사되지 않는데, 실제 원인은 기재 단위가 아니라 외화 품목이다
        FormAdapterOutput output =
                adaptWithResource("2,000백만원", 1_265_624_700d, 0d, "기계장치(HW)", 1_000_000d, "USD");

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("외화 품목이 있어");
        assertThat(amountWarning(output)).doesNotContain("기재 단위를 1-2 품목 합계로 확정하지 못했습니다");
    }

    @Test
    @DisplayName("1-2 품목 합계로 대사되지 않아도 `필요예산 편성요청` 칸으로 단위를 확정해 적재한다")
    void resolvesUnitFromYearRequestWhenItemTotalDisagrees() {
        ProjectAmounts amounts = fundingDeskSample("1,211백만원").projectAmounts().get(0);

        assertThat(amounts.isPresent()).isTrue();
        assertThat(amounts.totRqmAmt()).isEqualByComparingTo("2000000000");
        assertThat(amounts.mplAmt()).isEqualByComparingTo("202746300");
        assertThat(amounts.dfrAmt()).isEqualByComparingTo("585835340");
    }

    @Test
    @DisplayName("폴백으로 단위를 확정해도 1-1과 1-2가 어긋난 사실은 경고로 남긴다")
    void warnsAboutTableGapWhenFallbackResolvesUnit() {
        FormAdapterOutput output = fundingDeskSample("1,211백만원");

        assertThat(FormDiagnostics.messageOf(output.diagnostics(), "declaredYearTotal"))
                .contains("필요예산 편성요청")
                .doesNotContain("어느 단위로도 맞지 않습니다");
        assertThat(output.diagnostics()).noneMatch(diagnostic -> diagnostic.code().blocks());
    }

    @Test
    @DisplayName("`필요예산 편성요청` 칸에 단위가 없으면 폴백 기준점으로 쓰지 않는다")
    void ignoresYearRequestWithoutUnitSuffix() {
        // 접미사가 없으면 그 칸도 요약표와 같은 단위 미확정 값이라 기준점이 되지 못한다
        FormAdapterOutput output = fundingDeskSample("1,211");

        assertThat(output.projectAmounts().get(0).isPresent()).isFalse();
        assertThat(amountWarning(output)).contains("기재 단위를 1-2 품목 합계로 확정하지 못했습니다");
    }

    /**
     * 실측 제출본(자금운용실)의 금액 구성을 재현합니다.
     *
     * <p>1-2 일반관리비가 `'27년 유지보수료`까지 담은 연간 금액이라 품목 합계가 `'26년도 합계`보다 6,309,600원 큽니다. 상대 오차 0.518%로 허용치
     * 0.5%를 넘어 품목 합계 대사는 실패하지만, 1-1이 스스로 적은 `'26년도 필요예산 편성요청`과는 원 단위로 맞습니다.
     *
     * @param yearRequest `'26년도 필요예산 편성요청` 칸에 적을 문자열
     */
    private FormAdapterOutput fundingDeskSample(String yearRequest) {
        return adaptWithResource(
                "2,000백만원", yearRequest, 1_211_418_360d, 202_746_300d, "기계장치(HW)", 1_217_727_960d);
    }

    @Test
    @DisplayName("사업은 그대로 만들어 파일을 막지 않는다")
    void stillProducesProject() {
        FormAdapterOutput output = adapt(overviewOnly("2,000백만원", 1_265_624_700d, 0d));

        assertThat(output.projects()).hasSize(1);
        assertThat(output.diagnostics()).noneMatch(diagnostic -> diagnostic.code().blocks());
    }

    private FormAdapterOutput adapt(Sheet overview) {
        Map<FormSheetKind, Sheet> sheets = new EnumMap<>(FormSheetKind.class);
        sheets.put(FormSheetKind.CAPITAL_OVERVIEW, overview);
        return adapt(sheets);
    }

    /** 원화 품목 1건을 담은 1-2를 함께 만들어 배수가 확정된 상태로 적재를 시도합니다. */
    private FormAdapterOutput adaptWithResource(
            String wholePeriod,
            Double yearTotal,
            Double laterTotal,
            String itemGroup,
            double itemAmount) {
        return adaptWithResource(wholePeriod, yearTotal, laterTotal, itemGroup, itemAmount, "KRW");
    }

    /** `'26년도 필요예산 편성요청` 칸까지 채운 1-1과 1-2를 함께 만듭니다. */
    private FormAdapterOutput adaptWithResource(
            String wholePeriod,
            String yearRequest,
            Double yearTotal,
            Double laterTotal,
            String itemGroup,
            double itemAmount) {
        return adaptWithResource(
                wholePeriod, yearRequest, yearTotal, laterTotal, itemGroup, itemAmount, "KRW");
    }

    /** 1-1과 1-2를 함께 담은 워크북을 만듭니다. 통화를 지정해 외화 품목(원화 합계에서 빠지는 행)도 만들 수 있습니다. */
    private FormAdapterOutput adaptWithResource(
            String wholePeriod,
            Double yearTotal,
            Double laterTotal,
            String itemGroup,
            double itemAmount,
            String currency) {
        return adaptWithResource(
                wholePeriod, null, yearTotal, laterTotal, itemGroup, itemAmount, currency);
    }

    private FormAdapterOutput adaptWithResource(
            String wholePeriod,
            String yearRequest,
            Double yearTotal,
            Double laterTotal,
            String itemGroup,
            double itemAmount,
            String currency) {
        Workbook wb =
                workbookOf(
                        w -> {
                            Sheet overview = w.createSheet(OVERVIEW_SHEET_NAME);
                            writeOverview(
                                    overview,
                                    wholePeriod,
                                    yearTotal,
                                    laterTotal,
                                    "총 계",
                                    yearRequest);
                            Sheet resource = w.createSheet(RESOURCE_SHEET_NAME);
                            writeResourceItem(resource, itemGroup, itemAmount, currency);
                        });
        Map<FormSheetKind, Sheet> sheets = new EnumMap<>(FormSheetKind.class);
        sheets.put(FormSheetKind.CAPITAL_OVERVIEW, wb.getSheet(OVERVIEW_SHEET_NAME));
        sheets.put(FormSheetKind.CAPITAL_RESOURCE, wb.getSheet(RESOURCE_SHEET_NAME));
        return adapt(sheets);
    }

    private FormAdapterOutput adapt(Map<FormSheetKind, Sheet> sheets) {
        return adapter.adapt(
                new FormAdapterContext(
                        sheets,
                        "2026",
                        new RequestFormDto.FileEntry("부서/파일.xls", "폴더부서", null, null, null),
                        "0999",
                        "폴더부서",
                        orgIndex,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678"));
    }

    /**
     * 산출 실패 경고(`field=declaredAmounts`)의 문구를 꺼냅니다.
     *
     * <p>같은 조건에서 대사 경고(`field=declaredYearTotal`)도 같은 코드로 나올 수 있으므로 코드가 아니라 필드로 좁힙니다.
     */
    private static String amountWarning(FormAdapterOutput output) {
        return FormDiagnostics.messageOf(output.diagnostics(), "declaredAmounts");
    }

    private static final String OVERVIEW_SHEET_NAME = "① (정보화사업) 1-1. 정보화사업 개요";
    private static final String RESOURCE_SHEET_NAME = "① (정보화사업) 1-2. 소요자원 상세내용";

    /** 1-1만 담은 시트를 만듭니다. 요약표 값이 null이면 그 칸을 비웁니다. */
    private static Sheet overviewOnly(String wholePeriod, Double yearTotal, Double laterTotal) {
        return overviewOnly(wholePeriod, yearTotal, laterTotal, "총 계");
    }

    private static Sheet overviewOnly(
            String wholePeriod, Double yearTotal, Double laterTotal, String totalLabel) {
        Workbook wb =
                workbookOf(
                        w -> {
                            Sheet sheet = w.createSheet(OVERVIEW_SHEET_NAME);
                            writeOverview(sheet, wholePeriod, yearTotal, laterTotal, totalLabel);
                        });
        return wb.getSheetAt(0);
    }

    /** 실제 단일 시트 샘플처럼 1-1 요약표에 비목별 금액만 있는 워크북을 만듭니다. */
    private static Sheet overviewWithSummaryItem() {
        return overviewWithSummaryItem(1155d, 0d, "1,155 백만원");
    }

    private static Sheet overviewWithSummaryItem(
            double yearAmount, double laterAmount, String wholePeriod) {
        Workbook wb =
                workbookOf(
                        w -> {
                            Sheet sheet = w.createSheet(OVERVIEW_SHEET_NAME);
                            Row nameRow = sheet.createRow(0);
                            cell(nameRow, 2).setCellValue("사업명");
                            cell(nameRow, 3).setCellValue("노후인프라 중장기 실행방안 수립");
                            Row amountRow = sheet.createRow(1);
                            cell(amountRow, 7).setCellValue("총 사업금액(전체기간)");
                            cell(amountRow, 9).setCellValue(wholePeriod);
                            Row header = sheet.createRow(2);
                            cell(header, 0).setCellValue("분기별 소요(안) (백만원, 부가세포함)");
                            cell(header, 1).setCellValue("비목");
                            cell(header, 6).setCellValue("'26년도 합계");
                            cell(header, 7).setCellValue("'26년도 이후");
                            cell(header, 8).setCellValue("전체 합계");
                            Row item = sheet.createRow(3);
                            cell(item, 1).setCellValue("외주용역비");
                            cell(item, 5).setCellValue(yearAmount);
                            cell(item, 6).setCellValue(yearAmount);
                            cell(item, 7).setCellValue(laterAmount);
                            cell(item, 8).setCellValue(yearAmount + laterAmount);
                            Row total = sheet.createRow(4);
                            cell(total, 0).setCellValue("총 계");
                            cell(total, 6).setCellValue(yearAmount);
                            cell(total, 7).setCellValue(laterAmount);
                            cell(total, 8).setCellValue(yearAmount + laterAmount);
                        });
        return wb.getSheetAt(0);
    }

    /** 1-1 시트에 `총 사업금액(전체기간)`과 요약표(`'26년도 합계`·`'26년도 이후`)를 채웁니다. */
    private static void writeOverview(
            Sheet sheet, String wholePeriod, Double yearTotal, Double laterTotal) {
        writeOverview(sheet, wholePeriod, yearTotal, laterTotal, "총 계");
    }

    private static void writeOverview(
            Sheet sheet,
            String wholePeriod,
            Double yearTotal,
            Double laterTotal,
            String totalLabel) {
        writeOverview(sheet, wholePeriod, yearTotal, laterTotal, totalLabel, null);
    }

    private static void writeOverview(
            Sheet sheet,
            String wholePeriod,
            Double yearTotal,
            Double laterTotal,
            String totalLabel,
            String yearRequest) {
        Row nameRow = sheet.createRow(0);
        cell(nameRow, 2).setCellValue("사업명");
        cell(nameRow, 3).setCellValue("사업");
        Row amountRow = sheet.createRow(1);
        cell(amountRow, 7).setCellValue("총 사업금액(전체기간)");
        cell(amountRow, 9).setCellValue(wholePeriod);
        if (yearRequest != null) {
            // 실 양식과 같이 총액 칸 바로 아래에 둔다
            Row requestRow = sheet.createRow(3);
            cell(requestRow, 7).setCellValue("‘26년도 필요예산 편성요청");
            cell(requestRow, 9).setCellValue(yearRequest);
        }
        if (yearTotal != null || laterTotal != null) {
            Row header = sheet.createRow(2);
            cell(header, 6).setCellValue("'26년도 합계");
            cell(header, 7).setCellValue("'26년도 이후");
            Row totalRow = sheet.createRow(4);
            cell(totalRow, 0).setCellValue(totalLabel);
            if (yearTotal != null) cell(totalRow, 6).setCellValue(yearTotal);
            if (laterTotal != null) cell(totalRow, 7).setCellValue(laterTotal);
        }
    }

    /** 1-2 시트에 품목 1건을 채웁니다. `FormAdapterResolutionPathTest.writeResourceSheet`와 같은 레이아웃입니다. */
    private static void writeResourceItem(
            Sheet sheet, String group, double amount, String currency) {
        Row header = sheet.createRow(9);
        cell(header, 1).setCellValue("구분");
        cell(header, 3).setCellValue("항목");
        cell(header, 4).setCellValue("수량");
        cell(header, 6).setCellValue("통화");
        cell(header, 7).setCellValue("소요예산 (부가세포함)");
        Row item = sheet.createRow(11);
        cell(item, 2).setCellValue(group);
        cell(item, 3).setCellValue("품목");
        cell(item, 4).setCellValue(1);
        cell(item, 6).setCellValue(currency);
        cell(item, 7).setCellValue(amount);
    }

    /** 워크북을 채운 뒤 바이트로 직렬화·역직렬화해, 반환된 시트가 스캐너가 실제로 읽는 셀 캐시 상태를 갖게 합니다. */
    private static Workbook workbookOf(Consumer<Workbook> filler) {
        try (Workbook wb = new HSSFWorkbook()) {
            filler.accept(wb);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return new HSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Cell cell(Row row, int colIndex) {
        Cell existing = row.getCell(colIndex);
        return existing == null ? row.createCell(colIndex) : existing;
    }
}
