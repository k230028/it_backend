package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정보화사업 품목 기준 예산 합계 계산 서비스.
 *
 * <p>BITEMM 품목 목록을 자본예산/개발비/기계장치/무형자산/일반관리비로 분류해 {@link ProjectDto.Response}의 예산 합계 필드에 반영합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ProjectBudgetSummaryService {

    /** 자본예산 세부 코드타입: 개발비 */
    private static final String IOE_DVC = "IOE_DVC";

    /** 자본예산 세부 코드타입: 기계장치 */
    private static final String IOE_HW = "IOE_HW";

    /** 자본예산 세부 코드타입: 기타무형자산 */
    private static final String IOE_SW = "IOE_SW";

    /** 자본예산 세부 코드타입 집합 */
    private static final Set<String> CAPITAL_DETAIL_CTPS = Set.of(IOE_DVC, IOE_HW, IOE_SW);

    /** 공통코드 서비스: IOE 코드 분류 조회용 */
    private final CodeService codeService;

    /** 사업 금액 중앙 계산기: 현재·예정·지급·총소요금액 계산과 외화 예정금액 환산용 */
    private final ProjectAmountCalculator amountCalculator;

    /**
     * 품목 목록으로부터 자본예산/일반관리비 합계를 계산하여 응답 DTO에 설정합니다.
     *
     * @param response 예산 합계를 설정할 응답 DTO
     * @param bitemms 합계 계산 대상 품목 목록
     */
    public void applyBudgetSummary(ProjectDto.Response response, List<Bitemm> bitemms) {
        applyBudgetSummaryValues(
                response,
                bitemms.stream()
                        .map(item -> new BudgetValues(item.getIoeC(), item.getAmt(), item))
                        .toList(),
                amountCalculator.calculate(bitemms, response.getDfrAmt()));
    }

    /**
     * 품목 예산 프로젝션으로 자본예산과 일반관리비 합계를 응답에 설정합니다.
     *
     * @param response 예산 합계를 설정할 응답 DTO
     * @param items 합계 계산 대상 품목 프로젝션
     * @throws NullPointerException 응답 또는 품목 목록이 null인 경우
     */
    public void applyBudgetSummaryViews(
            ProjectDto.Response response, List<ProjectItemRepository.ProjectItemBudgetView> items) {
        List<BudgetValues> budgetValues =
                items.stream()
                        .map(
                                item ->
                                        new BudgetValues(
                                                item.getIoeC(), item.getAmt(), toAmountItem(item)))
                        .toList();
        applyBudgetSummaryValues(
                response,
                budgetValues,
                amountCalculator.calculate(
                        budgetValues.stream().map(BudgetValues::amountItem).toList(),
                        response.getDfrAmt()));
    }

    // 주의: 이 파생 합계는 비목 분류에 걸린 품목만 더한다. 저장 스냅샷(calculateAmountSnapshot)은
    // 미분류 비목도 포함하므로, 미분류 비목이 있는 사업은 두 값이 다를 수 있다.
    private void applyBudgetSummaryValues(
            ProjectDto.Response response,
            List<BudgetValues> bitemms,
            ProjectAmountSummary amountSummary) {
        List<Ccodem> allIoeCodes =
                codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE);
        List<Ccodem> assetCodes =
                allIoeCodes.stream()
                        .filter(
                                c ->
                                        CAPITAL_DETAIL_CTPS.contains(c.getCTp())
                                                || "IOE_CPIT".equals(c.getCTp()))
                        .toList();
        Set<String> assetTypes =
                assetCodes.stream().map(value -> value.getCdva()).collect(Collectors.toSet());

        java.util.Map<String, Set<String>> assetSubTypesByCTp =
                assetCodes.stream()
                        .collect(
                                Collectors.groupingBy(
                                        c -> c.getCTp() != null ? c.getCTp() : "",
                                        Collectors.mapping(
                                                value -> value.getCdva(), Collectors.toSet())));
        Set<String> devTypes =
                new HashSet<>(
                        assetSubTypesByCTp.getOrDefault(IOE_DVC, java.util.Collections.emptySet()));
        Set<String> machTypes =
                new HashSet<>(
                        assetSubTypesByCTp.getOrDefault(IOE_HW, java.util.Collections.emptySet()));
        Set<String> intanTypes =
                new HashSet<>(
                        assetSubTypesByCTp.getOrDefault(IOE_SW, java.util.Collections.emptySet()));

        // 구 데이터 호환: IOE_CPIT 행은 CDVA_DES 한글명으로 세부 분류합니다.
        assetCodes.stream()
                .filter(c -> "IOE_CPIT".equals(c.getCTp()))
                .forEach(
                        c -> {
                            String cdvaDes = c.getCdvaDes() != null ? c.getCdvaDes() : "";
                            if ("단말기".equals(cdvaDes)) devTypes.add(c.getCdva());
                            else if ("기계장치".equals(cdvaDes)) machTypes.add(c.getCdva());
                            else if ("기타무형자산".equals(cdvaDes)) intanTypes.add(c.getCdva());
                        });

        Set<String> costTypes =
                allIoeCodes.stream()
                        .filter(
                                c ->
                                        Set.of("IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE")
                                                .contains(c.getCTp()))
                        .map(value -> value.getCdva())
                        .collect(Collectors.toSet());

        Function<BudgetValues, BigDecimal> calcAmt = this::resolveKrwAmount;

        List<BudgetValues> validItems =
                bitemms.stream().filter(item -> item.ioeC() != null).toList();

        BigDecimal assetBg = sumByIoe(validItems, assetTypes, calcAmt);
        BigDecimal dvcBg = sumByIoe(validItems, devTypes, calcAmt);
        BigDecimal hwBg = sumByIoe(validItems, machTypes, calcAmt);
        BigDecimal swBg = sumByIoe(validItems, intanTypes, calcAmt);
        BigDecimal costBg = sumByIoe(validItems, costTypes, calcAmt);

        response.setBudgetAmounts(assetBg, dvcBg, hwBg, swBg, costBg);

        Function<BudgetValues, BigDecimal> calcMpl =
                item -> amountCalculator.toPlannedKrw(item.amountItem());
        List<BudgetValues> mplItems = validItems;
        BigDecimal mplCpit = sumByIoe(mplItems, assetTypes, calcMpl);
        BigDecimal mplMngc = sumByIoe(mplItems, costTypes, calcMpl);

        response.setMplCpitAmt(mplCpit);
        response.setMplMngcAmt(mplMngc);
        response.setTyyBgAmt(amountSummary.currentRequestAmt());
        response.setPrjBgAmt(amountSummary.totalRequiredAmt());
        response.setMplAmt(amountSummary.plannedAmt());
    }

    /**
     * 저장된 사업 단위 금액 스냅샷으로 총소요금액·예정금액·지급금액·당해 요청금액을 덮어씁니다.
     *
     * <p>{@link #applyBudgetSummary}·{@link #applyBudgetSummaryViews} 다음에 부릅니다. 자본예산·일반관리비 분류 합계와
     * 달리 파생 사업 금액은 비목 분류 여부와 무관하게 모든 활성 품목을 중앙 계산기로 합산합니다.
     *
     * <p>당해 요청금액은 {@code 총소요금액 − 예정금액 − 지급금액}으로 복원합니다. 저장 불변식이 깨진 경우 음수를 0으로 숨기지 않고 그대로 노출합니다.
     *
     * <p>일반 등록·수정 경로는 저장할 때마다 세 컬럼을 중앙 계산 결과로 갱신하므로({@code ProjectService.applyAmountSnapshot}) 보통
     * 파생값과 같습니다. 다른 경우는 저장 컬럼이 정본입니다.
     *
     * @param response 파생 합계가 이미 채워진 응답
     * @param totRqmAmt 저장된 총소요금액. null이면 세 값을 모두 파생 합계로 둡니다
     * @param mplAmt 저장된 예정금액. null이면 0으로 봅니다
     * @param dfrAmt 저장된 원화 지급금액. null이면 0으로 봅니다
     * @throws NullPointerException 응답이 null인 경우
     */
    public void applyStoredAmountSnapshot(
            ProjectDto.Response response,
            BigDecimal totRqmAmt,
            BigDecimal mplAmt,
            BigDecimal dfrAmt) {
        if (response == null || totRqmAmt == null) return;

        BigDecimal storedPlannedAmt = nvl(mplAmt);
        BigDecimal storedPaidAmt = nvl(dfrAmt);
        BigDecimal storedCurrentRequestAmt =
                amountCalculator.restoreCurrentRequestAmount(totRqmAmt, mplAmt, dfrAmt);
        warnSnapshotDiff(response, "tyyBgAmt", response.getTyyBgAmt(), storedCurrentRequestAmt);
        warnSnapshotDiff(response, "prjBgAmt", response.getPrjBgAmt(), totRqmAmt);
        warnSnapshotDiff(response, "mplAmt", response.getMplAmt(), storedPlannedAmt);
        warnSnapshotDiff(response, "dfrAmt", nvl(response.getDfrAmt()), storedPaidAmt);

        response.setPrjBgAmt(totRqmAmt);
        response.setMplAmt(storedPlannedAmt);
        response.setDfrAmt(dfrAmt);
        response.setTyyBgAmt(storedCurrentRequestAmt);
    }

    /**
     * 지정 비목 집합에 해당하는 품목 금액을 합산합니다.
     *
     * @param items 계산 대상 품목
     * @param ioeTypes 합산할 비목 코드 집합
     * @param calcAmt 품목 금액 계산 함수
     * @return 합계 금액
     */
    private BigDecimal sumByIoe(
            List<BudgetValues> items,
            Set<String> ioeTypes,
            Function<BudgetValues, BigDecimal> calcAmt) {
        return items.stream()
                .filter(item -> ioeTypes.contains(item.ioeC()))
                .map(calcAmt)
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
    }

    /**
     * 품목의 저장 원화 금액을 반환합니다.
     *
     * <p>BITEMM.amt는 이미 원화 기준 금액이므로 환율을 다시 적용하지 않습니다.
     *
     * @param item 품목 예산값
     * @return 저장된 원화 금액, null이면 0
     */
    private BigDecimal resolveKrwAmount(BudgetValues item) {
        return item.amt() == null ? BigDecimal.ZERO : item.amt();
    }

    private Bitemm toAmountItem(ProjectItemRepository.ProjectItemBudgetView item) {
        return Bitemm.builder()
                .curC(item.getCurC())
                .amt(item.getAmt())
                .mplAmt(item.getMplAmt())
                .xcr(item.getXcr())
                .build();
    }

    private record BudgetValues(String ioeC, BigDecimal amt, Bitemm amountItem) {}

    /**
     * 활성 품목으로 사업 단위 금액 스냅샷을 계산합니다.
     *
     * <p>현재 요청금액은 모든 품목 AMT 합계이므로 비목 분류를 적용하지 않습니다. 자본/관리비로 나누는 {@code applyBudgetSummary}와 달리, 어느
     * 비목 집합에도 없는 품목도 합계에 포함됩니다.
     *
     * @param bitemms 활성 품목 목록 (null 금액은 0으로 취급, 빈 목록 허용)
     * @param paidAmt 원화 지급금액 (null이면 0)
     * @return 현재 요청금액·원화 예정금액·원화 지급금액·총소요금액
     * @throws NullPointerException 품목 목록이 null인 경우
     */
    public ProjectAmountSummary calculateAmountSnapshot(List<Bitemm> bitemms, BigDecimal paidAmt) {
        return amountCalculator.calculate(bitemms, paidAmt);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void warnSnapshotDiff(
            ProjectDto.Response response,
            String field,
            BigDecimal derivedAmount,
            BigDecimal storedAmount) {
        BigDecimal derived = nvl(derivedAmount);
        BigDecimal stored = nvl(storedAmount);
        if (derived.compareTo(stored) == 0) return;

        log.warn(
                "정보화사업 금액 스냅샷 불일치: projectKey={}, field={}, derived={}, stored={}",
                response.getAbusMngNo(),
                field,
                derived.toPlainString(),
                stored.toPlainString());
    }
}
