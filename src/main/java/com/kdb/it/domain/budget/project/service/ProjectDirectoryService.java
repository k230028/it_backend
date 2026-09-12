package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.UserNameResolver;
import com.kdb.it.domain.budget.project.dto.ProjectDirectoryDto;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository.ProjectDirectoryView;
import com.kdb.it.exception.NotFoundException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전 직원 통합 검색과 접근 제한 안내에 필요한 안전한 사업 요약을 제공합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectDirectoryService {

    private static final String PROJECT_STATUS_CODE = "IT_PTL_STS_TC";
    private static final String PROJECT_TABLE_NAME = "BPROJM";

    /**
     * 디렉터리 목록이 한 요청에서 조립하는 최대 사업 수입니다.
     *
     * <p>정보화사업 목록 API의 상한({@code ProjectRepositoryImpl.MAX_LIST_ROWS})과 같은 값입니다. 관리번호 내림차순으로 조회하므로
     * 상한을 넘으면 가장 오래된 사업부터 목록에서 빠집니다.
     */
    static final int MAX_DIRECTORY_ROWS = 500;

    private final ProjectRepository projectRepository;
    private final BprojaRepository bprojaRepository;
    private final ApplicationMapRepository applicationMapRepository;
    private final ApplicationRepository applicationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final CodeService codeService;

    /**
     * 부서 범위를 적용하지 않고 현재 최종본 사업의 안전한 요약을 반환합니다.
     *
     * <p>전체 엔티티 대신 {@link ProjectDirectoryView} 프로젝션을 (관리번호, 순번) 내림차순으로 {@link
     * #MAX_DIRECTORY_ROWS}까지만 조회합니다.
     */
    public List<ProjectDirectoryDto.Response> findAll() {
        return assemble(
                projectRepository.findDirectoryViewsByDelYnAndLstYnOrderByAbusMngNoDescSnoDesc(
                        "N", "Y", Limit.of(MAX_DIRECTORY_ROWS)));
    }

    /**
     * 상세 권한과 무관하게 지정 사업의 담당 부서·담당자 요약을 반환합니다.
     *
     * @throws NotFoundException 최종·미삭제 사업이 없는 경우
     */
    public ProjectDirectoryDto.Response findOne(String abusMngNo) {
        ProjectDirectoryView project =
                projectRepository
                        .findDirectoryViewByAbusMngNoAndLstYnAndDelYn(abusMngNo, "Y", "N")
                        .orElseThrow(() -> new NotFoundException("정보화사업을 찾을 수 없습니다."));
        return assemble(List.of(project)).getFirst();
    }

    private List<ProjectDirectoryDto.Response> assemble(List<ProjectDirectoryView> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }

        List<String> projectIds =
                projects.stream().map(ProjectDirectoryView::getAbusMngNo).distinct().toList();
        Map<String, List<Bproja>> stepsByProject =
                bprojaRepository.findByAbusMngNoInAndDelYn(projectIds, "N").stream()
                        .collect(Collectors.groupingBy(Bproja::getAbusMngNo));
        Set<String> currentRevisionKeys =
                projects.stream()
                        .map(project -> revisionKey(project.getAbusMngNo(), project.getSno()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, ApplicationMapRepository.ApplicationMapView> latestApplications =
                new LinkedHashMap<>();
        for (ApplicationMapRepository.ApplicationMapView view :
                applicationMapRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                        PROJECT_TABLE_NAME, projectIds)) {
            String revisionKey = revisionKey(view.getPkColNm(), view.getFntTbCrySno());
            if (currentRevisionKeys.contains(revisionKey)) {
                latestApplications.putIfAbsent(revisionKey, view);
            }
        }
        List<String> applicationIds =
                latestApplications.values().stream()
                        .map(ApplicationMapRepository.ApplicationMapView::getApfDcmNo)
                        .distinct()
                        .toList();
        Map<String, ApplicationRepository.ApplicationSummaryView> applications =
                applicationIds.isEmpty()
                        ? Map.of()
                        : applicationRepository
                                .findSummaryViewsByApfMngNoIn(applicationIds)
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                ApplicationRepository.ApplicationSummaryView
                                                        ::getApfMngNo,
                                                java.util.function.Function.identity()));

        Set<String> organizationCodes = new LinkedHashSet<>();
        Set<String> userIds = new LinkedHashSet<>();
        for (ProjectDirectoryView project : projects) {
            addNonBlank(organizationCodes, project.getSvnDpmC());
            addNonBlank(userIds, project.getTlrUsid());
            addNonBlank(userIds, project.getUsid());
        }

        Map<String, String> organizationNames = new LinkedHashMap<>();
        for (OrganizationRepository.OrganizationNameView view :
                organizationRepository.findNameViewsByPrlmOgzCConeIn(organizationCodes)) {
            organizationNames.put(view.getPrlmOgzCCone(), view.getBbrNm());
        }
        Map<String, String> userNames = new LinkedHashMap<>();
        for (UserRepository.UserNameView view : userRepository.findNameViewsByEnoIn(userIds)) {
            userNames.put(view.getEno(), view.getUsrNm());
        }
        Map<String, String> statusNames = new LinkedHashMap<>();
        for (Ccodem code : codeService.findCodeEntitiesByCId(PROJECT_STATUS_CODE)) {
            statusNames.put(code.getCdva(), code.getCdvaNm());
        }

        return projects.stream()
                .map(
                        project -> {
                            String status =
                                    ProjectQueryAssembler.representativeStatus(
                                            stepsByProject.getOrDefault(
                                                    project.getAbusMngNo(), List.of()),
                                            project.getAbusMngNo());
                            ApplicationMapRepository.ApplicationMapView applicationMap =
                                    latestApplications.get(
                                            revisionKey(project.getAbusMngNo(), project.getSno()));
                            ApplicationRepository.ApplicationSummaryView application =
                                    applicationMap == null
                                            ? null
                                            : applications.get(applicationMap.getApfDcmNo());
                            String applicationStatusCode =
                                    application == null ? null : application.getItPtlApfPrgStsC();
                            Person leader =
                                    person(
                                            project.getTlrUsid(),
                                            project.getTlrNm(),
                                            userNames.get(project.getTlrUsid()));
                            Person manager =
                                    person(
                                            project.getUsid(),
                                            project.getUsrNm(),
                                            userNames.get(project.getUsid()));
                            return new ProjectDirectoryDto.Response(
                                    project.getAbusMngNo(),
                                    project.getAbusNm(),
                                    project.getOdnYn(),
                                    status,
                                    statusNames.get(status),
                                    applicationStatusCode == null
                                            ? null
                                            : ApprovalStatus.ofCode(applicationStatusCode).label(),
                                    applicationStatusCode,
                                    firstNonBlank(
                                            organizationNames.get(project.getSvnDpmC()),
                                            project.getSvnDpmNm()),
                                    leader.id(),
                                    leader.name(),
                                    manager.id(),
                                    manager.name());
                        })
                .toList();
    }

    private static String revisionKey(String projectId, Integer sequence) {
        return projectId + "|" + sequence;
    }

    private static Person person(String storedId, String snapshotName, String resolvedName) {
        String name = firstNonBlank(UserNameResolver.resolve(storedId, resolvedName), snapshotName);
        String id = UserNameResolver.isStoredName(storedId, resolvedName) ? null : storedId;
        return new Person(id, name);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static void addNonBlank(Collection<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private record Person(String id, String name) {}
}
