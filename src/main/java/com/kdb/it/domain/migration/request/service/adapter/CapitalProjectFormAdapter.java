package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.AmountUnitResolver;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * 시트 ① 1-1·1-2를 합쳐 정보화사업 생성 요청을 만듭니다.
 *
 * <p>두 시트가 같은 사업의 머리와 몸통이라 어댑터 하나가 둘을 함께 읽습니다. 1-2는 자본예산 블록과 일반관리비 블록이 위아래로 놓여 있어 소요자원 리더를 두 번
 * 호출하고, 두 블록의 품목을 <b>같은 사업의 {@code BITEMM}</b>으로 담습니다 — 양식 주석("정보화사업에 포함된 일반관리비는 1-1·1-2 시트에 작성")과
 * 실측 {@code BITEMM}에 일반관리비 비목이 들어 있는 사실이 이를 뒷받침합니다.
 *
 * <p>품목 금액({@code BITEMM})의 원본은 여전히 1-2입니다. 반면 <b>사업 단위 금액 3종({@code TOT_RQM_AMT}·{@code
 * MPL_AMT}·{@code DFR_AMT})은 1-1 선언값에서 산출</b>합니다 — 전체기간 총액과 예산연도 이후 계획분은 예산연도 품목 합계로는 얻을 수 없기
 * 때문입니다.
 *
 * <p>1-1 요약표는 기재 단위가 파일마다 달라 그대로 쓰지 못합니다. 항상 원 단위인 1-2 품목 합계와 대사해 배수를 역추정한 뒤 그 배수를 곱해 원 단위로 폅니다. 환산
 * 근거가 없거나 산출값을 신뢰할 수 없으면 <b>적재하지 않고 경고만</b> 내며 파일 반입 자체는 막지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class CapitalProjectFormAdapter implements FormSheetAdapter {

    /**
     * 사업 단위 금액 컬럼에 담을 수 있는 정수부 상한(배타).
     *
     * <p>{@code Bprojm}의 `TOT_RQM_AMT`·`MPL_AMT`·`DFR_AMT`는 {@code @Column(precision = 18, scale =
     * 3)} = Oracle {@code NUMBER(18,3)}이라 정수부가 18 − 3 = 15자리입니다. 그래서 절댓값이 {@code 10^15} 이상이면 담기지
     * 못합니다.
     */
    private static final BigDecimal AMOUNT_COLUMN_LIMIT = BigDecimal.TEN.pow(15);

    private final CapitalOverviewReader overviewReader;
    private final ResourceTableReader resourceTableReader;
    private final MigrationIoeCatalogReader catalogReader;

    @Override
    public FormSheetKind trigger() {
        return FormSheetKind.CAPITAL_OVERVIEW;
    }

    @Override
    public FormAdapterOutput adapt(FormAdapterContext context) {
        Sheet overview = context.sheets().get(FormSheetKind.CAPITAL_OVERVIEW);
        if (overview == null) return FormAdapterOutput.empty();

        CapitalOverviewReader.Result read = overviewReader.read(overview, context, catalogs());
        ProjectDto.CreateRequest project = read.project();
        if (project.getAbusNm() == null || project.getAbusNm().isBlank()) {
            // 1-1 시트가 빈 껍데기인 파일(경상사업·일반관리비만 낸 부점)이라 진단 없이 건너뛴다
            return FormAdapterOutput.empty();
        }

        List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>(read.diagnostics());
        List<ProjectDto.BitemmDto> items = readItems(context, diagnostics);
        project.setItems(items);

        BigDecimal itemTotal = sumItemAmounts(items);
        Optional<AmountUnit> unit =
                AmountUnitResolver.inferUnit(read.amounts().yearTotalRaw(), itemTotal);
        reconcileTotals(
                read.amounts().yearTotalRaw(), itemTotal, unit, project.getAbusNm(), diagnostics);
        ProjectAmounts amounts =
                declaredAmounts(
                        read.amounts(),
                        unit,
                        hasForeignCurrencyItem(items),
                        project.getAbusNm(),
                        diagnostics);

        return new FormAdapterOutput(
                List.of(project), List.of(), List.copyOf(diagnostics), null, List.of(amounts));
    }

    /**
     * 1-1 해석에 쓰는 공통코드를 모읍니다.
     *
     * <p>선택 항목의 후보는 미기재 안내에 붙어 미리보기에서 바로 고를 수 있게 합니다. 저장 형태가 코드값명인 항목과 코드인 항목이 갈리므로 후보값도 그에 맞춰
     * 만듭니다({@code storeName}).
     */
    private FormCatalogs catalogs() {
        Map<String, List<MigrationDto.Candidate>> options = new LinkedHashMap<>();
        options.put("bzDttNm", catalogReader.candidates(CommonCodeGroups.BZ_DTT, true));
        options.put("bzTpC", catalogReader.candidates(CommonCodeGroups.PRJ_TYPE, true));
        options.put("sklTpTc", catalogReader.candidates(CommonCodeGroups.TECH_TYPE, true));
        options.put("cstTpTc", catalogReader.candidates(CommonCodeGroups.MAIN_USER, true));
        options.put("rprStsTc", catalogReader.candidates(CommonCodeGroups.REPORT_STS, false));
        options.put("exePttYn", catalogReader.candidates(CommonCodeGroups.EXE_POSSIBLE, false));
        options.put(
                "dplYn",
                List.of(
                        new MigrationDto.Candidate("N", "비중복(N)"),
                        new MigrationDto.Candidate("Y", "중복(Y)")));
        return new FormCatalogs(
                catalogReader.exePttCodeByName(),
                catalogReader.edrtCapitalCodeByName(),
                catalogReader.reportStatusCodeByName(),
                Map.copyOf(options));
    }

    /** 1-2의 자본예산·일반관리비 두 블록을 순서대로 읽어 품목 목록을 만듭니다. */
    private List<ProjectDto.BitemmDto> readItems(
            FormAdapterContext context, List<RequestFormDto.FormDiagnostic> diagnostics) {
        Sheet resource = context.sheets().get(FormSheetKind.CAPITAL_RESOURCE);
        List<ProjectDto.BitemmDto> items = new ArrayList<>();
        if (resource == null) return items;

        int sno = 1;
        Optional<ResourceTableReader.Result> capital =
                resourceTableReader.readCapitalResource(resource, 0, false);
        if (capital.isPresent()) {
            for (ResourceRow row : capital.get().rows()) {
                items.add(toItem(row, context, sno++, diagnostics));
            }
        }
        int nextFrom = capital.map(result -> result.headerRow() + 1).orElse(0);
        Optional<ResourceTableReader.Result> general =
                resourceTableReader.readCapitalResource(resource, nextFrom, true);
        if (general.isPresent()) {
            for (ResourceRow row : general.get().rows()) {
                items.add(toItem(row, context, sno++, diagnostics));
            }
        }
        return items;
    }

    private ProjectDto.BitemmDto toItem(
            ResourceRow row,
            FormAdapterContext context,
            int sno,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        return ResourceTableReader.toItem(
                row, resolveIoe(row, context, diagnostics), sno, context.bseYy());
    }

    private String resolveIoe(
            ResourceRow row,
            FormAdapterContext context,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> override =
                context.override(FormSheetKind.CAPITAL_RESOURCE, row.excelRow(), "ioeC");
        if (override.isPresent() && context.ioeIndex().exists(override.get()))
            return override.get();

        boolean domestic = !context.foreignBranch();
        IoeHierarchyIndex.Resolution resolution =
                context.ioeIndex().resolveByGroup(row.group(), domestic);

        if (resolution.code() == null) {
            diagnostics.add(
                    itemDiagnostic(
                            row,
                            context,
                            resolution.isAmbiguous()
                                    ? RequestFormDiagnosticCode.CODE_AMBIGUOUS
                                    : RequestFormDiagnosticCode.CODE_UNRESOLVED,
                            "구분 `%s`의 비목을 정하지 못했습니다.".formatted(row.group()),
                            resolution));
            return null;
        }
        if (!resolution.candidates().isEmpty()) {
            // 기본값으로 정했지만 대안이 있는 경우다. 반영은 막지 않고 확인만 요청한다 —
            // 여기서 막으면 개발비·기타무형자산 품목이 있는 파일이 전부 차단된다.
            diagnostics.add(
                    itemDiagnostic(
                            row,
                            context,
                            RequestFormDiagnosticCode.CODE_DEFAULTED,
                            "구분 `%s`의 비목을 `%s`로 기본 설정했습니다. 다른 비목이면 골라 주세요."
                                    .formatted(row.group(), resolution.label()),
                            resolution));
        }
        return resolution.code();
    }

    private RequestFormDto.FormDiagnostic itemDiagnostic(
            ResourceRow row,
            FormAdapterContext context,
            RequestFormDiagnosticCode code,
            String message,
            IoeHierarchyIndex.Resolution resolution) {
        return RequestFormDto.FormDiagnostic.about(
                FormSheetKind.CAPITAL_RESOURCE,
                row.excelRow(),
                "ioeC",
                row.itemName(),
                code,
                message,
                IoeCandidates.orAll(resolution, context));
    }

    /**
     * 품목의 원화 소요예산({@code AMT}) 합계를 더합니다.
     *
     * <p>1-2의 원화 행은 `수량 × 단가`를 그대로 적어 원 단위입니다. 다만 <b>외화 행은 {@code AMT}가 비어 있고 {@code FC_AMT}만
     * 채워지므로(원화 환산은 서버가 환율로 나중에 합니다) 이 합계에서 빠집니다.</b> 그래서 외화 품목이 섞인 파일은 합계가 실제 원화 총액보다 작고, 1-1 요약표와
     * 대사해도 어느 배수에도 맞지 않아 단위 판정이 실패합니다 — 그 경우의 안내는 {@link #declaredAmounts}가 외화 품목 유무로 갈라서 냅니다.
     */
    private static BigDecimal sumItemAmounts(List<ProjectDto.BitemmDto> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (ProjectDto.BitemmDto item : items) {
            if (item.getAmt() != null) total = total.add(item.getAmt());
        }
        return total;
    }

    /**
     * 원화 합계에서 빠지는 외화 품목이 하나라도 있는지 봅니다.
     *
     * <p>{@code ResourceTableReader.toItem}이 외화 행의 {@code AMT}를 비우므로 `null`이 곧 외화 행 신호입니다.
     */
    private static boolean hasForeignCurrencyItem(List<ProjectDto.BitemmDto> items) {
        return items.stream().anyMatch(item -> item.getAmt() == null);
    }

    /**
     * 1-1 요약표와 1-2 품목 합계를 대사합니다.
     *
     * <p>어느 배수로도 맞지 않으면 단위 문제가 아니라 기재 오류이므로 경고를 냅니다. 요약표를 읽지 못했거나 품목이 없으면 대사할 수 없어 조용히 넘어갑니다 — 그
     * 경우의 안내는 {@link #declaredAmounts}가 냅니다.
     */
    private void reconcileTotals(
            BigDecimal declaredYearTotal,
            BigDecimal itemTotal,
            Optional<AmountUnit> unit,
            String projectName,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (declaredYearTotal == null || itemTotal.signum() == 0) return;
        if (unit.isPresent()) return;

        diagnostics.add(
                RequestFormDto.FormDiagnostic.about(
                        FormSheetKind.CAPITAL_OVERVIEW,
                        null,
                        "declaredYearTotal",
                        projectName,
                        RequestFormDiagnosticCode.AMOUNT_MISMATCH,
                        "1-1 요약표의 합계(%s)와 1-2 품목 합계(%s)가 어느 단위로도 맞지 않습니다."
                                .formatted(
                                        declaredYearTotal.toPlainString(),
                                        itemTotal.toPlainString()),
                        List.of()));
    }

    /**
     * 1-1 선언 금액에서 사업 단위 금액 3종을 산출합니다.
     *
     * <p>산식은 {@code 총소요금액 = 총 사업금액(전체기간)}, {@code 예정금액 = '26년도 이후}, {@code 지급금액 = 총 사업금액 − '26년도 이후
     * − '26년도 합계}입니다. 요약표는 단위가 파일마다 다르므로 1-2 품목 합계로 역추정한 배수를 곱해 원 단위로 폅니다.
     *
     * <p>환산 근거가 없거나, 지급금액이 음수거나, 산출값이 컬럼 용량을 넘으면 <b>적재하지 않고 경고만</b> 냅니다. 파일은 그대로 반영되고 세 컬럼은 품목 합계
     * 스냅샷으로 남습니다 — 여기서 막으면 1-2가 정상인 파일까지 통째로 반입되지 못합니다.
     *
     * @param declared 1-1이 읽어 온 선언 금액
     * @param unit 요약표 기재 단위. 판정에 실패했으면 빈 Optional
     * @param foreignCurrencyItems 1-2에 원화 합계로 잡히지 않는 외화 품목이 있으면 true. 단위 미확정의 원인을 가르는 데만 씁니다
     * @param projectName 진단에 붙일 사업명
     * @param diagnostics 진단 수집 목록 (실패 시 경고가 추가됩니다)
     * @return 산출한 금액. 실패하면 {@link ProjectAmounts#none()}
     */
    private ProjectAmounts declaredAmounts(
            CapitalOverviewReader.DeclaredAmounts declared,
            Optional<AmountUnit> unit,
            boolean foreignCurrencyItems,
            String projectName,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (declared.yearTotalRaw() == null) {
            return skipAmounts(projectName, "1-1 요약표를 찾지 못했습니다.", diagnostics);
        }
        if (unit.isEmpty()) {
            return skipAmounts(
                    projectName,
                    foreignCurrencyItems
                            ? "외화 품목이 있어 1-1 요약표를 1-2 품목 합계로 대사할 수 없습니다."
                            : "1-1 요약표의 기재 단위를 1-2 품목 합계로 확정하지 못했습니다.",
                    diagnostics);
        }

        AmountUnit resolved = unit.get();
        BigDecimal whole =
                declared.wholePeriodWon() != null
                        ? declared.wholePeriodWon()
                        : resolved.toWon(declared.wholePeriodRaw());
        if (whole == null) {
            return skipAmounts(
                    projectName,
                    declared.wholePeriodUnknownUnit()
                            ? "`총 사업금액(전체기간)`의 표기를 금액으로 해석하지 못했습니다."
                            : "`총 사업금액(전체기간)` 칸이 비어 있습니다.",
                    diagnostics);
        }

        BigDecimal year = resolved.toWon(declared.yearTotalRaw());
        BigDecimal later =
                declared.laterTotalRaw() == null
                        ? BigDecimal.ZERO
                        : resolved.toWon(declared.laterTotalRaw());
        BigDecimal paid = whole.subtract(later).subtract(year);
        if (paid.signum() < 0) {
            return skipAmounts(
                    projectName,
                    "`총 사업금액(전체기간)`(%s)이 요약표 합계(%s)보다 작습니다."
                            .formatted(whole.toPlainString(), year.add(later).toPlainString()),
                    diagnostics);
        }
        BigDecimal overflow = firstOverColumnCapacity(whole, later, paid);
        if (overflow != null) {
            return skipAmounts(
                    projectName,
                    "산출한 금액(%s)이 저장 가능한 범위를 넘습니다.".formatted(overflow.toPlainString()),
                    diagnostics);
        }
        return new ProjectAmounts(whole, later, paid);
    }

    /**
     * 세 금액 중 컬럼 용량을 넘는 첫 값을 찾습니다.
     *
     * <p>요약표 배수를 자유 텍스트 칸에 적용하는 폴백 경로에서 제출자가 두 칸의 단위를 뒤섞어 적으면 총액이 최대 100만배로 부풀 수 있습니다. 그대로 두면
     * flush에서 {@code ORA-01438}이 나고, 반입 트랜잭션이 롤백되면서 원인과 무관한 `FILE_UNREADABLE`로 <b>파일 전체가 실패</b>합니다.
     * 미적재는 경고일 뿐 파일을 막지 않는다는 계약을 지키려면 저장 전에 걸러야 합니다.
     *
     * @param amounts 검사할 원 단위 금액들 (null 허용)
     * @return 상한을 넘는 첫 값. 모두 담을 수 있으면 null
     */
    private static BigDecimal firstOverColumnCapacity(BigDecimal... amounts) {
        for (BigDecimal amount : amounts) {
            if (amount != null && amount.abs().compareTo(AMOUNT_COLUMN_LIMIT) >= 0) return amount;
        }
        return null;
    }

    /** 산출 실패를 경고로 남기고 빈 금액을 돌려줍니다. 문구에 후속 조치를 함께 적습니다. */
    private ProjectAmounts skipAmounts(
            String projectName, String reason, List<RequestFormDto.FormDiagnostic> diagnostics) {
        diagnostics.add(
                RequestFormDto.FormDiagnostic.about(
                        FormSheetKind.CAPITAL_OVERVIEW,
                        null,
                        "declaredAmounts",
                        projectName,
                        RequestFormDiagnosticCode.AMOUNT_MISMATCH,
                        "%s 기 지급예산을 산출하지 못했습니다. 반입 후 사업 수정 화면에서 입력해 주세요.".formatted(reason),
                        List.of()));
        return ProjectAmounts.none();
    }
}
