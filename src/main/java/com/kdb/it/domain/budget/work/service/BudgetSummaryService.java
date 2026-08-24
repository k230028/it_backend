package com.kdb.it.domain.budget.work.service;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.domain.budget.work.repository.BudgetReadView;
import com.kdb.it.domain.budget.work.repository.BudgetWorkQueryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비목 목록과 예산연도별 편성 요약을 조립합니다. */
@Service
@Transactional(readOnly = true)
public class BudgetSummaryService {

    private final BbugtmRepository bbugtmRepository;
    private final BudgetWorkQueryRepository budgetWorkQueryRepository;
    private final BudgetIoeCatalog ioeCatalog;

    @Autowired
    public BudgetSummaryService(
            BbugtmRepository bbugtmRepository,
            BudgetWorkQueryRepository budgetWorkQueryRepository,
            BudgetIoeCatalog ioeCatalog) {
        this.bbugtmRepository = bbugtmRepository;
        this.budgetWorkQueryRepository = budgetWorkQueryRepository;
        this.ioeCatalog = ioeCatalog;
    }

    /** 직접 생성하는 기존 테스트와의 생성자 호환성을 유지합니다. */
    @Deprecated(forRemoval = true)
    public BudgetSummaryService(
            BbugtmRepository bbugtmRepository,
            BudgetWorkQueryRepository budgetWorkQueryRepository,
            ProjectRepository ignoredProjectRepository,
            ProjectItemRepository ignoredProjectItemRepository,
            BudgetIoeCatalog ioeCatalog) {
        this(bbugtmRepository, budgetWorkQueryRepository, ioeCatalog);
    }

    /**
     * 편성비목별 요청금액과 최신 편성률을 조회합니다.
     *
     * @param bgYy 예산연도
     * @return 편성비목 목록
     */
    public List<BudgetWorkDto.IoeCategoryResponse> getIoeCategories(String bgYy) {
        List<Ccodem> duplicateCodes = ioeCatalog.findCodes("DUP_IOE");
        List<BudgetReadView> existingBudgets =
                bbugtmRepository.findReadViewsByBseYyAndDelYn(bgYy, "N");
        Map<String, Set<String>> prefixToIoeCodes =
                ioeCatalog.buildPrefixToIoeCValuesMap(ioeCatalog.findCodes(CommonCodeGroups.IOE));
        return duplicateCodes.stream()
                .map(
                        code -> {
                            String prefix = ioeCatalog.extractPrefix(code.getCdva());
                            Set<String> ioeCodes = prefixToIoeCodes.getOrDefault(prefix, Set.of());
                            BigDecimal requestAmount =
                                    bbugtmRepository.sumApprovedAmountByIoeCValues(ioeCodes, bgYy);
                            List<BudgetReadView> candidates =
                                    existingBudgets.stream()
                                            .filter(
                                                    budget ->
                                                            budget.getIoeC() != null
                                                                    && ioeCodes.contains(
                                                                            budget.getIoeC()))
                                            .toList();
                            BigDecimal rate =
                                    candidates.isEmpty()
                                            ? null
                                            : BudgetRepresentativeSelector.pickView(candidates)
                                                    .getAsgRt();
                            return new BudgetWorkDto.IoeCategoryResponse(
                                    code.getCdva(),
                                    code.getCdvaDes() != null ? code.getCdvaDes() : code.getCNm(),
                                    code.getCNm(),
                                    prefix,
                                    rate,
                                    requestAmount != null ? requestAmount : BigDecimal.ZERO);
                        })
                .toList();
    }

    /**
     * 예산연도 전체 편성 결과를 비목별로 집계합니다.
     *
     * @param bgYy 예산연도
     * @return 비목별 편성 요약
     */
    public BudgetWorkDto.SummaryResponse getSummary(String bgYy) {
        return getSummary(bgYy, null);
    }

    /**
     * 선택 원본에 한정한 편성 결과를 비목별로 집계합니다.
     *
     * @param bgYy 예산연도
     * @param srcPks 선택 원본 PK. null 또는 빈 목록이면 전체
     * @return 비목별 편성 요약
     */
    public BudgetWorkDto.SummaryResponse getSummary(String bgYy, List<String> srcPks) {
        List<BudgetReadView> budgets =
                filterByApprovedSource(
                        bbugtmRepository.findReadViewsByBseYyAndDelYn(bgYy, "N"), bgYy);
        if (srcPks != null && !srcPks.isEmpty()) {
            Set<String> selectedPks = new LinkedHashSet<>(srcPks);
            budgets =
                    budgets.stream()
                            .filter(
                                    budget ->
                                            budget.getPkColNm() != null
                                                    && selectedPks.contains(budget.getPkColNm()))
                            .toList();
        }

        List<Ccodem> duplicateCodes = ioeCatalog.findCodes("DUP_IOE");
        List<Ccodem> allIoeCodes = ioeCatalog.findCodes(CommonCodeGroups.IOE);
        Map<String, String> hierarchyByIoe = new LinkedHashMap<>();
        Map<String, String> displayNameByIoe = new LinkedHashMap<>();
        Map<String, String> groupNameByIoe = new LinkedHashMap<>();
        Map<String, Boolean> capitalByIoe = new LinkedHashMap<>();
        for (Ccodem code : allIoeCodes) {
            hierarchyByIoe.put(code.getCdva(), code.getCdvaDtlC());
            displayNameByIoe.put(
                    code.getCdva(),
                    code.getCdvaNm() != null
                            ? code.getCdvaNm()
                            : (code.getCdvaDtl() != null ? code.getCdvaDtl() : code.getCdvaDtlC()));
            groupNameByIoe.put(code.getCdva(), ioeCatalog.resolveGroupName(code));
            capitalByIoe.put(code.getCdva(), ioeCatalog.isCapitalCTp(code.getCTp()));
        }

        Map<String, String> groupNameByPrefix = new LinkedHashMap<>();
        List<String> prefixOrder = new ArrayList<>();
        for (Ccodem code : duplicateCodes) {
            String prefix = ioeCatalog.extractPrefix(code.getCdva());
            groupNameByPrefix.put(
                    prefix, code.getCdvaDes() != null ? code.getCdvaDes() : code.getCNm());
            prefixOrder.add(prefix);
        }
        Map<String, List<BudgetReadView>> budgetsByIoe = new LinkedHashMap<>();
        for (BudgetReadView budget : budgets) {
            if (budget.getIoeC() != null) {
                budgetsByIoe
                        .computeIfAbsent(budget.getIoeC(), ignored -> new ArrayList<>())
                        .add(budget);
            }
        }

        Map<String, BigDecimal> approvedCosts =
                budgetWorkQueryRepository.findApprovedCostAmountByIoeC(bgYy, srcPks);
        Map<String, BigDecimal> approvedItems =
                budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(bgYy, srcPks);

        List<BudgetWorkDto.SummaryItem> responseItems = new ArrayList<>();
        BigDecimal totalRequest = BigDecimal.ZERO;
        BigDecimal totalBudget = BigDecimal.ZERO;
        for (String prefix : prefixOrder) {
            String groupName = groupNameByPrefix.get(prefix);
            List<String> detailCodes =
                    matchingIoeCodes(
                            prefix,
                            hierarchyByIoe,
                            List.of(
                                    budgetsByIoe.keySet(),
                                    approvedCosts.keySet(),
                                    approvedItems.keySet()));
            if (detailCodes.isEmpty()) {
                responseItems.add(
                        new BudgetWorkDto.SummaryItem(
                                groupName,
                                prefix,
                                prefix,
                                groupName,
                                false,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                null));
                continue;
            }

            Map<String, List<String>> codesByDisplayName = new LinkedHashMap<>();
            for (String ioeC : detailCodes) {
                String rawName = displayNameByIoe.getOrDefault(ioeC, ioeC);
                codesByDisplayName
                        .computeIfAbsent(stripGroupPrefix(rawName), ignored -> new ArrayList<>())
                        .add(ioeC);
            }
            for (Map.Entry<String, List<String>> nameEntry : codesByDisplayName.entrySet()) {
                List<String> ioeCodes = nameEntry.getValue();
                List<BudgetReadView> records = new ArrayList<>();
                for (String ioeC : ioeCodes) {
                    records.addAll(budgetsByIoe.getOrDefault(ioeC, List.of()));
                }
                BudgetReadView representative =
                        records.isEmpty() ? null : BudgetRepresentativeSelector.pickView(records);
                String representativeIoe =
                        representative != null ? representative.getIoeC() : ioeCodes.get(0);
                BigDecimal budgetAmount = sumBudgetAmount(records);
                BigDecimal requestAmount = BigDecimal.ZERO;
                for (String ioeC : ioeCodes) {
                    requestAmount =
                            requestAmount
                                    .add(approvedCosts.getOrDefault(ioeC, BigDecimal.ZERO))
                                    .add(approvedItems.getOrDefault(ioeC, BigDecimal.ZERO));
                }
                String itemGroupName = groupNameByIoe.get(representativeIoe);
                if (itemGroupName == null || itemGroupName.isBlank()) itemGroupName = groupName;
                responseItems.add(
                        new BudgetWorkDto.SummaryItem(
                                nameEntry.getKey(),
                                representativeIoe,
                                prefix,
                                itemGroupName,
                                Boolean.TRUE.equals(capitalByIoe.get(representativeIoe)),
                                requestAmount,
                                budgetAmount,
                                representative != null ? representative.getAsgRt() : null));
                totalRequest = totalRequest.add(requestAmount);
                totalBudget = totalBudget.add(budgetAmount);
            }
        }
        return new BudgetWorkDto.SummaryResponse(
                responseItems, new BudgetWorkDto.SummaryTotals(totalRequest, totalBudget));
    }

    private List<String> matchingIoeCodes(
            String prefix, Map<String, String> hierarchyByIoe, List<Set<String>> candidateSets) {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : hierarchyByIoe.entrySet()) {
            if (entry.getValue() != null && entry.getValue().startsWith(prefix)) {
                result.add(entry.getKey());
            }
        }
        for (Set<String> candidates : candidateSets) {
            for (String ioeC : candidates) {
                String hierarchy = hierarchyByIoe.get(ioeC);
                if (hierarchy != null && hierarchy.startsWith(prefix)) result.add(ioeC);
            }
        }
        return new ArrayList<>(result);
    }

    private List<BudgetReadView> filterByApprovedSource(List<BudgetReadView> budgets, String bgYy) {
        Set<String> approvedSourcePks = budgetWorkQueryRepository.findApprovedSourcePks(bgYy);
        if (approvedSourcePks == null || approvedSourcePks.isEmpty()) return budgets;
        return budgets.stream()
                .filter(
                        budget ->
                                budget.getPkColNm() != null
                                        && approvedSourcePks.contains(budget.getPkColNm()))
                .toList();
    }

    private BigDecimal sumBudgetAmount(List<BudgetReadView> budgets) {
        return budgets.stream()
                .map(BudgetReadView::getBgDupAmt)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String stripGroupPrefix(String fullName) {
        int dashIndex = fullName.indexOf(" - ");
        return dashIndex >= 0 ? fullName.substring(dashIndex + 3) : fullName;
    }
}
