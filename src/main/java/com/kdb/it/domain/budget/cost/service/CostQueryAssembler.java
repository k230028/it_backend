package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 전산업무비 단건·목록·일괄 조회 응답의 연관 정보를 조립합니다. */
@Component
@RequiredArgsConstructor
public class CostQueryAssembler {

    private static final Set<String> COST_CTT_TPS =
            Set.of("IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE");
    private static final Set<String> CAPITAL_DETAIL_CTPS = Set.of("IOE_DVC", "IOE_HW", "IOE_SW");

    private final ApplicationMapRepository applicationMapRepository;
    private final ApplicationRepository applicationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ApproverRepository approverRepository;
    private final CodeRepository codeRepository;
    private final BbugtmRepository budgetRepository;
    private final CostRepository costRepository;
    private final CodeNameMapBuilder codeNameMapBuilder;
    private final CostTerminalAssembler terminalAssembler;

    /**
     * 대표 비용 행에 신청서·조직·사용자·코드·단말기·전년도 예산을 조립합니다.
     *
     * @param cost 대표 비용 행
     * @return 상세 응답
     */
    public CostDto.Response assembleDetail(Bcostm cost) {
        CostDto.Response response = CostDto.Response.fromEntity(cost);
        applyApplication(response, cost.getCostBgNo(), cost.getBgSno());
        applySnapshotNames(response, cost);
        applyNames(response);
        applyBudgetCategory(response);
        applyPreviousBudget(response);
        terminalAssembler.attach(response);
        return response;
    }

    /**
     * 비용 목록의 신청서·조직·사용자·코드·단말기·이전예산을 배치 조립합니다.
     *
     * @param costs 활성 비용 행 목록
     * @return 입력 순서와 같은 응답 목록
     */
    public List<CostDto.Response> assembleList(List<Bcostm> costs) {
        List<CostDto.Response> responses =
                costs.stream().map(CostDto.Response::fromEntity).toList();
        if (costs.isEmpty()) {
            return responses;
        }
        BatchData data = loadBatchData(costs, responses);
        for (int index = 0; index < costs.size(); index++) {
            applyBatch(costs.get(index), responses.get(index), data);
        }
        terminalAssembler.attachBatch(costs, responses);
        applyPreviousBudgets(responses);
        return responses;
    }

    /**
     * 일괄 대표행에 상세 연관 정보와 기준연도 편성예산을 조립합니다.
     *
     * @param costs 관리번호별 대표 비용 행
     * @param budgetYear 편성예산 기준연도
     * @return 입력 순서와 같은 상세 응답 목록
     */
    public List<CostDto.Response> assembleBulk(List<Bcostm> costs, String budgetYear) {
        List<CostDto.Response> responses = assembleList(costs);
        applyComposedBudgets(responses, budgetYear);
        return responses;
    }

    private void applyApplication(CostDto.Response response, String costBgNo, Integer bgSno) {
        List<ApplicationMapRepository.ApplicationMapView> applicationMaps =
                applicationMapRepository
                        .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                "BCOSTM", costBgNo, bgSno);
        if (applicationMaps.isEmpty()) {
            return;
        }
        ApplicationMapRepository.ApplicationMapView applicationMap = applicationMaps.getFirst();
        response.setApfMngNo(applicationMap.getApfDcmNo());
        applicationRepository
                .findSummaryViewsByApfMngNoIn(List.of(applicationMap.getApfDcmNo()))
                .stream()
                .findFirst()
                .ifPresent(
                        application -> {
                            response.setApfSts(statusLabel(application.getItPtlApfPrgStsC()));
                            response.setApplicationInfo(
                                    ApplicationInfoDto.fromReadViews(
                                            application,
                                            approverRepository
                                                    .findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc(
                                                            applicationMap.getApfDcmNo())));
                        });
    }

    private BatchData loadBatchData(List<Bcostm> costs, List<CostDto.Response> responses) {
        List<String> costBgNos = costs.stream().map(Bcostm::getCostBgNo).distinct().toList();
        Map<String, ApplicationMapRepository.ApplicationMapView> latestApplications =
                new LinkedHashMap<>();
        for (ApplicationMapRepository.ApplicationMapView applicationMap :
                applicationMapRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                        "BCOSTM", costBgNos)) {
            latestApplications.putIfAbsent(
                    key(applicationMap.getPkColNm(), applicationMap.getFntTbCrySno()),
                    applicationMap);
        }
        List<String> applicationIds =
                latestApplications.values().stream()
                        .map(ApplicationMapRepository.ApplicationMapView::getApfDcmNo)
                        .toList();
        Map<String, ApplicationRepository.ApplicationSummaryView> applications =
                applicationRepository.findSummaryViewsByApfMngNoIn(applicationIds).stream()
                        .collect(
                                Collectors.toMap(
                                        ApplicationRepository.ApplicationSummaryView::getApfMngNo,
                                        value -> value));
        Map<String, List<ApproverRepository.ApproverReadView>> decisions =
                approverRepository
                        .findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(applicationIds)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        ApproverRepository.ApproverReadView::getDcdMngNo));

        Set<String> organizationCodes = new HashSet<>();
        Set<String> userIds = new HashSet<>();
        Set<String> businessUnitCodes = new HashSet<>();
        Set<String> paymentCodes = new HashSet<>();
        Set<String> terminalCodes = new HashSet<>();
        Set<String> businessCodes = new HashSet<>();
        Set<String> itemCodes = new HashSet<>();
        for (CostDto.Response response : responses) {
            addNonBlank(organizationCodes, response.getCostSvnDpmC());
            addNonBlank(organizationCodes, response.getSvnTemC());
            addNonBlank(userIds, response.getCgprId());
            addNonBlank(businessUnitCodes, response.getBgUntAbusC());
            addNonBlank(paymentCodes, response.getDfrCleC());
            if ("Y".equals(response.getTmnYn())) {
                terminalCodes.add("1");
            } else if ("N".equals(response.getTmnYn())) {
                terminalCodes.add("0");
            }
            addNonBlank(businessCodes, response.getAbusTc());
            addNonBlank(itemCodes, response.getIoeC());
        }

        Map<String, String> organizationNames =
                organizationRepository.findNameViewsByPrlmOgzCConeIn(organizationCodes).stream()
                        .collect(
                                Collectors.toMap(
                                        OrganizationRepository.OrganizationNameView
                                                ::getPrlmOgzCCone,
                                        OrganizationRepository.OrganizationNameView::getBbrNm));
        List<UserRepository.UserNameView> userViews = userRepository.findNameViewsByEnoIn(userIds);
        Map<String, String> userNames =
                userViews.stream()
                        .collect(
                                Collectors.toMap(
                                        UserRepository.UserNameView::getEno,
                                        UserRepository.UserNameView::getUsrNm));
        Map<String, String> positions = new HashMap<>();
        for (UserRepository.UserNameView view : userViews) {
            if (view.getPtCNm() != null && !view.getPtCNm().isBlank()) {
                positions.put(view.getEno(), view.getPtCNm());
            }
        }
        return new BatchData(
                latestApplications,
                applications,
                decisions,
                organizationNames,
                userNames,
                positions,
                buildCodeNames(CommonCodeGroups.ABUS_UNIT, businessUnitCodes),
                buildCodeNames(CommonCodeGroups.DFR_CLE, paymentCodes),
                buildCodeNames(CommonCodeGroups.TMN_YN, terminalCodes),
                buildCodeNames(CommonCodeGroups.ABUS, businessCodes),
                buildItemCodeNames(itemCodes));
    }

    private void applyBatch(Bcostm cost, CostDto.Response response, BatchData data) {
        ApplicationMapRepository.ApplicationMapView applicationMap =
                data.latestApplications().get(key(cost.getCostBgNo(), cost.getBgSno()));
        if (applicationMap != null) {
            response.setApfMngNo(applicationMap.getApfDcmNo());
            ApplicationRepository.ApplicationSummaryView application =
                    data.applications().get(applicationMap.getApfDcmNo());
            if (application != null) {
                response.setApfSts(statusLabel(application.getItPtlApfPrgStsC()));
                response.setApplicationInfo(
                        ApplicationInfoDto.fromReadViews(
                                application,
                                data.decisions()
                                        .getOrDefault(applicationMap.getApfDcmNo(), List.of())));
            }
        }
        response.setCostSvnDpmNm(
                cost.getSvnDpmNm() != null
                        ? cost.getSvnDpmNm()
                        : mapValue(data.organizationNames(), response.getCostSvnDpmC()));
        response.setSvnTemNm(
                cost.getSvnTemNm() != null
                        ? cost.getSvnTemNm()
                        : mapValue(data.organizationNames(), response.getSvnTemC()));
        response.setCgprNm(mapValue(data.userNames(), response.getCgprId()));
        response.setCgprPtCNm(mapValue(data.positions(), response.getCgprId()));
        response.setBgUntAbusCNm(mapValue(data.businessUnitNames(), response.getBgUntAbusC()));
        response.setDfrCleCNm(mapValue(data.paymentNames(), response.getDfrCleC()));
        if ("Y".equals(response.getTmnYn())) {
            response.setTmnYnNm(data.terminalNames().get("1"));
        } else if ("N".equals(response.getTmnYn())) {
            response.setTmnYnNm(data.terminalNames().get("0"));
        }
        response.setAbusTcNm(mapValue(data.businessNames(), response.getAbusTc()));
        response.setIoeCNm(mapValue(data.itemNames(), response.getIoeC()));
        applyBudgetCategory(response);
    }

    private void applySnapshotNames(CostDto.Response response, Bcostm cost) {
        if (cost.getSvnDpmNm() != null) {
            response.setCostSvnDpmNm(cost.getSvnDpmNm());
        }
        if (cost.getSvnTemNm() != null) {
            response.setSvnTemNm(cost.getSvnTemNm());
        }
    }

    private void applyNames(CostDto.Response response) {
        if (response.getCostSvnDpmNm() == null && hasText(response.getCostSvnDpmC())) {
            organizationRepository
                    .findNameViewByPrlmOgzCCone(response.getCostSvnDpmC())
                    .ifPresent(value -> response.setCostSvnDpmNm(value.getBbrNm()));
        }
        if (response.getSvnTemNm() == null && hasText(response.getSvnTemC())) {
            organizationRepository
                    .findNameViewByPrlmOgzCCone(response.getSvnTemC())
                    .ifPresent(value -> response.setSvnTemNm(value.getBbrNm()));
        }
        if (hasText(response.getCgprId())) {
            userRepository
                    .findNameViewByEno(response.getCgprId())
                    .ifPresent(
                            value -> {
                                response.setCgprNm(value.getUsrNm());
                                response.setCgprPtCNm(value.getPtCNm());
                            });
        }
        applyCodeName(
                CommonCodeGroups.ABUS_UNIT, response.getBgUntAbusC(), response::setBgUntAbusCNm);
        applyCodeName(CommonCodeGroups.DFR_CLE, response.getDfrCleC(), response::setDfrCleCNm);
        String terminalCode =
                "Y".equals(response.getTmnYn())
                        ? "1"
                        : "N".equals(response.getTmnYn()) ? "0" : null;
        applyCodeName(CommonCodeGroups.TMN_YN, terminalCode, response::setTmnYnNm);
        applyCodeName(CommonCodeGroups.ABUS, response.getAbusTc(), response::setAbusTcNm);
        if (hasText(response.getIoeC())) {
            response.setIoeCNm(
                    buildItemCodeNames(Set.of(response.getIoeC())).get(response.getIoeC()));
        }
    }

    private void applyBudgetCategory(CostDto.Response response) {
        BigDecimal amount =
                response.getCostTotXpAmt() != null ? response.getCostTotXpAmt() : BigDecimal.ZERO;
        response.setAssetBg(BigDecimal.ZERO);
        response.setDvcBg(BigDecimal.ZERO);
        response.setHwBg(BigDecimal.ZERO);
        response.setSwBg(BigDecimal.ZERO);
        response.setCostBg(BigDecimal.ZERO);
        if (!hasText(response.getIoeC())) {
            return;
        }
        Optional<Ccodem> code =
                codeRepository.findByCIdWithValidDate(CommonCodeGroups.IOE, null).stream()
                        .filter(value -> response.getIoeC().equals(value.getCdva()))
                        .findFirst();
        if (code.isEmpty()) {
            return;
        }
        String codeType = code.get().getCTp();
        if (CAPITAL_DETAIL_CTPS.contains(codeType) || "IOE_CPIT".equals(codeType)) {
            response.setAssetBg(amount);
            switch (codeType) {
                case "IOE_DVC" -> response.setDvcBg(amount);
                case "IOE_HW" -> response.setHwBg(amount);
                case "IOE_SW" -> response.setSwBg(amount);
                case "IOE_CPIT" -> applyLegacyCapitalCategory(response, code.get(), amount);
                default -> {
                    // 자본예산 상위 분류는 합계만 유지합니다.
                }
            }
        } else if (COST_CTT_TPS.contains(codeType)) {
            response.setCostBg(amount);
        }
    }

    private static void applyLegacyCapitalCategory(
            CostDto.Response response, Ccodem code, BigDecimal amount) {
        String description = code.getCdvaDes() != null ? code.getCdvaDes() : "";
        if ("단말기".equals(description)) {
            response.setDvcBg(amount);
        } else if ("기계장치".equals(description)) {
            response.setHwBg(amount);
        } else if ("기타무형자산".equals(description)) {
            response.setSwBg(amount);
        }
    }

    private void applyPreviousBudget(CostDto.Response response) {
        response.setPrevBgAmt(BigDecimal.ZERO);
        if (!"20".equals(response.getAbusTc()) || !isYear(response.getBseYy())) {
            return;
        }
        String lookupKey = previousBudgetKey(response);
        if (!hasText(lookupKey)) {
            return;
        }
        String previousYear = previousYear(response.getBseYy());
        response.setPrevBgAmt(
                costRepository
                        .sumPrevBgByCostBgNos(List.of(lookupKey), previousYear)
                        .getOrDefault(lookupKey, BigDecimal.ZERO));
    }

    private void applyPreviousBudgets(List<CostDto.Response> responses) {
        responses.forEach(
                response -> {
                    response.setPrevBgAmt(BigDecimal.ZERO);
                    response.setPrevDupBg(BigDecimal.ZERO);
                });
        Map<String, List<CostDto.Response>> responsesByYear =
                responses.stream()
                        .filter(response -> isYear(response.getBseYy()))
                        .collect(Collectors.groupingBy(CostDto.Response::getBseYy));
        for (Map.Entry<String, List<CostDto.Response>> entry : responsesByYear.entrySet()) {
            String previousYear = previousYear(entry.getKey());
            List<CostDto.Response> yearGroup = entry.getValue();
            List<String> previousBudgetKeys =
                    yearGroup.stream()
                            .filter(response -> "20".equals(response.getAbusTc()))
                            .map(CostQueryAssembler::previousBudgetKey)
                            .filter(CostQueryAssembler::hasText)
                            .distinct()
                            .toList();
            if (!previousBudgetKeys.isEmpty()) {
                Map<String, BigDecimal> previousBudgets =
                        costRepository.sumPrevBgByCostBgNos(previousBudgetKeys, previousYear);
                yearGroup.stream()
                        .filter(response -> "20".equals(response.getAbusTc()))
                        .forEach(
                                response ->
                                        response.setPrevBgAmt(
                                                previousBudgets.getOrDefault(
                                                        previousBudgetKey(response),
                                                        BigDecimal.ZERO)));
            }
            List<String> linkedCostNos =
                    yearGroup.stream()
                            .map(CostDto.Response::getCncdRfrNo)
                            .filter(CostQueryAssembler::hasText)
                            .distinct()
                            .toList();
            if (!linkedCostNos.isEmpty()) {
                Map<String, BigDecimal> previousComposedBudgets =
                        budgetRepository.sumDupBgByItMngcNos(linkedCostNos, previousYear);
                yearGroup.stream()
                        .filter(response -> hasText(response.getCncdRfrNo()))
                        .forEach(
                                response ->
                                        response.setPrevDupBg(
                                                previousComposedBudgets.getOrDefault(
                                                        response.getCncdRfrNo(), BigDecimal.ZERO)));
            }
        }
    }

    private void applyComposedBudgets(List<CostDto.Response> responses, String budgetYear) {
        if (!hasText(budgetYear) || responses.isEmpty()) {
            return;
        }
        Map<String, BigDecimal> composedBudgets =
                budgetRepository.sumDupBgByItMngcNos(
                        responses.stream().map(CostDto.Response::getCostBgNo).toList(), budgetYear);
        responses.forEach(
                response -> {
                    BigDecimal amount =
                            composedBudgets.getOrDefault(response.getCostBgNo(), BigDecimal.ZERO);
                    response.setDupBgAmt(amount);
                    boolean asset =
                            response.getAssetBg() != null
                                    && response.getAssetBg().compareTo(BigDecimal.ZERO) > 0;
                    response.setAssetDupBg(asset ? amount : BigDecimal.ZERO);
                    response.setCostDupBg(asset ? BigDecimal.ZERO : amount);
                });
    }

    private void applyCodeName(
            String group, String code, java.util.function.Consumer<String> setter) {
        if (!hasText(code)) {
            return;
        }
        codeRepository
                .findByCIdAndCdvaWithValidDate(group, code, null)
                .ifPresent(value -> setter.accept(value.getCdvaNm()));
    }

    private Map<String, String> buildCodeNames(String group, Set<String> codes) {
        return codes.isEmpty() ? Map.of() : codeNameMapBuilder.build(group, codes);
    }

    private Map<String, String> buildItemCodeNames(Set<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return codeRepository.findByCIdWithValidDate(CommonCodeGroups.IOE, null).stream()
                .filter(code -> codes.contains(code.getCdva()))
                .collect(
                        Collectors.toMap(
                                Ccodem::getCdva,
                                code -> {
                                    String displayName =
                                            code.getCdvaNm() != null
                                                    ? code.getCdvaNm()
                                                    : code.getCdvaDtl() != null
                                                            ? code.getCdvaDtl()
                                                            : code.getCNm() != null
                                                                    ? code.getCNm()
                                                                    : code.getCdva();
                                    String[] parts = displayName.split(" - ");
                                    return parts[parts.length - 1].trim();
                                },
                                (first, second) -> first));
    }

    private static String statusLabel(String statusCode) {
        return statusCode == null ? null : ApprovalStatus.ofCode(statusCode).label();
    }

    private static String previousBudgetKey(CostDto.Response response) {
        return hasText(response.getCncdRfrNo()) ? response.getCncdRfrNo() : response.getCostBgNo();
    }

    private static String previousYear(String year) {
        return String.valueOf(Integer.parseInt(year) - 1);
    }

    private static boolean isYear(String value) {
        return value != null && value.matches("\\d{4}");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void addNonBlank(Set<String> values, String value) {
        if (hasText(value)) {
            values.add(value);
        }
    }

    private static String mapValue(Map<String, String> values, String key) {
        return key == null ? null : values.get(key);
    }

    private static String key(String costBgNo, Integer bgSno) {
        return costBgNo + "_" + bgSno;
    }

    private record BatchData(
            Map<String, ApplicationMapRepository.ApplicationMapView> latestApplications,
            Map<String, ApplicationRepository.ApplicationSummaryView> applications,
            Map<String, List<ApproverRepository.ApproverReadView>> decisions,
            Map<String, String> organizationNames,
            Map<String, String> userNames,
            Map<String, String> positions,
            Map<String, String> businessUnitNames,
            Map<String, String> paymentNames,
            Map<String, String> terminalNames,
            Map<String, String> businessNames,
            Map<String, String> itemNames) {}
}
