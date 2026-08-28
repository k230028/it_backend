package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.AmountUnitResolver;
import com.kdb.it.domain.migration.request.service.FormLexicon;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <p>품목 금액({@code BITEMM})의 원본은 원칙적으로 1-2입니다. 단, 1-2가 없는 단일 시트 양식은 1-1 요약표의 명시 단위와 비목별 합계로 품목을
 * 합성합니다. 사업 단위 금액 3종({@code TOT_RQM_AMT}·{@code MPL_AMT}·{@code DFR_AMT})은 1-1 선언값에서 산출합니다.
 *
 * <p>1-2가 있으면 항상 원 단위인 품목 합계와 대사해 1-1 배수를 역추정합니다. 품목 합계가 요약표와 맞지 않으면(실측: 1-2 일반관리비가 익년 이후 계약분까지 담은
 * 연간 금액) 1-1이 단위와 함께 한 번 더 적은 `'26년도 필요예산 편성요청` 칸을 두 번째 기준점으로 씁니다. 1-2가 없으면 1-1 표 제목에 명시된 단위를 씁니다.
 * 환산 근거가 없거나 산출값을 신뢰할 수 없으면 <b>적재하지 않고 경고만</b> 내며 파일 반입 자체는 막지 않습니다.
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

    /**
     * 폴백 경로에서 허용하는 `총 사업금액(전체기간)` ÷ (`'26년도 합계` + `'26년도 이후`) 비율 상한 (MIG-14).
     *
     * <p>업무 확정값 <b>100배</b>입니다. 정상 다년도 사업의 비율은 실측 한~두 자릿수이고, 두 칸의 단위가 뒤섞이면 5자릿수 이상 벌어집니다. 그 사이를 넉넉히
     * 가르는 값이라 정상 파일을 막지 않습니다.
     */
    private static final BigDecimal WHOLE_PERIOD_RATIO_LIMIT = BigDecimal.valueOf(100);

    /** 1-1 요청예산을 품목 합계로 자동 보정하는 오차율 상한(배타). */
    private static final BigDecimal CURRENT_AMOUNT_TOLERANCE_PERCENT = BigDecimal.valueOf(3);

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
        if (project.getAbusNm() == null
                || project.getAbusNm().isBlank()
                || FormLexicon.isNotApplicableProjectName(project.getAbusNm())) {
            // 1-1 시트가 빈 껍데기인 파일(경상사업·일반관리비만 낸 부점)이라 진단 없이 건너뛴다
            return FormAdapterOutput.empty();
        }

        List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>(read.diagnostics());
        ItemReadResult itemRead =
                readItems(context, read.amounts(), project.getAbusNm(), diagnostics);
        List<ProjectDto.BitemmDto> items = itemRead.items();
        project.setItems(items);

        BigDecimal itemTotal = sumItemAmounts(items);
        BigDecimal declaredCurrent =
                read.amounts().yearRequestWon() != null
                        ? read.amounts().yearRequestWon()
                        : read.amounts().summaryUnit() == null
                                        || read.amounts().yearTotalRaw() == null
                                ? null
                                : read.amounts().summaryUnit().toWon(read.amounts().yearTotalRaw());
        BigDecimal declaredCurrentBasis =
                closestCurrentBasis(
                        itemTotal,
                        itemRead.generalExpenseAmounts(),
                        read.amounts().yearTotalRaw(),
                        declaredCurrent);
        Optional<AmountUnit> itemUnit =
                AmountUnitResolver.inferUnit(read.amounts().yearTotalRaw(), declaredCurrentBasis);
        // 1-2 품목 합계로 대사되지 않으면 1-1이 스스로 적은 `'26년도 필요예산 편성요청`을 두 번째 기준점으로 쓴다.
        // 같은 금액을 단위와 함께 한 번 더 적은 칸이라 1-2와 어긋난 파일에서도 요약표 배수를 확정할 수 있다
        Optional<AmountUnit> unit =
                itemUnit.isPresent()
                        ? itemUnit
                        : AmountUnitResolver.inferUnit(
                                read.amounts().yearTotalRaw(), read.amounts().yearRequestWon());
        ProjectAmounts amounts =
                declaredAmounts(
                        read.amounts(),
                        unit,
                        itemTotal,
                        !itemRead.generalExpenseAmounts().isEmpty()
                                && AmountUnitResolver.inferUnit(
                                                read.amounts().yearTotalRaw(), declaredCurrentBasis)
                                        .isPresent(),
                        hasForeignCurrencyItem(items),
                        project.getAbusNm(),
                        diagnostics);
        reconcileTotals(
                read.amounts().yearTotalRaw(),
                declaredCurrentBasis,
                itemUnit,
                unit,
                amounts,
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
        // 전결권자는 이름이 코드표에 없을 때 사람이 고를 수 있어야 한다. 후보가 없으면 그 파일은 영구히 차단된다
        options.put("edrtTc", catalogReader.edrtCapitalCandidates());
        return new FormCatalogs(
                catalogReader.exePttCodeByName(),
                catalogReader.edrtCapitalCodeByName(),
                catalogReader.reportStatusCodeByName(),
                Map.copyOf(options));
    }

    /** 1-2의 두 블록을 읽고, 시트가 없으면 1-1 비목별 요약 행으로 품목을 만듭니다. */
    private ItemReadResult readItems(
            FormAdapterContext context,
            CapitalOverviewReader.DeclaredAmounts declared,
            String projectName,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Sheet resource = context.sheets().get(FormSheetKind.CAPITAL_RESOURCE);
        List<ProjectDto.BitemmDto> items = new ArrayList<>();
        if (resource == null) {
            return new ItemReadResult(
                    summaryItems(declared, projectName, context, diagnostics), List.of());
        }

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
        List<ProjectDto.BitemmDto> generalItems = new ArrayList<>();
        if (general.isPresent()) {
            for (ResourceRow row : general.get().rows()) {
                generalItems.add(toItem(row, context, sno++, diagnostics));
            }
        }
        if (items.isEmpty()) {
            items.addAll(summaryItems(declared, projectName, context, diagnostics));
        }
        moveExactPlannedItem(items, declared);
        List<BigDecimal> generalExpenseAmounts =
                generalItems.stream()
                        .map(ProjectDto.BitemmDto::getAmt)
                        .filter(Objects::nonNull)
                        .toList();
        return new ItemReadResult(List.copyOf(items), generalExpenseAmounts);
    }

    /** 일반관리비가 당해·예정 순으로 이어진 양식은 당해 선언액에 가장 가까운 앞쪽 행까지만 대사합니다. */
    static BigDecimal closestCurrentBasis(
            BigDecimal capitalTotal,
            List<BigDecimal> generalExpenseAmounts,
            BigDecimal declaredRaw,
            BigDecimal declaredCurrent) {
        BigDecimal baseCapitalTotal = capitalTotal == null ? BigDecimal.ZERO : capitalTotal;
        BigDecimal candidate = baseCapitalTotal;
        if (AmountUnitResolver.inferUnit(declaredRaw, candidate).isPresent()) return candidate;
        for (BigDecimal amount : generalExpenseAmounts) {
            if (amount == null) continue;
            candidate = candidate.add(amount);
            if (AmountUnitResolver.inferUnit(declaredRaw, candidate).isPresent()) return candidate;
        }
        BigDecimal best = baseCapitalTotal;
        if (declaredCurrent == null) {
            return candidate;
        }
        BigDecimal bestGap = declaredCurrent.subtract(best).abs();
        candidate = baseCapitalTotal;
        for (BigDecimal amount : generalExpenseAmounts) {
            if (amount == null) continue;
            candidate = candidate.add(amount);
            BigDecimal gap = declaredCurrent.subtract(candidate).abs();
            if (gap.compareTo(bestGap) < 0) {
                best = candidate;
                bestGap = gap;
            }
        }
        return best;
    }

    /** 저장할 자본 품목과 1-1 금액 대사에만 쓸 일반관리비 행 금액을 함께 전달합니다. */
    private record ItemReadResult(
            List<ProjectDto.BitemmDto> items, List<BigDecimal> generalExpenseAmounts) {}

    /** 1-1 예정금액과 정확히 같은 단일 자본 품목은, 나머지가 당해 합계와 맞을 때만 예정 품목으로 분리합니다. */
    private void moveExactPlannedItem(
            List<ProjectDto.BitemmDto> items, CapitalOverviewReader.DeclaredAmounts declared) {
        if (declared.summaryUnit() == null
                || declared.yearTotalRaw() == null
                || declared.laterTotalRaw() == null) return;
        BigDecimal planned = declared.summaryUnit().toWon(declared.laterTotalRaw());
        List<ProjectDto.BitemmDto> matches =
                items.stream().filter(item -> Objects.equals(item.getAmt(), planned)).toList();
        if (matches.size() != 1) return;
        BigDecimal declaredCurrent = declared.summaryUnit().toWon(declared.yearTotalRaw());
        BigDecimal remainingCurrent =
                items.stream()
                        .filter(item -> item != matches.getFirst())
                        .map(ProjectDto.BitemmDto::getAmt)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (remainingCurrent.compareTo(declaredCurrent) != 0) return;
        matches.getFirst().setAmt(BigDecimal.ZERO);
        matches.getFirst().setMplAmt(planned);
    }

    /** 1-2가 없는 단일 시트 양식의 1-1 비목별 합계를 합성 BITEMM DTO로 바꿉니다. */
    private List<ProjectDto.BitemmDto> summaryItems(
            CapitalOverviewReader.DeclaredAmounts declared,
            String projectName,
            FormAdapterContext context,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (declared.summaryUnit() == null || declared.summaryItems().isEmpty()) {
            return List.of();
        }

        List<ProjectDto.BitemmDto> items = new ArrayList<>();
        int sno = 1;
        for (CapitalOverviewReader.SummaryItem row : declared.summaryItems()) {
            ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
            item.setSno(sno++);
            item.setIoeC(
                    resolveIoe(
                            FormSheetKind.CAPITAL_OVERVIEW,
                            row.excelRow(),
                            row.group(),
                            projectName,
                            context,
                            diagnostics));
            item.setGclNm(projectName);
            item.setQty(BigDecimal.ONE);
            item.setCurC("KRW");
            item.setXcrBseDt(context.bseYy() + "0101");
            item.setLstYn("Y");
            item.setAmt(declared.summaryUnit().toWon(row.amountRaw()));
            item.setMplAmt(declared.summaryUnit().toWon(row.laterAmountRaw()));
            items.add(item);
        }
        return List.copyOf(items);
    }

    private ProjectDto.BitemmDto toItem(
            ResourceRow row,
            FormAdapterContext context,
            int sno,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        return ResourceTableReader.toItem(
                row,
                resolveIoe(row, context, diagnostics),
                sno,
                context.bseYy(),
                !context.foreignBranch());
    }

    private String resolveIoe(
            ResourceRow row,
            FormAdapterContext context,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        return resolveIoe(
                FormSheetKind.CAPITAL_RESOURCE,
                row.excelRow(),
                row.group(),
                row.itemName(),
                context,
                diagnostics);
    }

    private String resolveIoe(
            FormSheetKind sheet,
            int excelRow,
            String group,
            String subject,
            FormAdapterContext context,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> override = context.override(sheet, excelRow, "ioeC");
        if (override.isPresent() && context.ioeIndex().exists(override.get()))
            return override.get();

        boolean domestic = !context.foreignBranch();
        IoeHierarchyIndex.Resolution resolution =
                sheet == FormSheetKind.CAPITAL_OVERVIEW
                        ? context.ioeIndex().resolveByDetail("", group)
                        : context.ioeIndex().resolveByGroup(group, domestic);
        if (resolution.isUnresolved()) {
            resolution = context.ioeIndex().resolveByGroup(group, domestic);
        }

        if (resolution.code() == null) {
            diagnostics.add(
                    itemDiagnostic(
                            sheet,
                            excelRow,
                            subject,
                            context,
                            resolution.isAmbiguous()
                                    ? RequestFormDiagnosticCode.CODE_AMBIGUOUS
                                    : RequestFormDiagnosticCode.CODE_UNRESOLVED,
                            "구분 `%s`의 비목을 정하지 못했습니다.".formatted(group),
                            resolution));
            return null;
        }
        if (!resolution.candidates().isEmpty()) {
            // 기본값으로 정했지만 대안이 있는 경우다. 반영은 막지 않고 확인만 요청한다 —
            // 여기서 막으면 개발비·기타무형자산 품목이 있는 파일이 전부 차단된다.
            diagnostics.add(
                    itemDiagnostic(
                            sheet,
                            excelRow,
                            subject,
                            context,
                            RequestFormDiagnosticCode.CODE_DEFAULTED,
                            "구분 `%s`의 비목을 `%s`로 기본 설정했습니다. 다른 비목이면 골라 주세요."
                                    .formatted(group, resolution.label()),
                            resolution));
        }
        return resolution.code();
    }

    private RequestFormDto.FormDiagnostic itemDiagnostic(
            FormSheetKind sheet,
            int excelRow,
            String subject,
            FormAdapterContext context,
            RequestFormDiagnosticCode code,
            String message,
            IoeHierarchyIndex.Resolution resolution) {
        return RequestFormDto.FormDiagnostic.about(
                sheet,
                excelRow,
                "ioeC",
                subject,
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
     *
     * <p>`'26년도 필요예산 편성요청` 폴백으로 배수를 확정했더라도 두 표가 어긋난 사실 자체는 남깁니다. 금액은 적재되지만 1-1과 1-2 중 어느 쪽이 맞는지는
     * 사람만 판단할 수 있습니다.
     *
     * @param declaredYearTotal 요약표 `'26년도 합계` 기재값
     * @param itemTotal 1-2 품목 합계 (원 단위)
     * @param itemUnit 품목 합계로 역추정한 배수. 대사에 실패했으면 빈 Optional
     * @param unit 폴백까지 적용해 확정한 배수. 끝내 못 정했으면 빈 Optional
     * @param projectName 진단에 붙일 사업명
     * @param diagnostics 진단 수집 목록
     */
    private void reconcileTotals(
            BigDecimal declaredYearTotal,
            BigDecimal itemTotal,
            Optional<AmountUnit> itemUnit,
            Optional<AmountUnit> unit,
            ProjectAmounts amounts,
            String projectName,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (declaredYearTotal == null || itemTotal == null || itemTotal.signum() == 0) return;
        if (itemUnit.isPresent()) return;

        String message;
        AmountUnit resolvedUnit = unit.orElse(null);
        if (resolvedUnit != null) {
            BigDecimal requested = resolvedUnit.toWon(declaredYearTotal);
            if (requested == null) {
                throw new IllegalStateException("요약표 금액 환산 결과가 없습니다.");
            }
            if (isBelowCurrentAmountTolerance(requested, itemTotal)) return;
            message = amountMismatchMessage(requested, itemTotal, amounts, resolvedUnit);
        } else {
            message =
                    "1-1 요약표의 합계(%s)와 1-2 품목 합계(%s)가 어느 단위로도 맞지 않습니다."
                            .formatted(
                                    declaredYearTotal.toPlainString(), itemTotal.toPlainString());
        }
        diagnostics.add(
                RequestFormDto.FormDiagnostic.about(
                        FormSheetKind.CAPITAL_OVERVIEW,
                        null,
                        "declaredYearTotal",
                        projectName,
                        RequestFormDiagnosticCode.AMOUNT_MISMATCH,
                        message,
                        List.of()));
    }

    /**
     * 1-1 선언 금액에서 사업 단위 금액 3종을 산출합니다.
     *
     * <p>산식은 {@code 총소요금액 = 총 사업금액(전체기간)}, {@code 예정금액 = '26년도 이후}, {@code 지급금액 = 총 사업금액 − '26년도 이후
     * − '26년도 합계}입니다. 요약표는 1-2 품목 합계로 역추정하거나 단일 시트 표의 명시 단위를 적용해 원 단위로 폅니다.
     *
     * <p>환산 근거가 없거나, 총액 칸에 단위가 없어 요약표 배수 해석과 원 단위 해석이 모두 성립하거나, 지급금액이 음수거나, 산출값이 컬럼 용량을 넘으면 <b>적재하지
     * 않고 경고만</b> 냅니다. 파일은 그대로 반영되고 세 컬럼은 품목 합계 스냅샷으로 남습니다 — 여기서 막으면 1-2가 정상인 파일까지 통째로 반입되지 못합니다.
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
            BigDecimal itemTotal,
            boolean separateGeneralExpense,
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
        if (itemTotal.signum() > 0
                && (separateGeneralExpense || isBelowCurrentAmountTolerance(year, itemTotal))) {
            BigDecimal adjustedPaid = whole.subtract(later).subtract(itemTotal);
            if (adjustedPaid.signum() >= 0) year = itemTotal;
        }
        BigDecimal paid = whole.subtract(later).subtract(year);

        // 조건 ⑥(모호): `총 사업금액(전체기간)`에 접미사가 없어 요약표 배수로 폴백한 경우에 한해,
        // 배수를 적용한 해석(candidateA=whole, 현재 동작)과 원 단위 그대로라는 해석(candidateB)이
        // 둘 다 지급금액을 음수로 만들지 않으면 어느 쪽이 맞는지 산술만으로 확정할 수 없다.
        // 배수가 1(WON)이면 두 해석이 같은 값이라 애초에 모호할 수 없으므로 먼저 걸러낸다 —
        // 빠뜨리면 폴백 경로의 정상 파일(WON 단위)이 전부 미적재로 돌아가는 회귀가 된다.
        // whole은 이 판정과 무관하게 계속 candidateA를 쓴다: 이 조건은 적재 여부만 조이고
        // 값을 candidateB로 바꾸지 않는다.
        boolean usedSummaryMultiplier =
                declared.wholePeriodWon() == null
                        && declared.wholePeriodRaw() != null
                        && resolved != AmountUnit.WON;
        if (usedSummaryMultiplier) {
            BigDecimal candidateB = declared.wholePeriodRaw();
            BigDecimal paidB = candidateB.subtract(later).subtract(year);
            if (paid.signum() >= 0 && paidB.signum() >= 0) {
                return skipAmounts(
                        projectName,
                        "`총 사업금액(전체기간)`에 단위가 적혀 있지 않아 요약표 단위(%s)로 읽었는데, 원 단위로 읽어도 계산이 맞아 어느 쪽인지 확정할 수 없습니다. 칸에 단위를 함께 적어 주세요."
                                .formatted(resolved.label()),
                        diagnostics);
            }
        }
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
        // 조건 ⑦(비율 상한, MIG-14): 조건 ⑥은 두 해석이 **둘 다 성립할 때**만 막으므로, 원 단위 해석의
        // 지급금액이 음수인 경우(= 원 단위 총액이 요약표 합계보다 작은 경우)는 모호로 판정되지 않아
        // 배수가 곱해진 총액이 그대로 적재됐다. 그 구멍을 비율로 닫는다.
        //
        // 컬럼 용량 검사(조건 ⑤) 뒤에 둔다 — 저장 가능 여부는 하드 제약이고 이쪽은 업무 타당성
        // 판정이라, 둘 다 걸리는 파일에서는 저장 제약 문구가 원인에 더 가깝다(선례: 조건⑤ 테스트).
        BigDecimal declaredBase = year.add(later);
        if (usedSummaryMultiplier
                && declaredBase.signum() > 0
                && whole.compareTo(declaredBase.multiply(WHOLE_PERIOD_RATIO_LIMIT)) > 0) {
            return skipAmounts(
                    projectName,
                    "`총 사업금액(전체기간)`(%s)이 요약표 합계(%s)의 %s배를 넘습니다. 단위 표기가 뒤섞였는지 확인해 주세요."
                            .formatted(
                                    whole.toPlainString(),
                                    declaredBase.toPlainString(),
                                    WHOLE_PERIOD_RATIO_LIMIT.toPlainString()),
                    diagnostics);
        }
        return new ProjectAmounts(whole, later, paid);
    }

    private static boolean isBelowCurrentAmountTolerance(
            BigDecimal requested, BigDecimal itemTotal) {
        if (requested == null || itemTotal == null || itemTotal.signum() <= 0) return false;
        return requested
                        .subtract(itemTotal)
                        .abs()
                        .multiply(BigDecimal.valueOf(100))
                        .compareTo(itemTotal.multiply(CURRENT_AMOUNT_TOLERANCE_PERCENT))
                < 0;
    }

    private static String amountMismatchMessage(
            BigDecimal requested, BigDecimal itemTotal, ProjectAmounts amounts, AmountUnit unit) {
        BigDecimal gapPercent =
                requested
                        .subtract(itemTotal)
                        .abs()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(itemTotal, 2, java.math.RoundingMode.HALF_UP);
        String planned = amounts.isPresent() ? amounts.mplAmt().toPlainString() : "산출 불가";
        String paid = amounts.isPresent() ? amounts.dfrAmt().toPlainString() : "산출 불가";
        return "1-1 요청금액(%s)과 1-2 품목 합계(%s)의 차이가 %s%%로 자동 보정 범위(3%% 미만)를 벗어납니다. 예정금액=%s, 기지급금액=%s입니다. 요약표 단위는 %s으로 확정했습니다."
                .formatted(
                        requested.toPlainString(),
                        itemTotal.toPlainString(),
                        gapPercent.toPlainString(),
                        planned,
                        paid,
                        unit.label());
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
