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
import com.kdb.it.common.util.UserNameResolver;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.dto.ProjectResponseMapper;
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
                projects.stream().map(ProjectResponseMapper::fromEntity).toList();
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
            budgetSummaryService.applyStoredAmountSnapshot(
                    response, project.getTotRqmAmt(), project.getMplAmt(), project.getDfrAmt());
        }
        return responses;
    }

    List<ProjectDto.Response> assembleBulk(
            List<Bprojm> projects,
            String budgetYear,
            Consumer<List<ProjectDto.BitemmDto>> itemNameEnricher) {
        List<ProjectDto.Response> responses =
                projects.stream().map(ProjectResponseMapper::fromEntity).toList();
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
            budgetSummaryService.applyStoredAmountSnapshot(
                    response, project.getTotRqmAmt(), project.getMplAmt(), project.getDfrAmt());
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
                response.setApfStsC(application.getItPtlApfPrgStsC());
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
        response.setStsTc(
                ProjectQueryAssembler.representativeStatus(steps, project.getAbusMngNo()));
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

    /**
     * 담당자 사번 일괄 조회 결과로 사용자명·직위명을 채웁니다.
     *
     * <p>담당자 컬럼은 사번 또는 이름을 담으므로, 사번 조회가 비면 {@link UserNameResolver}가 저장값 자체를 이름으로 사용할지 판정합니다. 직위명은
     * 사번 조회가 성공한 경우에만 채웁니다.
     *
     * <p>행번이 비어 있으면 조인 해석을 건너뛰고, 해석에 실패해도 null로 덮지 않습니다 — 응답 초기값으로 실린 저장 스냅샷 이름(USR_NM·TLR_NM)을
     * 유지해 행번 미해석 행의 이름이 화면에서 사라지지 않게 합니다.
     */
    private static void applyUserNames(
            ProjectDto.Response response,
            Map<String, String> names,
            Map<String, String> positions) {
        applyUserName(
                names,
                positions,
                response.getDvmUsid(),
                response::setDvmUsid,
                response::setDvmUsidNm,
                response::setDvmUsidPtCNm);
        applyUserName(
                names,
                positions,
                response.getTlrUsid(),
                response::setTlrUsid,
                response::setTlrUsidNm,
                response::setTlrUsidPtCNm);
        applyUserName(
                names,
                positions,
                response.getUsid(),
                response::setUsid,
                response::setUsidNm,
                response::setUsidPtCNm);
        applyUserName(
                names,
                positions,
                response.getDvmTlrUsid(),
                response::setDvmTlrUsid,
                response::setDvmTlrUsidNm,
                response::setDvmTlrUsidPtCNm);
    }

    /**
     * 담당자 한 명의 사용자명·직위명 해석을 적용합니다.
     *
     * @param names 사번→사용자명 일괄 조회 결과
     * @param positions 사번→직위명 일괄 조회 결과
     * @param userId 담당자 컬럼 저장값 (빈값이면 스냅샷 유지를 위해 아무것도 하지 않음)
     * @param idSetter 저장값이 이름으로 판정되면 행번을 비우는 setter
     * @param nameSetter 해석된 표시명 setter (해석 실패 시 호출하지 않음 — 스냅샷 유지)
     * @param positionSetter 직위명 setter (사번 조회 성공 시에만 값 존재)
     */
    private static void applyUserName(
            Map<String, String> names,
            Map<String, String> positions,
            String userId,
            java.util.function.Consumer<String> idSetter,
            java.util.function.Consumer<String> nameSetter,
            java.util.function.Consumer<String> positionSetter) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String lookedUpName = getOrNull(names, userId);
        String resolvedName = UserNameResolver.resolve(userId, lookedUpName);
        if (resolvedName != null) {
            nameSetter.accept(resolvedName);
        }
        positionSetter.accept(getOrNull(positions, userId));
        if (UserNameResolver.isStoredName(userId, lookedUpName)) {
            idSetter.accept(null);
        }
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
