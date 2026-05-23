package com.kdb.it.domain.budget.project.controller;

import java.net.URI;
import java.util.List;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 정보화사업(프로젝트) 관리 REST 컨트롤러
 *
 * <p>
 * 정보화사업(TPRMPP_BPROJM 테이블)의 CRUD 및 일괄 조회 기능을 담당합니다.
 * </p>
 *
 * <p>
 * 기본 URL: {@code /api/projects}
 * </p>
 *
 * <p>
 * 정보화사업은 IT 부문의 신규 사업/시스템 도입 프로젝트를 관리하는 도메인으로,
 * 품목 정보(TPRMPP_BITEMM)와 신청서 정보(TPRMPP_CAPPLM)와 연관됩니다.
 * </p>
 *
 * <p>
 * 결재 제약 사항:
 * </p>
 * <ul>
 * <li>결재중/결재완료 상태인 프로젝트는 수정/삭제 불가</li>
 * </ul>
 *
 * <p>
 * 보안: JWT 토큰 인증 필요
 * </p>
 */
@RestController // REST API 컨트롤러로 등록
@RequestMapping("/api/projects") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Tag(name = "Project", description = "정보화사업 API") // Swagger UI 그룹 태그
public class ProjectController {

    /** 정보화사업 비즈니스 로직 서비스 */
    private final ProjectService projectService;

    /**
     * 정보화사업 목록 조회 (검색 조건 지원)
     *
     * <p>
     * DEL_YN='N'인 삭제되지 않은 정보화사업 목록을 반환합니다.
     * Query Parameter로 검색 조건을 전달하면 필터링된 결과를 반환합니다.
     * 각 프로젝트의 최신 신청서 정보(신청서관리번호, 신청서상태)도 포함됩니다.
     * </p>
     *
     * <p>
     * 검색 조건 예시:
     * </p>
     * <ul>
     * <li>{@code GET /api/projects} → 전체 조회</li>
     * <li>{@code GET /api/projects?apfSts=none} → 신청서가 없는 프로젝트만</li>
     * <li>{@code GET /api/projects?apfSts=결재중} → 결재중인 프로젝트만</li>
     * <li>{@code GET /api/projects?bgYy=2026} → 2026년 사업만</li>
     * <li>{@code GET /api/projects?apfSts=none&bgYy=2026} → 복합 조건</li>
     * </ul>
     *
     * @param condition 검색 조건 (apfSts, bgYy, prjSts, prjTp, itDpm, svnDpm). 미입력 시 전체 조회
     * @return HTTP 200 + 정보화사업 목록 ({@link ProjectDto.Response} 리스트)
     */
    @GetMapping
    @Operation(
        summary = "정보화사업 목록 조회",
        description = "정보화사업 목록을 조회합니다. " +
                      "Query Parameter로 조건을 지정하면 필터링된 결과를 반환합니다. " +
                      "apfSts=none은 신청서가 없는 프로젝트, " +
                      "apfSts=결재중/결재완료 등은 해당 결재상태의 프로젝트를 조회합니다."
    )
    public ResponseEntity<List<ProjectDto.Response>> getProjects(
            @ModelAttribute ProjectDto.SearchCondition condition) {
        return ResponseEntity.ok(projectService.searchProjectList(condition));
    }

    /**
     * 특정 정보화사업 단건 조회
     *
     * <p>
     * 프로젝트 관리번호(PRJ_MNG_NO)로 정보화사업 상세 정보를 조회합니다.
     * 품목 목록(TPRMPP_BITEMM)과 최신 신청서 정보도 함께 반환됩니다.
     * </p>
     *
     * @param prjMngNo 프로젝트 관리번호 (예: {@code PRJ-2026-0001})
     * @return HTTP 200 + 정보화사업 상세 정보 ({@link ProjectDto.Response})
     */
    @GetMapping("/{prjMngNo}")
    @Operation(summary = "특정 정보화사업 조회", description = "특정 정보화사업을 조회합니다.")
    public ResponseEntity<ProjectDto.Response> getProject(@PathVariable("prjMngNo") String prjMngNo) {
        ProjectDto.Response response = projectService.getProject(prjMngNo);
        return ResponseEntity.ok(response);
    }

    /**
     * 신규 정보화사업 생성
     *
     * <p>
     * 새로운 정보화사업을 등록합니다.
     * </p>
     *
     * <p>
     * 채번 규칙:
     * </p>
     * <ul>
     * <li>형식: {@code PRJ-{사업연도}-{4자리 시퀀스}} (예: {@code PRJ-2026-0001})</li>
     * <li>사업연도가 누락된 경우 서버 시간 기준 현재 연도를 사용합니다.</li>
     * </ul>
     *
     * @param request 정보화사업 생성 요청 ({@link ProjectDto.CreateRequest})
     * @return HTTP 201 Created + 생성된 프로젝트 관리번호 (Location 헤더 포함)
     */
    @PostMapping
    @Operation(summary = "신규 정보화사업 생성", description = "신규 정보화사업을 생성합니다.")
        // FIXME: @Valid 추가 필요 — Bean Validation이 동작하지 않아 미검증 입력이 서비스 레이어로 전달됨 (CLAUDE.md §5.5.2)
    public ResponseEntity<String> createProject(@RequestBody ProjectDto.CreateRequest request) {
        String prjMngNo = projectService.createProject(request);
        // 201 Created 응답 + Location 헤더에 생성된 리소스 URL 포함
        return ResponseEntity.created(URI.create("/api/projects/" + prjMngNo)).body(prjMngNo);
    }

    /**
     * 정보화사업 정보 수정
     *
     * <p>
     * 정보화사업 정보를 수정합니다. 품목 목록도 함께 동기화됩니다.
     * </p>
     *
     * <p>
     * 품목 동기화 규칙:
     * </p>
     * <ul>
     * <li>요청에 있는 기존 품목 → 수정</li>
     * <li>요청에 있는 신규 품목 (gclMngNo 없음) → 추가</li>
     * <li>요청에 없는 기존 품목 → Soft Delete (DEL_YN='Y')</li>
     * </ul>
     *
     * <p>
     * ⚠ 결재중/결재완료 상태인 경우 수정 불가 (400 에러 반환)
     * </p>
     *
     * @param prjMngNo 수정할 프로젝트 관리번호
     * @param request  수정 요청 데이터 ({@link ProjectDto.UpdateRequest})
     * @return HTTP 200 + 수정된 프로젝트 관리번호
     */
    @PutMapping("/{prjMngNo}")
    @Operation(summary = "정보화사업 수정", description = "정보화사업을 수정합니다.")
    public ResponseEntity<String> updateProject(@PathVariable("prjMngNo") String prjMngNo,
        // FIXME: @Valid 추가 필요 — Bean Validation이 동작하지 않아 미검증 입력이 서비스 레이어로 전달됨 (CLAUDE.md §5.5.2)
            @RequestBody ProjectDto.UpdateRequest request) {
        String updatedPrjMngNo = projectService.updateProject(prjMngNo, request);
        return ResponseEntity.ok(updatedPrjMngNo);
    }

    /**
     * 정보화사업 삭제 (Soft Delete)
     *
     * <p>
     * 정보화사업과 관련 품목을 논리 삭제(DEL_YN='Y')합니다.
     * </p>
     *
     * <p>
     * ⚠ 결재중/결재완료 상태인 경우 삭제 불가 (400 에러 반환)
     * </p>
     *
     * @param prjMngNo 삭제할 프로젝트 관리번호
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{prjMngNo}")
    @Operation(summary = "정보화사업 삭제", description = "정보화사업을 삭제합니다.")
    public ResponseEntity<Void> deleteProject(@PathVariable("prjMngNo") String prjMngNo) {
        projectService.deleteProject(prjMngNo);
        return ResponseEntity.noContent().build();
    }

    /**
     * 정보화사업 일괄 조회
     *
     * <p>
     * 여러 프로젝트 관리번호를 한 번에 조회합니다.
     * 존재하지 않는 프로젝트 관리번호는 결과에서 제외됩니다.
     * </p>
     *
     * @param request 조회할 프로젝트 관리번호 목록 ({@link ProjectDto.BulkGetRequest})
     * @return HTTP 200 + 정보화사업 목록 (존재하는 프로젝트만 포함)
     */
    @PostMapping("/bulk-get")
    @Operation(summary = "정보화사업 일괄 조회", description = "여러 개의 정보화사업을 한 번에 조회합니다. 존재하지 않는 프로젝트는 결과에서 제외됩니다.")
    public ResponseEntity<List<ProjectDto.Response>> bulkGetProjects(
            @RequestBody ProjectDto.BulkGetRequest request) {
        List<ProjectDto.Response> responses = projectService.getProjectsByIds(request);
        return ResponseEntity.ok(responses);
    }
}
