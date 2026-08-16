package com.kdb.it.domain.budget.work.service;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 편성률 적용과 BBUGTM 저장을 담당합니다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetRateApplicationService {

    private static final BigDecimal DEFAULT_DUP_RT = BigDecimal.valueOf(100);
    private static final BigDecimal PERCENT_BASE = BigDecimal.valueOf(100);

    private final BbugtmRepository bbugtmRepository;
    private final ProjectItemRepository projectItemRepository;
    private final CostRepository costRepository;
    private final AuditorAware<String> auditorAware;
    private final BudgetIoeCatalog ioeCatalog;
    private final BudgetSummaryService budgetSummaryService;

    /**
     * 비목별 편성률을 결재완료 비용·품목에 적용합니다.
     *
     * @param request 예산연도와 비목별 편성률
     * @return 저장 건수와 저장 직후 요약
     */
    @Transactional
    public BudgetWorkDto.ApplyResponse applyRates(BudgetWorkDto.ApplyRequest request) {
        String bgYy = request.bgYy();
        String bgMngNo = generateBgMngNo(bgYy);
        int nextSno = 0;
        int totalRecords = 0;
        Map<String, Set<String>> prefixToIoeCodes =
                ioeCatalog.buildPrefixToIoeCValuesMap(ioeCatalog.findCodes(CommonCodeGroups.IOE));
        Map<String, Bbugtm> existingCosts = existingBudgetMap(bgYy, "BCOSTM");
        Map<String, Bbugtm> existingItems = existingBudgetMap(bgYy, "BITEMM");

        for (BudgetWorkDto.RateItem rate : request.rates()) {
            String prefix = ioeCatalog.extractPrefix(rate.cdId());
            Set<String> ioeCodes = prefixToIoeCodes.getOrDefault(prefix, Set.of());
            BigDecimal dupRt = rate.dupRt() == null ? null : BigDecimal.valueOf(rate.dupRt());
            for (Bcostm cost : bbugtmRepository.findApprovedCostsByIoeCValues(ioeCodes, bgYy)) {
                String key = naturalKey(cost.getCostBgNo(), cost.getBgSno(), cost.getIoeC());
                Bbugtm existing = existingCosts.get(key);
                BigDecimal amount = calculateDupBg(cost.getCostTotXpAmt(), dupRt);
                if (existing != null) {
                    existing.update(amount, dupRt);
                } else {
                    Bbugtm created =
                            newBudget(
                                    bgMngNo,
                                    ++nextSno,
                                    bgYy,
                                    "BCOSTM",
                                    cost.getCostBgNo(),
                                    cost.getBgSno(),
                                    cost.getIoeC(),
                                    amount,
                                    dupRt);
                    bbugtmRepository.save(created);
                    existingCosts.put(key, created);
                }
                totalRecords++;
            }
            for (Bitemm item : bbugtmRepository.findApprovedItemsByIoeCValues(ioeCodes, bgYy)) {
                String key = naturalKey(item.getGclMngNo(), item.getSno(), item.getIoeC());
                Bbugtm existing = existingItems.get(key);
                BigDecimal amount =
                        calculateDupBg(
                                item.getAmt() != null ? item.getAmt() : BigDecimal.ZERO, dupRt);
                if (existing != null) {
                    existing.update(amount, dupRt);
                } else {
                    Bbugtm created =
                            newBudget(
                                    bgMngNo,
                                    ++nextSno,
                                    bgYy,
                                    "BITEMM",
                                    item.getGclMngNo(),
                                    item.getSno(),
                                    item.getIoeC(),
                                    amount,
                                    dupRt);
                    bbugtmRepository.save(created);
                    existingItems.put(key, created);
                }
                totalRecords++;
            }
        }
        bbugtmRepository.flush();
        return new BudgetWorkDto.ApplyResponse(
                "편성률 적용 완료", totalRecords, budgetSummaryService.getSummary(bgYy));
    }

    /**
     * 사업별 자본·일반관리비 편성률을 적용합니다.
     *
     * @param request 예산연도와 사업별 편성률
     * @return 저장 건수와 저장 직후 요약
     */
    @Transactional
    public BudgetWorkDto.ApplyResponse applyItemRates(BudgetWorkDto.ItemApplyRequest request) {
        String bgYy = request.bgYy();
        String bgMngNo = generateBgMngNo(bgYy);
        String changerUsid =
                auditorAware
                        .getCurrentAuditor()
                        .orElseGet(
                                () -> {
                                    log.warn(
                                            "applyItemRates: AuditorAware에서 사번을 가져오지 못했습니다. LST_CHG_USID를 'SYSTEM'으로 설정합니다.");
                                    return "SYSTEM";
                                });
        bbugtmRepository.softDeleteByBseYy(bgYy, changerUsid, LocalDateTime.now());

        List<Ccodem> capitalCodes =
                ioeCatalog.findCodes(CommonCodeGroups.IOE).stream()
                        .filter(code -> ioeCatalog.isCapitalCTp(code.getCTp()))
                        .toList();
        if (capitalCodes.isEmpty()) capitalCodes = ioeCatalog.findCodes("IOE_CPIT");
        Set<String> capitalPrefixes =
                capitalCodes.stream()
                        .map(Ccodem::getCdva)
                        .filter(value -> value != null)
                        .flatMap(
                                value -> {
                                    int lastDash = value.lastIndexOf('-');
                                    return lastDash > 0
                                            ? java.util.stream.Stream.of(
                                                    value.substring(0, lastDash), value)
                                            : java.util.stream.Stream.of(value);
                                })
                        .collect(Collectors.toSet());

        int nextSno = 0;
        int totalRecords = 0;
        for (BudgetWorkDto.ItemRate item : request.items()) {
            BigDecimal assetRate =
                    item.assetDupRt() != null
                            ? BigDecimal.valueOf(item.assetDupRt())
                            : DEFAULT_DUP_RT;
            BigDecimal costRate =
                    item.costDupRt() != null
                            ? BigDecimal.valueOf(item.costDupRt())
                            : DEFAULT_DUP_RT;
            Map<String, BigDecimal> ioeRates =
                    item.ioeRates() == null ? Map.of() : item.ioeRates();
            if ("BPROJM".equals(item.orcTb())) {
                for (Bitemm source :
                        projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(
                                item.orcPkVl(), "N", "Y")) {
                    BigDecimal rate =
                            rateOf(source.getIoeC(), ioeRates, assetRate, costRate, capitalPrefixes);
                    BigDecimal requestAmount =
                            source.getAmt() != null ? source.getAmt() : BigDecimal.ZERO;
                    bbugtmRepository.save(
                            newBudget(
                                    bgMngNo,
                                    ++nextSno,
                                    bgYy,
                                    "BITEMM",
                                    source.getGclMngNo(),
                                    source.getSno(),
                                    source.getIoeC(),
                                    calculateDupBg(requestAmount, rate),
                                    rate));
                    totalRecords++;
                }
            } else if ("BCOSTM".equals(item.orcTb())) {
                for (Bcostm source :
                        costRepository.findByCostBgNoAndDelYnAndLstYn(item.orcPkVl(), "N", "Y")) {
                    BigDecimal rate =
                            rateOf(source.getIoeC(), ioeRates, assetRate, costRate, capitalPrefixes);
                    bbugtmRepository.save(
                            newBudget(
                                    bgMngNo,
                                    ++nextSno,
                                    bgYy,
                                    "BCOSTM",
                                    source.getCostBgNo(),
                                    source.getBgSno(),
                                    source.getIoeC(),
                                    calculateDupBg(source.getCostTotXpAmt(), rate),
                                    rate));
                    totalRecords++;
                }
            }
        }
        bbugtmRepository.flush();
        return new BudgetWorkDto.ApplyResponse(
                "사업별 편성률 적용 완료", totalRecords, budgetSummaryService.getSummary(bgYy));
    }

    private Map<String, Bbugtm> existingBudgetMap(String bgYy, String sourceTable) {
        return bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(bgYy, sourceTable, "N").stream()
                .collect(
                        Collectors.toMap(
                                value ->
                                        naturalKey(
                                                value.getPkColNm(),
                                                value.getFntTbCrySno(),
                                                value.getIoeC()),
                                Function.identity(),
                                (first, ignored) -> first));
    }

    private String naturalKey(String pk, Integer sno, String ioeC) {
        return pk + "|" + sno + "|" + ioeC;
    }

    private Bbugtm newBudget(
            String bgNo,
            int sno,
            String bgYy,
            String sourceTable,
            String sourcePk,
            Integer sourceSno,
            String ioeC,
            BigDecimal amount,
            BigDecimal rate) {
        return Bbugtm.builder()
                .bgNo(bgNo)
                .sno(sno)
                .bseYy(bgYy)
                .fntTbNm(sourceTable)
                .pkColNm(sourcePk)
                .fntTbCrySno(sourceSno)
                .ioeC(ioeC)
                .bgDupAmt(amount)
                .asgRt(rate)
                .build();
    }

    private boolean isCapitalIoeCode(String ioeC, Set<String> capitalPrefixes) {
        if (ioeC == null) return false;
        return capitalPrefixes.stream().anyMatch(ioeC::startsWith);
    }

    /**
     * 이 비목에 적용할 편성률을 정합니다.
     *
     * <p>비목별 편성률이 지정돼 있으면 그것이 이깁니다. 지정되지 않은 비목만 자본·일반 2버킷으로 떨어지므로,
     * 종합본이 일부 비목만 채워 보내도 나머지가 조용히 0이 되지 않습니다.
     *
     * @param ioeC 품목·전산업무비의 비목코드 (null 허용)
     * @param ioeRates 비목별 편성률. 비어 있으면 2버킷만 씁니다
     * @param assetRate 자본예산 계열 기본 편성률
     * @param costRate 그 밖의 기본 편성률
     * @param capitalPrefixes 자본예산 계열 판정용 접두어 집합
     * @return 적용할 편성률
     */
    private BigDecimal rateOf(
            String ioeC,
            Map<String, BigDecimal> ioeRates,
            BigDecimal assetRate,
            BigDecimal costRate,
            Set<String> capitalPrefixes) {
        if (ioeC != null) {
            BigDecimal explicit = ioeRates.get(ioeC.trim());
            if (explicit != null) {
                return explicit;
            }
        }
        return isCapitalIoeCode(ioeC, capitalPrefixes) ? assetRate : costRate;
    }

    /**
     * 예산관리번호를 채번합니다.
     *
     * <p>형식은 {@code BG-{예산년도}-{4자리 시퀀스}}(예: {@code BG-2026-0001})이며, 시퀀스가 9,999를 넘으면 잘리지 않고 자릿수가
     * 늘어납니다({@code BG-2026-10000}). 종전에는 리포지토리의 네이티브 쿼리가 Oracle {@code LPAD(NEXTVAL, 4, '0')}으로
     * 번호까지 만들었는데, {@code LPAD}는 초과분을 **잘라내** 기존 번호와 조용히 충돌한다(BE-28). 다른 14개 채번과 같이 Java {@code
     * String.format}으로 옮겨 그 경로를 없앴다.
     *
     * @param bgYy 예산년도
     * @return 채번된 예산관리번호
     */
    private String generateBgMngNo(String bgYy) {
        return String.format("BG-%s-%04d", bgYy, bbugtmRepository.nextBgMngNoSeq());
    }

    /**
     * 요청금액에 편성률을 적용해 편성금액을 계산합니다.
     *
     * @param requestAmount 요청금액. null이면 0원
     * @param rate 편성률(0~100, 소수 허용). null이면 0원
     * @return 편성금액. 물리 컬럼 스케일(3)로 반올림합니다
     */
    private BigDecimal calculateDupBg(BigDecimal requestAmount, BigDecimal rate) {
        if (requestAmount == null || rate == null) return BigDecimal.ZERO;
        return requestAmount.multiply(rate).divide(PERCENT_BASE, 3, RoundingMode.HALF_UP);
    }
}
