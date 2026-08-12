package com.kdb.it.domain.budget.work.service;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostRepresentativeSelector;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.ItemRepresentativeSelector;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.domain.budget.work.repository.BudgetReadView;
import com.kdb.it.domain.budget.work.repository.BudgetWorkQueryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 원본 품목·비용을 사업 단위로 묶어 편성 결과를 조립합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetProjectSummaryService {

    private static final BigDecimal PERCENT_BASE = BigDecimal.valueOf(100);

    private final BbugtmRepository bbugtmRepository;
    private final BudgetWorkQueryRepository budgetWorkQueryRepository;
    private final ProjectRepository projectRepository;
    private final ProjectItemRepository projectItemRepository;
    private final CostRepository costRepository;
    private final BudgetIoeCatalog ioeCatalog;

    /**
     * 예산연도 편성 결과를 사업·비용별로 조회합니다.
     *
     * @param bgYy 예산연도
     * @return 비목 헤더와 사업별 금액 요약
     */
    public BudgetWorkDto.ProjectSummaryResponse getProjectSummary(String bgYy) {
        List<Ccodem> duplicateCodes = ioeCatalog.findCodes("DUP_IOE");
        List<BudgetReadView> budgets =
                filterByApprovedSource(
                        bbugtmRepository.findReadViewsByBseYyAndDelYn(bgYy, "N"), bgYy);
        List<Ccodem> detailCodes = ioeCatalog.findCodes(CommonCodeGroups.IOE);
        Map<String, String> hierarchyByIoe = new LinkedHashMap<>();
        Map<String, Boolean> capitalByIoe = new LinkedHashMap<>();
        for (Ccodem code : detailCodes) {
            hierarchyByIoe.put(code.getCdva(), code.getCdvaDtlC());
            capitalByIoe.put(code.getCdva(), ioeCatalog.isCapitalCTp(code.getCTp()));
        }

        Map<String, List<BudgetReadView>> budgetsByPrefix = new LinkedHashMap<>();
        for (BudgetReadView budget : budgets) {
            if (budget.getIoeC() == null || budget.getAsgRt() == null) continue;
            String hierarchy = hierarchyByIoe.get(budget.getIoeC());
            if (hierarchy == null) continue;
            for (Ccodem code : duplicateCodes) {
                String prefix = ioeCatalog.extractPrefix(code.getCdva());
                if (hierarchy.startsWith(prefix)) {
                    budgetsByPrefix
                            .computeIfAbsent(prefix, ignored -> new ArrayList<>())
                            .add(budget);
                    break;
                }
            }
        }
        Map<String, Integer> rateByPrefix = new LinkedHashMap<>();
        budgetsByPrefix.forEach(
                (prefix, values) ->
                        rateByPrefix.put(
                                prefix, BudgetRepresentativeSelector.pickView(values).getAsgRt()));
        List<BudgetWorkDto.ProjectSummaryCategory> headers = new ArrayList<>();
        for (Ccodem code : duplicateCodes) {
            String prefix = ioeCatalog.extractPrefix(code.getCdva());
            headers.add(
                    new BudgetWorkDto.ProjectSummaryCategory(
                            prefix,
                            resolveCategoryName(prefix, code, detailCodes),
                            code.getCdvaDes(),
                            rateByPrefix.getOrDefault(prefix, 0)));
        }

        Set<String> itemPks =
                budgets.stream()
                        .filter(
                                budget ->
                                        "BITEMM".equals(budget.getFntTbNm())
                                                && budget.getPkColNm() != null)
                        .map(BudgetReadView::getPkColNm)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Bitemm> itemByPk = findRepresentativeItems(itemPks);
        Map<String, String> itemToProject = new LinkedHashMap<>();
        itemByPk.forEach((key, item) -> itemToProject.put(key, item.getAbusMngNo()));

        Map<String, BigDecimal> mplFactorByGroup =
                computeMplFactors(budgets, itemByPk, capitalByIoe);
        Map<SourceKey, Map<String, BigDecimal[]>> amountsBySource = new LinkedHashMap<>();
        for (BudgetReadView budget : budgets) {
            if (budget.getPkColNm() == null) continue;
            Bitemm sourceItem = null;
            SourceKey sourceKey;
            if ("BITEMM".equals(budget.getFntTbNm())) {
                sourceItem = itemByPk.get(budget.getPkColNm());
                sourceKey =
                        new SourceKey(
                                "BPROJM",
                                itemToProject.getOrDefault(
                                        budget.getPkColNm(), budget.getPkColNm()));
            } else {
                sourceKey = new SourceKey(budget.getFntTbNm(), budget.getPkColNm());
            }
            // computeIfAbsent 반환값을 그대로 사용해 같은 키를 다시 조회하지 않는다.
            Map<String, BigDecimal[]> amountsByPrefix =
                    amountsBySource.computeIfAbsent(sourceKey, ignored -> new LinkedHashMap<>());
            String prefix = matchPrefix(budget.getIoeC(), hierarchyByIoe, duplicateCodes);
            if (prefix == null) continue;
            BigDecimal[] amounts =
                    amountsByPrefix.computeIfAbsent(
                            prefix, ignored -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal requestAmount = reverseRequestAmount(budget);
            BigDecimal budgetAmount =
                    budget.getBgDupAmt() != null ? budget.getBgDupAmt() : BigDecimal.ZERO;
            if (sourceItem != null && sourceItem.getAbusMngNo() != null) {
                boolean capital = Boolean.TRUE.equals(capitalByIoe.get(budget.getIoeC()));
                BigDecimal factor = mplFactorByGroup.get(sourceItem.getAbusMngNo() + "|" + capital);
                if (factor != null) {
                    requestAmount = requestAmount.subtract(requestAmount.multiply(factor));
                    budgetAmount = budgetAmount.subtract(budgetAmount.multiply(factor));
                    if (requestAmount.signum() < 0) requestAmount = BigDecimal.ZERO;
                    if (budgetAmount.signum() < 0) budgetAmount = BigDecimal.ZERO;
                }
            }
            amounts[0] = amounts[0].add(requestAmount);
            amounts[1] = amounts[1].add(budgetAmount);
        }

        Map<String, String> projectNames = findProjectNames(amountsBySource.keySet());
        Map<String, String> costNames = findCostNames(amountsBySource.keySet());
        List<BudgetWorkDto.ProjectSummaryItem> items = new ArrayList<>();
        BigDecimal totalRequest = BigDecimal.ZERO;
        BigDecimal totalBudget = BigDecimal.ZERO;
        for (Map.Entry<SourceKey, Map<String, BigDecimal[]>> entry : amountsBySource.entrySet()) {
            SourceKey source = entry.getKey();
            String name = resolveSourceName(source, projectNames, costNames);
            Map<String, BudgetWorkDto.CategoryAmount> categoryAmounts = new LinkedHashMap<>();
            BigDecimal sourceRequest = BigDecimal.ZERO;
            BigDecimal sourceBudget = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal[]> category : entry.getValue().entrySet()) {
                BigDecimal[] amounts = category.getValue();
                categoryAmounts.put(
                        category.getKey(),
                        new BudgetWorkDto.CategoryAmount(amounts[0], amounts[1]));
                sourceRequest = sourceRequest.add(amounts[0]);
                sourceBudget = sourceBudget.add(amounts[1]);
            }
            items.add(
                    new BudgetWorkDto.ProjectSummaryItem(
                            source.pkValue(),
                            source.table(),
                            name,
                            sourceRequest,
                            sourceBudget,
                            categoryAmounts));
            totalRequest = totalRequest.add(sourceRequest);
            totalBudget = totalBudget.add(sourceBudget);
        }
        return new BudgetWorkDto.ProjectSummaryResponse(
                headers, items, new BudgetWorkDto.SummaryTotals(totalRequest, totalBudget));
    }

    private Map<String, Bitemm> findRepresentativeItems(Set<String> itemPks) {
        Map<String, List<Bitemm>> historiesByPk = new LinkedHashMap<>();
        if (!itemPks.isEmpty()) {
            for (Bitemm item : projectItemRepository.findByGclMngNoInAndDelYn(itemPks, "N")) {
                historiesByPk
                        .computeIfAbsent(item.getGclMngNo(), ignored -> new ArrayList<>())
                        .add(item);
            }
        }
        Map<String, Bitemm> result = new LinkedHashMap<>();
        historiesByPk.forEach(
                (key, values) -> result.put(key, ItemRepresentativeSelector.pick(values)));
        return result;
    }

    private Map<String, BigDecimal> computeMplFactors(
            List<BudgetReadView> budgets,
            Map<String, Bitemm> itemByPk,
            Map<String, Boolean> capitalByIoe) {
        Map<String, List<BudgetReadView>> budgetsByItem = new LinkedHashMap<>();
        for (BudgetReadView budget : budgets) {
            if ("BITEMM".equals(budget.getFntTbNm())
                    && budget.getPkColNm() != null
                    && budget.getIoeC() != null) {
                budgetsByItem
                        .computeIfAbsent(budget.getPkColNm(), ignored -> new ArrayList<>())
                        .add(budget);
            }
        }
        Map<String, BigDecimal> requests = new LinkedHashMap<>();
        Map<String, BigDecimal> planned = new LinkedHashMap<>();
        for (Map.Entry<String, List<BudgetReadView>> entry : budgetsByItem.entrySet()) {
            Bitemm item = itemByPk.get(entry.getKey());
            if (item == null || item.getAbusMngNo() == null) continue;
            BudgetReadView representative = BudgetRepresentativeSelector.pickView(entry.getValue());
            String key =
                    item.getAbusMngNo()
                            + "|"
                            + Boolean.TRUE.equals(capitalByIoe.get(representative.getIoeC()));
            requests.merge(
                    key, item.getAmt() != null ? item.getAmt() : BigDecimal.ZERO, BigDecimal::add);
            planned.merge(
                    key,
                    item.getMplAmt() != null ? item.getMplAmt() : BigDecimal.ZERO,
                    BigDecimal::add);
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : requests.entrySet()) {
            BigDecimal request = entry.getValue();
            BigDecimal mpl = planned.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (request.signum() <= 0 || mpl.signum() <= 0) continue;
            result.put(
                    entry.getKey(),
                    mpl.compareTo(request) >= 0
                            ? BigDecimal.ONE
                            : mpl.divide(request, 10, RoundingMode.HALF_UP));
        }
        return result;
    }

    private Map<String, String> findProjectNames(Set<SourceKey> sourceKeys) {
        Set<String> projectNos = sourceValues(sourceKeys, "BPROJM");
        Map<String, String> result = new LinkedHashMap<>();
        if (!projectNos.isEmpty()) {
            projectRepository
                    .findKeyViewsByAbusMngNoInAndLstYnAndDelYn(projectNos, "Y", "N")
                    .forEach(view -> result.put(view.getAbusMngNo(), view.getAbusNm()));
        }
        return result;
    }

    private Map<String, String> findCostNames(Set<SourceKey> sourceKeys) {
        Set<String> costNos = sourceValues(sourceKeys, "BCOSTM");
        Map<String, String> result = new LinkedHashMap<>();
        if (!costNos.isEmpty()) {
            costRepository.findRepresentativeViewsByCostBgNoInAndDelYn(costNos, "N").stream()
                    .collect(
                            Collectors.groupingBy(
                                    history -> history.getCostBgNo(),
                                    LinkedHashMap::new,
                                    Collectors.toList()))
                    .forEach(
                            (key, histories) ->
                                    result.put(
                                            key,
                                            CostRepresentativeSelector.pickView(histories)
                                                    .getCttNm()));
        }
        return result;
    }

    private Set<String> sourceValues(Set<SourceKey> keys, String table) {
        return keys.stream()
                .filter(key -> table.equals(key.table()))
                .map(SourceKey::pkValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveSourceName(
            SourceKey source, Map<String, String> projectNames, Map<String, String> costNames) {
        if ("BPROJM".equals(source.table())) {
            return projectNames.getOrDefault(source.pkValue(), source.pkValue());
        }
        if ("BCOSTM".equals(source.table())) {
            return costNames.containsKey(source.pkValue())
                    ? costNames.get(source.pkValue())
                    : source.pkValue();
        }
        return source.pkValue();
    }

    private String matchPrefix(
            String ioeC, Map<String, String> hierarchyByIoe, List<Ccodem> duplicateCodes) {
        String hierarchy = ioeC != null ? hierarchyByIoe.get(ioeC) : null;
        if (hierarchy == null) return null;
        for (Ccodem code : duplicateCodes) {
            String prefix = ioeCatalog.extractPrefix(code.getCdva());
            if (hierarchy.startsWith(prefix)) return prefix;
        }
        return null;
    }

    private BigDecimal reverseRequestAmount(BudgetReadView budget) {
        if (budget.getBgDupAmt() == null || budget.getAsgRt() == null || budget.getAsgRt() <= 0)
            return BigDecimal.ZERO;
        return budget.getBgDupAmt()
                .multiply(PERCENT_BASE)
                .divide(BigDecimal.valueOf(budget.getAsgRt()), 2, RoundingMode.HALF_UP);
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

    private String resolveCategoryName(
            String prefix, Ccodem duplicateCode, List<Ccodem> detailCodes) {
        for (Ccodem detail : detailCodes) {
            if (detail.getCdvaDtlC() != null && detail.getCdvaDtlC().startsWith(prefix)) {
                String groupName = ioeCatalog.resolveGroupName(detail);
                if (groupName != null && !groupName.isBlank()) return groupName;
            }
        }
        if (duplicateCode.getCdvaNm() != null && !duplicateCode.getCdvaNm().isBlank()) {
            return duplicateCode.getCdvaNm();
        }
        if (duplicateCode.getCNm() != null && !duplicateCode.getCNm().isBlank()) {
            return duplicateCode.getCNm();
        }
        if (duplicateCode.getCdvaDes() != null && !duplicateCode.getCdvaDes().isBlank()) {
            return duplicateCode.getCdvaDes();
        }
        return prefix;
    }

    private record SourceKey(String table, String pkValue) {}
}
