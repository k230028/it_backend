package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.common.security.BudgetDetailAccessVerifier;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.exception.DataCorruptionException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정보화사업 조회 흐름을 담당합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProjectQueryService.class);

    private final ProjectRepository projectRepository;
    private final ProjectQueryAssembler queryAssembler;

    /**
     * 삭제되지 않은 모든 정보화사업을 조회합니다.
     *
     * @return 신청서·코드명·예산 정보가 조립된 목록
     */
    public List<ProjectDto.Response> getProjectList() {
        return queryAssembler.assembleList(projectRepository.findAllByDelYn("N"));
    }

    /**
     * 검색 조건에 맞는 정보화사업을 조회합니다.
     *
     * @param condition 검색 조건
     * @return 조건에 맞고 연관 정보가 조립된 목록
     */
    public List<ProjectDto.Response> searchProjectList(ProjectDto.SearchCondition condition) {
        return searchProjectList(condition, com.kdb.it.common.util.ListPageParams.unpaged());
    }

    /**
     * 검색 조건에 맞는 정보화사업을 지정한 페이지 구간만 조회합니다.
     *
     * @param condition 검색 조건
     * @param paging 페이지 파라미터 (미지정이면 상한까지)
     * @return 해당 구간의 목록
     */
    public List<ProjectDto.Response> searchProjectList(
            ProjectDto.SearchCondition condition, com.kdb.it.common.util.ListPageParams paging) {
        return queryAssembler.assembleList(projectRepository.searchByCondition(condition, paging));
    }

    /**
     * 검색 조건에 맞는 정보화사업 전체 건수를 조회합니다.
     *
     * <p>페이지 응답의 {@code X-Total-Count}에 사용합니다. 목록과 같은 WHERE를 공유하므로 페이지를 지정하지 않았을 때의 목록 건수와 일치합니다.
     *
     * @param condition 검색 조건 (page·size는 건수에 영향을 주지 않음)
     * @return 조건에 맞는 전체 건수
     */
    public long countProjectList(ProjectDto.SearchCondition condition) {
        return projectRepository.countBySearchCondition(condition);
    }

    /**
     * 관리번호에 해당하는 정보화사업 상세를 조회합니다.
     *
     * @param prjMngNo 프로젝트관리번호
     * @return 신청서·품목·대표상태가 조립된 상세 응답
     * @throws IllegalArgumentException 활성 프로젝트가 없는 경우
     */
    public ProjectDto.Response getProject(String prjMngNo) {
        return queryAssembler.assembleDetail(findActiveProject(prjMngNo));
    }

    /**
     * 인증 사용자의 데이터 범위를 검증한 뒤 정보화사업 상세를 조회합니다.
     *
     * @param prjMngNo 프로젝트관리번호
     * @param actor 인증 사용자
     * @return 신청서·품목·대표상태가 조립된 상세 응답
     * @throws IllegalArgumentException 활성 프로젝트가 없는 경우
     * @throws org.springframework.security.access.AccessDeniedException 조회 범위 밖인 경우
     */
    public ProjectDto.Response getProject(String prjMngNo, CustomUserDetails actor) {
        Bprojm project = findActiveProject(prjMngNo);
        BudgetDetailAccessVerifier.verifyReadable(project.getSvnDpmC(), actor);
        return queryAssembler.assembleDetail(project);
    }

    private Bprojm findActiveProject(String prjMngNo) {
        Bprojm project =
                projectRepository
                        .findByAbusMngNoAndDelYn(prjMngNo, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Project not found with id: " + prjMngNo));
        return project;
    }

    /**
     * 여러 프로젝트를 입력 순서대로 일괄 조회합니다.
     *
     * @param request 프로젝트관리번호와 기준연도
     * @return 성공 항목과 누락 관리번호를 분리한 응답
     * @throws DataCorruptionException 같은 관리번호의 활성 기본행이 둘 이상인 경우
     */
    public ProjectDto.BulkResponse getProjectsByIds(ProjectDto.BulkGetRequest request) {
        if (request == null || request.getPrjMngNos() == null || request.getPrjMngNos().isEmpty()) {
            return new ProjectDto.BulkResponse(List.of(), List.of());
        }

        Map<String, Bprojm> projectById = loadBulkTargets(request);
        List<Bprojm> projects = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        for (String prjMngNo : request.getPrjMngNos()) {
            Bprojm project = projectById.get(prjMngNo);
            if (project == null) {
                failedIds.add(prjMngNo);
            } else {
                projects.add(project);
            }
        }
        if (!failedIds.isEmpty()) {
            log.warn("bulk-get 누락: type=project, failedIds={}", failedIds);
        }
        return new ProjectDto.BulkResponse(
                queryAssembler.assembleBulk(projects, request.getBseYy()), failedIds);
    }

    /**
     * bulk 조회 대상 개정본을 관리번호별로 한 건씩 확정합니다.
     *
     * <p>{@code versions}에 지정된 관리번호는 그 개정 순번을, 나머지는 최종본({@code LST_YN='Y'})을 씁니다. 지정된 개정본을 찾지 못하면
     * 조용히 최종본으로 대체하지 않고 누락으로 남겨 호출자가 구분할 수 있게 합니다.
     *
     * @param request 일괄 조회 요청
     * @return 관리번호 → 채택한 개정본
     * @throws DataCorruptionException 최종본 경로에서 활성 기본행이 둘 이상인 경우
     */
    private Map<String, Bprojm> loadBulkTargets(ProjectDto.BulkGetRequest request) {
        Map<String, Integer> requestedSnoByMngNo = new LinkedHashMap<>();
        if (request.getVersions() != null) {
            for (ProjectDto.VersionRef version : request.getVersions()) {
                if (version != null && version.mngNo() != null && version.sno() != null) {
                    requestedSnoByMngNo.put(version.mngNo(), version.sno());
                }
            }
        }

        Map<String, Bprojm> projectById = new LinkedHashMap<>();
        if (!requestedSnoByMngNo.isEmpty()) {
            List<Bprojm> versioned =
                    projectRepository.findByAbusMngNoInAndDelYnAndSnoIn(
                            List.copyOf(requestedSnoByMngNo.keySet()),
                            "N",
                            List.copyOf(new LinkedHashSet<>(requestedSnoByMngNo.values())));
            for (Bprojm project : versioned) {
                // 두 집합의 곱으로 읽었으므로 요청한 정확한 (관리번호, 순번) 쌍만 채택한다.
                if (Objects.equals(
                        requestedSnoByMngNo.get(project.getAbusMngNo()), project.getSno())) {
                    projectById.put(project.getAbusMngNo(), project);
                }
            }
        }

        List<String> currentVersionIds =
                request.getPrjMngNos().stream()
                        .filter(id -> !requestedSnoByMngNo.containsKey(id))
                        .distinct()
                        .toList();
        if (!currentVersionIds.isEmpty()) {
            projectRepository.findByAbusMngNoInAndDelYn(currentVersionIds, "N").stream()
                    .collect(
                            Collectors.toMap(
                                    Bprojm::getAbusMngNo,
                                    java.util.function.Function.identity(),
                                    (first, second) -> {
                                        throw new DataCorruptionException(
                                                "활성 사업 기본행이 둘 이상입니다: abusMngNo="
                                                        + first.getAbusMngNo());
                                    }))
                    .forEach(projectById::put);
        }
        return projectById;
    }
}
