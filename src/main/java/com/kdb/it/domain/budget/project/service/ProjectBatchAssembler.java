package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.IoeCategories;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** 목록과 일괄 조회의 연관 데이터를 배치로 조립합니다. */
final class ProjectBatchAssembler {

    private final ApplicationMapRepository applicationMapRepository;
    private final ApplicationRepository applicationRepository;
    private final ProjectItemRepository itemRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ApproverRepository approverRepository;
    private final BbugtmRepository budgetRepository;
    private final CodeService codeService;
    private final ProjectBudgetSummaryService budgetSummaryService;
    private final BprojaRepository bprojaRepository;
    private final CodeNameMapBuilder codeNameMapBuilder;
    private final ProjectRepository projectRepository;

    ProjectBatchAssembler(
            ApplicationMapRepository applicationMapRepository,
            ApplicationRepository applicationRepository,
            ProjectItemRepository itemRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            ApproverRepository approverRepository,
            BbugtmRepository budgetRepository,
            CodeService codeService,
            ProjectBudgetSummaryService budgetSummaryService,
            BprojaRepository bprojaRepository,
            CodeNameMapBuilder codeNameMapBuilder,
            ProjectRepository projectRepository) {
        this.applicationMapRepository = applicationMapRepository;
        this.applicationRepository = applicationRepository;
        this.itemRepository = itemRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.approverRepository = approverRepository;
        this.budgetRepository = budgetRepository;
        this.codeService = codeService;
        this.budgetSummaryService = budgetSummaryService;
        this.bprojaRepository = bprojaRepository;
        this.codeNameMapBuilder = codeNameMapBuilder;
        this.projectRepository = projectRepository;
    }

    List<ProjectDto.Response> assembleList(
            List<Bprojm> projects, Consumer<List<ProjectDto.BitemmDto>> itemNameEnricher) {
        List<ProjectDto.Response> responses =
                projects.stream().map(ProjectDto.Response::fromEntity).toList();
        if (projects.isEmpty()) {
            return responses;
        }
        List<String> projectIds = projects.stream().map(Bprojm::getAbusMngNo).toList();
        BatchData data = loadBatchData(projects, responses, false);
        Map<String, List<ProjectItemRepository.ProjectItemBudgetView>> budgetViews =
                itemRepository.findBudgetViewsByAbusMngNoInAndDelYn(projectIds, "N").stream()
                        .collect(
                                Collectors.groupingBy(
                                        ProjectItemRepository.ProjectItemBudgetView::getAbusMngNo));
        Map<String, String[]> scheduleByProject = new HashMap<>();
        for (Object[] row : projectRepository.findBizplanScheduleRange(projectIds)) {
            String id = toNativeString(row[0]);
            if (id != null) {
                scheduleByProject.put(
                        id, new String[] {toNativeString(row[1]), toNativeString(row[2])});
            }
        }
        for (int index = 0; index < projects.size(); index++) {
            Bprojm project = projects.get(index);
            ProjectDto.Response response = responses.get(index);
            applyCommon(project, response, data, false);
            String[] schedule = scheduleByProject.get(project.getAbusMngNo());
            if (schedule != null) {
                response.setBizplanSttDt(schedule[0]);
                response.setBizplanEndDt(schedule[1]);
            }
            List<Bitemm> items =
                    itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                            project.getAbusMngNo(), project.getSno(), "N");
            if (response.getItems() == null) {
                List<ProjectDto.BitemmDto> itemDtos =
                        items.stream().map(ProjectDto.BitemmDto::fromEntity).toList();
                itemNameEnricher.accept(itemDtos);
                response.setItems(itemDtos);
            }
            budgetSummaryService.applyBudgetSummary(response, items);
            budgetSummaryService.applyBudgetSummaryViews(
                    response, budgetViews.getOrDefault(project.getAbusMngNo(), List.of()));
        }
        return responses;
    }

    List<ProjectDto.Response> assembleBulk(
            List<Bprojm> projects,
            String budgetYear,
            Consumer<List<ProjectDto.BitemmDto>> itemNameEnricher) {
        List<ProjectDto.Response> responses =
                projects.stream().map(ProjectDto.Response::fromEntity).toList();
        if (projects.isEmpty()) {
            return responses;
        }
        BatchData data = loadBatchData(projects, responses, true);
        Map<String, List<Bitemm>> itemsByProject =
                itemRepository
                        .findByAbusMngNoInAndDelYn(
                                projects.stream().map(Bprojm::getAbusMngNo).distinct().toList(),
                                "N")
                        .stream()
                        .collect(Collectors.groupingBy(Bitemm::getAbusMngNo));
        List<ProjectDto.BitemmDto> allItemDtos = new ArrayList<>();
        for (int index = 0; index < projects.size(); index++) {
            Bprojm project = projects.get(index);
            ProjectDto.Response response = responses.get(index);
            applyCommon(project, response, data, true);
            List<Bitemm> items =
                    itemsByProject.getOrDefault(project.getAbusMngNo(), List.of()).stream()
                            .filter(item -> Objects.equals(item.getFntTbCrySno(), project.getSno()))
                            .toList();
            List<ProjectDto.BitemmDto> itemDtos =
                    items.stream().map(ProjectDto.BitemmDto::fromEntity).toList();
            response.setItems(itemDtos);
            allItemDtos.addAll(itemDtos);
            budgetSummaryService.applyBudgetSummary(response, items);
        }
        itemNameEnricher.accept(allItemDtos);
        applyComposedBudgets(responses, budgetYear);
        return responses;
    }

    private BatchData loadBatchData(
            List<Bprojm> projects, List<ProjectDto.Response> responses, boolean keyBySequence) {
        List<String> projectIds = projects.stream().map(Bprojm::getAbusMngNo).distinct().toList();
        Map<String, ApplicationMapRepository.ApplicationMapView> latestApplications =
                new LinkedHashMap<>();
        for (ApplicationMapRepository.ApplicationMapView view :
                applicationMapRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                        "BPROJM", projectIds)) {
            String key =
                    keyBySequence
                            ? view.getPkColNm() + "|" + view.getFntTbCrySno()
                            : view.getPkColNm();
            latestApplications.putIfAbsent(key, view);
        }
        List<String> applicationIds =
                latestApplications.values().stream()
                        .map(ApplicationMapRepository.ApplicationMapView::getApfDcmNo)
                        .distinct()
                        .toList();
        Map<String, ApplicationRepository.ApplicationSummaryView> applications =
                applicationRepository.findSummaryViewsByApfMngNoIn(applicationIds).stream()
                        .collect(
                                Collectors.toMap(
                                        ApplicationRepository.ApplicationSummaryView::getApfMngNo,
                                        java.util.function.Function.identity()));
        Map<String, List<ApproverRepository.ApproverReadView>> decisions =
                approverRepository
                        .findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(applicationIds)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        ApproverRepository.ApproverReadView::getDcdMngNo));
        Set<String> organizationCodes = new HashSet<>();
        Set<String> userIds = new HashSet<>();
        Set<String> reportCodes = new HashSet<>();
        Set<String> executableCodes = new HashSet<>();
        Set<String> businessCodes = new HashSet<>();
        Set<String> editorialCodes = new HashSet<>();
        for (ProjectDto.Response response : responses) {
            addNonBlank(organizationCodes, response.getDvmDpmC());
            addNonBlank(organizationCodes, response.getSvnDpmC());
            addNonBlank(userIds, response.getDvmUsid());
            addNonBlank(userIds, response.getTlrUsid());
            addNonBlank(userIds, response.getUsid());
            addNonBlank(userIds, response.getDvmTlrUsid());
            addNonBlank(reportCodes, response.getRprStsTc());
            addNonBlank(executableCodes, response.getExePttYn());
            addNonBlank(businessCodes, response.getAbusTc());
            addNonBlank(editorialCodes, response.getEdrtTc());
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
        Map<String, List<Bproja>> steps =
                bprojaRepository.findByAbusMngNoInAndDelYn(projectIds, "N").stream()
                        .collect(Collectors.groupingBy(Bproja::getAbusMngNo));
        return new BatchData(
                latestApplications,
                applications,
                decisions,
                organizationNames,
                userNames,
                positions,
                buildCodeNames(CommonCodeGroups.REPORT_STS, reportCodes),
                buildCodeNames(CommonCodeGroups.EXE_POSSIBLE, executableCodes),
                buildCodeNames(CommonCodeGroups.ABUS, businessCodes),
                buildCodeNames(CommonCodeGroups.EDRT, editorialCodes),
                steps);
    }

    private void applyCommon(
            Bprojm project, ProjectDto.Response response, BatchData data, boolean keyBySequence) {
        String applicationKey =
                keyBySequence
                        ? project.getAbusMngNo() + "|" + project.getSno()
                        : project.getAbusMngNo();
        ApplicationMapRepository.ApplicationMapView applicationMap =
                data.latestApplications().get(applicationKey);
        if (applicationMap != null) {
            response.setApfMngNo(applicationMap.getApfDcmNo());
            ApplicationRepository.ApplicationSummaryView application =
                    data.applications().get(applicationMap.getApfDcmNo());
            if (application != null) {
                response.setApfSts(
                        application.getItPtlApfPrgStsC() == null
                                ? null
                                : ApprovalStatus.ofCode(application.getItPtlApfPrgStsC()).label());
                response.setApplicationInfo(
                        ApplicationInfoDto.fromReadViews(
                                application,
                                data.decisions()
                                        .getOrDefault(applicationMap.getApfDcmNo(), List.of())));
            }
        }
        response.setDvmDpmCNm(getOrNull(data.organizationNames(), response.getDvmDpmC()));
        response.setSvnDpmCNm(
                project.getSvnDpmNm() != null
                        ? project.getSvnDpmNm()
                        : getOrNull(data.organizationNames(), response.getSvnDpmC()));
        applyUserNames(response, data.userNames(), data.positions());
        response.setBzTpCNm(response.getBzTpC());
        response.setBzDttNmNm(response.getBzDttNm());
        response.setSklTpTcNm(response.getSklTpTc());
        response.setCstTpTcNm(response.getCstTpTc());
        response.setRprStsTcNm(getOrNull(data.reportNames(), response.getRprStsTc()));
        response.setExePttYnNm(getOrNull(data.executableNames(), response.getExePttYn()));
        response.setAbusTcNm(getOrNull(data.businessNames(), response.getAbusTc()));
        response.setEdrtTcNm(getOrNull(data.editorialNames(), response.getEdrtTc()));
        List<Bproja> steps = data.steps().getOrDefault(project.getAbusMngNo(), List.of());
        response.setStsTc(ProjectQueryAssembler.representativeStatus(steps));
        if (keyBySequence) {
            response.setBprojaStsCodes(
                    steps.stream().map(Bproja::getStsTc).filter(Objects::nonNull).toList());
        }
    }

    private void applyComposedBudgets(List<ProjectDto.Response> responses, String budgetYear) {
        if (budgetYear == null || budgetYear.isBlank() || responses.isEmpty()) {
            return;
        }
        List<String> projectIds =
                responses.stream().map(ProjectDto.Response::getAbusMngNo).toList();
        Map<String, BigDecimal> totals =
                budgetRepository.sumDupBgByPrjMngNos(projectIds, budgetYear);
        List<com.kdb.it.common.code.entity.Ccodem> ioeCodes =
                codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE);
        Set<String> assetTypes =
                ioeCodes.stream()
                        .filter(code -> IoeCategories.isCapitalCTp(code.getCTp()))
                        .map(com.kdb.it.common.code.entity.Ccodem::getCdva)
                        .collect(Collectors.toSet());
        Set<String> costTypes =
                ioeCodes.stream()
                        .filter(
                                code ->
                                        Set.of("IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE")
                                                .contains(code.getCTp()))
                        .map(com.kdb.it.common.code.entity.Ccodem::getCdva)
                        .collect(Collectors.toSet());
        Map<String, BigDecimal> assets =
                budgetRepository.sumAssetDupBgByPrjMngNos(projectIds, budgetYear, assetTypes);
        Map<String, BigDecimal> costs =
                budgetRepository.sumCostDupBgByPrjMngNos(projectIds, budgetYear, costTypes);
        for (ProjectDto.Response response : responses) {
            String projectId = response.getAbusMngNo();
            response.setDupBgAmt(totals.getOrDefault(projectId, BigDecimal.ZERO));
            response.setAssetDupBg(assets.getOrDefault(projectId, BigDecimal.ZERO));
            response.setCostDupBg(costs.getOrDefault(projectId, BigDecimal.ZERO));
        }
    }

    private static void applyUserNames(
            ProjectDto.Response response,
            Map<String, String> names,
            Map<String, String> positions) {
        response.setDvmUsidNm(getOrNull(names, response.getDvmUsid()));
        response.setDvmUsidPtCNm(getOrNull(positions, response.getDvmUsid()));
        response.setTlrUsidNm(getOrNull(names, response.getTlrUsid()));
        response.setTlrUsidPtCNm(getOrNull(positions, response.getTlrUsid()));
        response.setUsidNm(getOrNull(names, response.getUsid()));
        response.setUsidPtCNm(getOrNull(positions, response.getUsid()));
        response.setDvmTlrUsidNm(getOrNull(names, response.getDvmTlrUsid()));
        response.setDvmTlrUsidPtCNm(getOrNull(positions, response.getDvmTlrUsid()));
    }

    private static String getOrNull(Map<String, String> values, String key) {
        return key == null ? null : values.get(key);
    }

    private Map<String, String> buildCodeNames(String group, Set<String> values) {
        return values.isEmpty() ? Map.of() : codeNameMapBuilder.build(group, values);
    }

    private static void addNonBlank(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private static String toNativeString(Object value) {
        return value == null ? null : value.toString();
    }

    private record BatchData(
            Map<String, ApplicationMapRepository.ApplicationMapView> latestApplications,
            Map<String, ApplicationRepository.ApplicationSummaryView> applications,
            Map<String, List<ApproverRepository.ApproverReadView>> decisions,
            Map<String, String> organizationNames,
            Map<String, String> userNames,
            Map<String, String> positions,
            Map<String, String> reportNames,
            Map<String, String> executableNames,
            Map<String, String> businessNames,
            Map<String, String> editorialNames,
            Map<String, List<Bproja>> steps) {}
}
