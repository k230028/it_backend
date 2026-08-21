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
        ProjectDto.Response response = ProjectResponseMapper.fromEntity(project);
        applyApplication(response, project.getAbusMngNo(), project.getSno());
        if (project.getSvnDpmNm() != null) {
            response.setSvnDpmCNm(project.getSvnDpmNm());
        }
        applyNames(response);
        List<Bproja> steps = bprojaRepository.findByAbusMngNoAndDelYn(project.getAbusMngNo(), "N");
        response.setStsTc(representativeStatus(steps, project.getAbusMngNo()));
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
        applyUserName(
                response.getDvmUsid(),
                response::setDvmUsid,
                response::setDvmUsidNm,
                response::setDvmUsidPtCNm);
        applyUserName(
                response.getTlrUsid(),
                response::setTlrUsid,
                response::setTlrUsidNm,
                response::setTlrUsidPtCNm);
        applyUserName(
                response.getUsid(), response::setUsid, response::setUsidNm, response::setUsidPtCNm);
        applyUserName(
                response.getDvmTlrUsid(),
                response::setDvmTlrUsid,
                response::setDvmTlrUsidNm,
                response::setDvmTlrUsidPtCNm);
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

    /**
     * 담당자 사번으로 사용자명·직위명을 채웁니다.
     *
     * <p>담당자 컬럼은 사번 또는 이름을 담으므로, 사번 조회가 비면 {@link UserNameResolver}가 저장값 자체를 이름으로 사용할지 판정합니다. 직위명은
     * 사용자 조회가 성공한 경우에만 채웁니다.
     */
    private void applyUserName(
            String userId,
            Consumer<String> idSetter,
            Consumer<String> nameSetter,
            Consumer<String> positionSetter) {
        if (!hasText(userId)) {
            return;
        }
        UserRepository.UserNameView view = userRepository.findNameViewByEno(userId).orElse(null);
        if (view != null) {
            positionSetter.accept(view.getPtCNm());
        }
        nameSetter.accept(UserNameResolver.resolve(userId, view == null ? null : view.getUsrNm()));
        if (UserNameResolver.isStoredName(userId, view == null ? null : view.getUsrNm())) {
            idSetter.accept(null);
        }
    }

    private void applyCodeName(String group, String value, Consumer<String> setter) {
        if (!hasText(value)) {
            return;
        }
        codeRepository
                .findByCIdAndCdvaWithValidDate(group, value, null)
                .ifPresent(code -> setter.accept(code.getCdvaNm()));
    }

    /**
     * 사업의 대표상태를 계산합니다.
     *
     * <p>BPROJA는 {@code (ABUS_MNG_NO, CNCD_RFR_NO)} 단위의 <b>단계 문서별</b> 상태 테이블입니다. 사업 자신의 상태는 {@code
     * CNCD_RFR_NO = ABUS_MNG_NO}인 행 하나뿐이고, 나머지는 상위 계획({@code PLN-...})·사업계획({@code BIZ-...}) 등 다른
     * 문서의 상태입니다. 종전에는 행 전체에서 {@code IT_PTL_STS_TC} 최댓값을 취해, 사업 자신은 결재완료('09')인데 상위 계획 행이 '11'이면
     * 대표상태가 '11'로 표시됐습니다(BE-33). 자신의 행만 보도록 좁혔습니다.
     *
     * <p>자신의 행은 사업당 하나지만, 방어적으로 최댓값을 취해 중복이 있어도 결과가 흔들리지 않게 합니다.
     *
     * @param rows 해당 사업의 미삭제 BPROJA 행 전체
     * @param abusMngNo 사업관리번호 — 이 값과 {@code cncdRfrNo}가 같은 행만 대표상태 후보다
     * @return 대표상태 코드. 자신의 행이 없거나 상태가 비어 있으면 null
     */
    static String representativeStatus(List<Bproja> rows, String abusMngNo) {
        return rows.stream()
                .filter(row -> java.util.Objects.equals(row.getCncdRfrNo(), abusMngNo))
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
