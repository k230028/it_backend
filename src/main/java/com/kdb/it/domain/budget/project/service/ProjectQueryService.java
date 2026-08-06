package com.kdb.it.domain.budget.project.service;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.exception.DataCorruptionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        return queryAssembler.assembleList(projectRepository.searchByCondition(condition));
    }

    /**
     * 관리번호에 해당하는 정보화사업 상세를 조회합니다.
     *
     * @param prjMngNo 프로젝트관리번호
     * @return 신청서·품목·대표상태가 조립된 상세 응답
     * @throws IllegalArgumentException 활성 프로젝트가 없는 경우
     */
    public ProjectDto.Response getProject(String prjMngNo) {
        Bprojm project =
                projectRepository
                        .findByAbusMngNoAndDelYn(prjMngNo, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Project not found with id: " + prjMngNo));
        return queryAssembler.assembleDetail(project);
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

        Map<String, Bprojm> projectById =
                projectRepository.findByAbusMngNoInAndDelYn(request.getPrjMngNos(), "N").stream()
                        .collect(
                                Collectors.toMap(
                                        Bprojm::getAbusMngNo,
                                        java.util.function.Function.identity(),
                                        (first, second) -> {
                                            throw new DataCorruptionException(
                                                    "활성 사업 기본행이 둘 이상입니다: abusMngNo="
                                                            + first.getAbusMngNo());
                                        }));
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
}
