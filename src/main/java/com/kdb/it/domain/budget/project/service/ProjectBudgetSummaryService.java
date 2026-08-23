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

    // 주의: 이 파생 합계는 비목 분류에 걸린 품목만 더한다. 저장 스냅샷(calculateAmountSnapshot)은
    // 미분류 비목도 포함하므로, 미분류 비목이 있는 사업은 두 값이 다를 수 있다.
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
        response.setTyyBgAmt(currentYear);
        // 총 예산·익년 이후 예산 파생값 (DB 스냅샷 컬럼과 같은 의미, 조회는 파생값을 쓴다)
        response.setPrjBgAmt(totalAmt);
        response.setMplAmt(totalMpl);
    }

    /**
     * 저장된 사업 단위 금액 스냅샷으로 총 예산·익년 이후 예산·당해예산을 덮어씁니다.
     *
     * <p>{@link #applyBudgetSummary}·{@link #applyBudgetSummaryViews} 다음에 부릅니다. 파생 합계는 <b>예산연도 품목 중
     * 비목 분류에 걸린 것</b>만 더하므로, 사업 전체기간 금액이 따로 선언된 편성요청서 반입 사업은 화면이 선언값을 보여주지 못합니다(실측: 총 사업금액
     * 2,000백만원인 사업이 품목 합계 1,217백만원으로 표시).
     *
     * <p>당해예산은 {@code 총 예산 − 익년 이후 − 기 지급예산}(0 하한)으로 다시 셉니다. 기 지급예산은 총 예산 안에 든 과거 지급분이므로({@code
     * ProjectService.applyAmountSnapshot}이 `기 지급예산 ≤ 총 예산`을 검증합니다) 빼야 세 값의 합이 총 예산과 맞습니다. 기 지급예산이
     * 없는 사업은 종전 파생식({@code ∑AMT − ∑MPL_AMT})과 같은 값입니다.
     *
     * <p>일반 등록·수정 경로는 저장할 때마다 두 컬럼을 품목 합계로 갱신하므로({@code ProjectService.applyAmountSnapshot}) 보통
     * 파생값과 같습니다. 다른 경우는 저장 컬럼이 정본입니다.
     *
     * @param response 파생 합계가 이미 채워진 응답
     * @param totRqmAmt 저장된 총소요금액. null이면 세 값을 모두 파생 합계로 둡니다
     * @param mplAmt 저장된 예정금액. null이면 파생 합계를 그대로 씁니다
     * @param dfrAmt 저장된 기 지급예산. null이면 0으로 봅니다
     * @throws NullPointerException 응답이 null인 경우
     */
    public void applyStoredAmountSnapshot(
            ProjectDto.Response response,
            BigDecimal totRqmAmt,
            BigDecimal mplAmt,
            BigDecimal dfrAmt) {
        if (totRqmAmt == null) return;

        response.setPrjBgAmt(totRqmAmt);
        if (mplAmt != null) response.setMplAmt(mplAmt);
        BigDecimal currentYear =
                totRqmAmt.subtract(nvl(response.getMplAmt())).subtract(nvl(dfrAmt));
        response.setTyyBgAmt(currentYear.signum() < 0 ? BigDecimal.ZERO : currentYear);
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

    /**
     * 사업 단위 금액 스냅샷.
     *
     * @param totRqmAmt 총 예산 (활성 품목 AMT 합계)
     * @param mplAmt 예산연도+1 이후 예산 (활성 품목 MPL_AMT 합계)
     */
    public record AmountSnapshot(BigDecimal totRqmAmt, BigDecimal mplAmt) {}

    /**
     * 활성 품목으로 사업 단위 금액 스냅샷을 계산합니다.
     *
     * <p>화면 [총 예산]은 모든 품목 소계의 합이므로 비목 분류를 적용하지 않고 전체를 더합니다. 자본/관리비로 나누는 {@code applyBudgetSummary}와
     * 달리, 어느 비목 집합에도 없는 품목도 합계에 포함됩니다.
     *
     * @param bitemms 활성 품목 목록 (null 금액은 0으로 취급, 빈 목록 허용)
     * @return 총 예산과 익년 이후 예산 합계 (항상 non-null, 최소 0)
     * @throws NullPointerException 품목 목록이 null인 경우
     */
    public AmountSnapshot calculateAmountSnapshot(List<Bitemm> bitemms) {
        BigDecimal totRqmAmt = BigDecimal.ZERO;
        BigDecimal mplAmt = BigDecimal.ZERO;
        for (Bitemm item : bitemms) {
            totRqmAmt = totRqmAmt.add(nvl(item.getAmt()));
            mplAmt = mplAmt.add(nvl(item.getMplAmt()));
        }
        return new AmountSnapshot(totRqmAmt, mplAmt);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
