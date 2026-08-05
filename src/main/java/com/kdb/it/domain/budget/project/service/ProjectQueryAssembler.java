package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 정보화사업 단건·목록·일괄 응답을 조립합니다. */
@Component
public class ProjectQueryAssembler {

    private final ApplicationMapRepository applicationMapRepository;
    private final ApplicationRepository applicationRepository;
    private final ProjectItemRepository itemRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ApproverRepository approverRepository;
    private final CodeRepository codeRepository;
    private final ProjectBudgetSummaryService budgetSummaryService;
    private final BprojaRepository bprojaRepository;
    private final ProjectBatchAssembler batchAssembler;

    public ProjectQueryAssembler(
            ApplicationMapRepository applicationMapRepository,
            ApplicationRepository applicationRepository,
            ProjectItemRepository itemRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            ApproverRepository approverRepository,
            CodeRepository codeRepository,
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
        this.codeRepository = codeRepository;
        this.budgetSummaryService = budgetSummaryService;
        this.bprojaRepository = bprojaRepository;
        this.batchAssembler =
                new ProjectBatchAssembler(
                        applicationMapRepository,
                        applicationRepository,
                        itemRepository,
                        organizationRepository,
                        userRepository,
                        approverRepository,
                        budgetRepository,
                        codeService,
                        budgetSummaryService,
                        bprojaRepository,
                        codeNameMapBuilder,
                        projectRepository);
    }

    /**
     * 사업 목록의 신청서·조직·사용자·코드·예산 정보를 배치로 조립합니다.
     *
     * @param projects 활성 프로젝트 목록
     * @return 입력 순서와 같은 응답 목록
     */
    public List<ProjectDto.Response> assembleList(List<Bprojm> projects) {
        return batchAssembler.assembleList(projects, this::enrichItemIoeNames);
    }

    /**
     * 사업 단건의 신청서·조직·사용자·코드·단계·품목 정보를 조립합니다.
     *
     * @param project 활성 프로젝트
     * @return 상세 응답
     */
    public ProjectDto.Response assembleDetail(Bprojm project) {
        ProjectDto.Response response = ProjectDto.Response.fromEntity(project);
        applyApplication(response, project.getAbusMngNo(), project.getSno());
        if (project.getSvnDpmNm() != null) {
            response.setSvnDpmCNm(project.getSvnDpmNm());
        }
        applyNames(response);
        List<Bproja> steps = bprojaRepository.findByAbusMngNoAndDelYn(project.getAbusMngNo(), "N");
        response.setStsTc(representativeStatus(steps));
        response.setBprojaStsCodes(
                steps.stream().map(Bproja::getStsTc).filter(java.util.Objects::nonNull).toList());
        List<Bitemm> items =
                itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                        project.getAbusMngNo(), project.getSno(), "N");
        List<ProjectDto.BitemmDto> itemDtos =
                items.stream().map(ProjectDto.BitemmDto::fromEntity).toList();
        enrichItemIoeNames(itemDtos);
        response.setItems(itemDtos);
        budgetSummaryService.applyBudgetSummary(response, items);
        return response;
    }

    /**
     * 일괄 조회 응답의 신청서·품목·대표상태·편성예산 정보를 배치로 조립합니다.
     *
     * @param projects 활성 프로젝트 목록
     * @param budgetYear 편성예산 기준연도
     * @return 입력 순서와 같은 상세 응답 목록
     */
    public List<ProjectDto.Response> assembleBulk(List<Bprojm> projects, String budgetYear) {
        return batchAssembler.assembleBulk(projects, budgetYear, this::enrichItemIoeNames);
    }

    private void applyApplication(
            ProjectDto.Response response, String projectId, Integer projectSequence) {
        List<ApplicationMapRepository.ApplicationMapView> applicationMaps =
                applicationMapRepository
                        .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                "BPROJM", projectId, projectSequence);
        if (applicationMaps.isEmpty()) {
            return;
        }
        ApplicationMapRepository.ApplicationMapView applicationMap = applicationMaps.get(0);
        response.setApfMngNo(applicationMap.getApfDcmNo());
        applicationRepository
                .findSummaryViewsByApfMngNoIn(List.of(applicationMap.getApfDcmNo()))
                .stream()
                .findFirst()
                .ifPresent(
                        application -> {
                            response.setApfSts(
                                    application.getItPtlApfPrgStsC() == null
                                            ? null
                                            : ApprovalStatus.ofCode(
                                                            application.getItPtlApfPrgStsC())
                                                    .label());
                            response.setApplicationInfo(
                                    ApplicationInfoDto.fromReadViews(
                                            application,
                                            approverRepository
                                                    .findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc(
                                                            applicationMap.getApfDcmNo())));
                        });
    }

    private void applyNames(ProjectDto.Response response) {
        if (hasText(response.getDvmDpmC())) {
            organizationRepository
                    .findNameViewByPrlmOgzCCone(response.getDvmDpmC())
                    .ifPresent(view -> response.setDvmDpmCNm(view.getBbrNm()));
        }
        if (response.getSvnDpmCNm() == null && hasText(response.getSvnDpmC())) {
            organizationRepository
                    .findNameViewByPrlmOgzCCone(response.getSvnDpmC())
                    .ifPresent(view -> response.setSvnDpmCNm(view.getBbrNm()));
        }
        applyUserName(response.getDvmUsid(), response::setDvmUsidNm, response::setDvmUsidPtCNm);
        applyUserName(response.getTlrUsid(), response::setTlrUsidNm, response::setTlrUsidPtCNm);
        applyUserName(response.getUsid(), response::setUsidNm, response::setUsidPtCNm);
        applyUserName(
                response.getDvmTlrUsid(), response::setDvmTlrUsidNm, response::setDvmTlrUsidPtCNm);
        response.setBzTpCNm(response.getBzTpC());
        response.setBzDttNmNm(response.getBzDttNm());
        response.setSklTpTcNm(response.getSklTpTc());
        response.setCstTpTcNm(response.getCstTpTc());
        applyCodeName(CommonCodeGroups.REPORT_STS, response.getRprStsTc(), response::setRprStsTcNm);
        applyCodeName(
                CommonCodeGroups.EXE_POSSIBLE, response.getExePttYn(), response::setExePttYnNm);
        applyCodeName(CommonCodeGroups.ABUS, response.getAbusTc(), response::setAbusTcNm);
        applyCodeName(CommonCodeGroups.EDRT, response.getEdrtTc(), response::setEdrtTcNm);
    }

    private void applyUserName(
            String userId, Consumer<String> nameSetter, Consumer<String> positionSetter) {
        if (!hasText(userId)) {
            return;
        }
        userRepository
                .findNameViewByEno(userId)
                .ifPresent(
                        view -> {
                            nameSetter.accept(view.getUsrNm());
                            positionSetter.accept(view.getPtCNm());
                        });
    }

    private void applyCodeName(String group, String value, Consumer<String> setter) {
        if (!hasText(value)) {
            return;
        }
        codeRepository
                .findByCIdAndCdvaWithValidDate(group, value, null)
                .ifPresent(code -> setter.accept(code.getCdvaNm()));
    }

    static String representativeStatus(List<Bproja> rows) {
        return rows.stream()
                .map(Bproja::getStsTc)
                .filter(ProjectQueryAssembler::hasText)
                .max(java.util.Comparator.naturalOrder())
                .orElse(null);
    }

    private void enrichItemIoeNames(List<ProjectDto.BitemmDto> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<String> itemCodes =
                items.stream()
                        .map(ProjectDto.BitemmDto::getIoeC)
                        .filter(ProjectQueryAssembler::hasText)
                        .collect(Collectors.toSet());
        if (itemCodes.isEmpty()) {
            return;
        }
        Map<String, String> names = buildItemCodeNames(itemCodes);
        for (ProjectDto.BitemmDto item : items) {
            if (item.getIoeC() != null) {
                item.setIoeCNm(names.get(item.getIoeC()));
            }
        }
    }

    private Map<String, String> buildItemCodeNames(Set<String> itemCodes) {
        Map<String, List<String>> valuesByGroup = new HashMap<>();
        for (String itemCode : itemCodes) {
            String normalized = itemCode.replace('-', '_');
            int separator = normalized.lastIndexOf('_');
            String group =
                    separator >= 0 ? normalized.substring(0, separator) : CommonCodeGroups.IOE;
            valuesByGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(itemCode);
        }
        Map<String, String> names = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : valuesByGroup.entrySet()) {
            for (Ccodem code : codeRepository.findByCIdWithValidDate(entry.getKey(), null)) {
                for (String original : entry.getValue()) {
                    String normalized = original.replace('-', '_');
                    int separator = normalized.lastIndexOf('_');
                    String value =
                            separator >= 0 ? normalized.substring(separator + 1) : normalized;
                    if (value.equals(code.getCdva())) {
                        String displayName =
                                code.getCdvaNm() != null ? code.getCdvaNm() : code.getCdvaDtl();
                        if (displayName != null) {
                            String[] parts = displayName.split(" - ");
                            names.put(original, parts[parts.length - 1].trim());
                        }
                    }
                }
            }
        }
        return names;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    @FunctionalInterface
    private interface Consumer<T> {
        void accept(T value);
    }
}
