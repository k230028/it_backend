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
                        .map(
                                item ->
                                        new BudgetValues(
                                                item.getIoeC(), item.getAmt(), item.getMplAmt()))
                        .toList());
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
        applyBudgetSummaryValues(
                response,
                items.stream()
                        .map(
                                item ->
                                        new BudgetValues(
                                                item.getIoeC(), item.getAmt(), item.getMplAmt()))
                        .toList());
    }

    private void applyBudgetSummaryValues(
            ProjectDto.Response response, List<BudgetValues> bitemms) {
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

        // === 예정금액(MPL_AMT) 파생 합산 (Bprojm 3개 컬럼 대체) ===
        // MPL_AMT도 저장 시점 금액을 그대로 사용해 AMT와 동일한 집계 기준을 유지합니다.
        Function<BudgetValues, BigDecimal> calcMpl =
                i -> {
                    if (i.mplAmt() == null) return BigDecimal.ZERO;
                    return i.mplAmt();
                };
        List<BudgetValues> mplItems = bitemms.stream().filter(i -> i.ioeC() != null).toList();
        BigDecimal mplCpit = sumByIoe(mplItems, assetTypes, calcMpl);
        BigDecimal mplMngc = sumByIoe(mplItems, costTypes, calcMpl);
        // 당해예산 = 비목 합계(AMT) - 비목 합계(MPL_AMT), 음수이면 0으로 보정
        BigDecimal totalAmt = assetBg.add(costBg);
        BigDecimal totalMpl = mplCpit.add(mplMngc);
        BigDecimal currentYear = totalAmt.subtract(totalMpl);
        if (currentYear.signum() < 0) currentYear = BigDecimal.ZERO;

        response.setMplCpitAmt(mplCpit);
        response.setMplMngcAmt(mplMngc);
        response.setTotRqmAmt(currentYear);
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

    private record BudgetValues(String ioeC, BigDecimal amt, BigDecimal mplAmt) {}
}
