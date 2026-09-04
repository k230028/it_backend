package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.UserNameResolver;
import com.kdb.it.domain.budget.project.dto.ProjectDirectoryDto;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.exception.NotFoundException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전 직원 통합 검색과 접근 제한 안내에 필요한 안전한 사업 요약을 제공합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectDirectoryService {

    private static final String PROJECT_STATUS_CODE = "IT_PTL_STS_TC";

    private final ProjectRepository projectRepository;
    private final BprojaRepository bprojaRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final CodeService codeService;

    /** 부서 범위를 적용하지 않고 현재 최종본 사업의 안전한 요약을 반환합니다. */
    public List<ProjectDirectoryDto.Response> findAll() {
        return assemble(projectRepository.findAllByDelYn("N"));
    }

    /** 상세 권한과 무관하게 지정 사업의 담당 부서·담당자 요약을 반환합니다. */
    public ProjectDirectoryDto.Response findOne(String abusMngNo) {
        Bprojm project =
                projectRepository
                        .findByAbusMngNoAndDelYn(abusMngNo, "N")
                        .orElseThrow(() -> new NotFoundException("정보화사업을 찾을 수 없습니다."));
        return assemble(List.of(project)).getFirst();
    }

    private List<ProjectDirectoryDto.Response> assemble(List<Bprojm> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }

        List<String> projectIds = projects.stream().map(Bprojm::getAbusMngNo).distinct().toList();
        Map<String, List<Bproja>> stepsByProject =
                bprojaRepository.findByAbusMngNoInAndDelYn(projectIds, "N").stream()
                        .collect(Collectors.groupingBy(Bproja::getAbusMngNo));

        Set<String> organizationCodes = new LinkedHashSet<>();
        Set<String> userIds = new LinkedHashSet<>();
        for (Bprojm project : projects) {
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
                                    status,
                                    statusNames.get(status),
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
