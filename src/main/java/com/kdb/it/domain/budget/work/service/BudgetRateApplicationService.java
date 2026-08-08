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

    private static final int DEFAULT_DUP_RT = 100;
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
            Integer dupRt = rate.dupRt();
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
            int assetRate = item.assetDupRt() != null ? item.assetDupRt() : DEFAULT_DUP_RT;
            int costRate = item.costDupRt() != null ? item.costDupRt() : DEFAULT_DUP_RT;
            if ("BPROJM".equals(item.orcTb())) {
                for (Bitemm source :
                        projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(
                                item.orcPkVl(), "N", "Y")) {
                    int rate =
                            isCapitalIoeCode(source.getIoeC(), capitalPrefixes)
                                    ? assetRate
                                    : costRate;
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
                    int rate =
                            isCapitalIoeCode(source.getIoeC(), capitalPrefixes)
                                    ? assetRate
                                    : costRate;
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
            Integer rate) {
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

    private BigDecimal calculateDupBg(BigDecimal requestAmount, Integer rate) {
        if (requestAmount == null || rate == null) return BigDecimal.ZERO;
        return requestAmount
                .multiply(BigDecimal.valueOf(rate))
                .divide(PERCENT_BASE, 2, RoundingMode.HALF_UP);
    }
}
