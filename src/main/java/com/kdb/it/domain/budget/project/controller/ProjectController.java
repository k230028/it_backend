package com.kdb.it.domain.budget.project.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.ListPageParams;
import com.kdb.it.domain.budget.project.dto.ProjectConflictResponse;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectQueryAssembler;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.budget.project.service.ProjectVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 정보화사업(프로젝트) 관리 REST 컨트롤러
 *
 * <p>정보화사업(TPRMPP_BPROJM 테이블)의 CRUD 및 일괄 조회 기능을 담당합니다.
 *
 * <p>기본 URL: {@code /api/projects}
 *
 * <p>정보화사업은 IT 부문의 신규 사업/시스템 도입 프로젝트를 관리하는 도메인으로, 품목 정보(TPRMPP_BITEMM)와 신청서 정보(TPRMPP_CAPPLM)와
 * 연관됩니다.
 *
 * <p>결재 제약 사항:
 *
 * <ul>
 *   <li>결재중/결재완료 상태인 프로젝트는 수정/삭제 불가
 * </ul>
 *
 * <p>보안: JWT 토큰 인증 필요
 */
@RestController // REST API 컨트롤러로 등록
@RequestMapping("/api/projects") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Tag(name = "Project", description = "정보화사업 API") // Swagger UI 그룹 태그
public class ProjectController {

    /** 정보화사업 비즈니스 로직 서비스 */
    private final ProjectService projectService;

    /** 정보화사업 재신청 이력 서비스 */
    private final ProjectVersionService projectVersionService;

    /** 정보화사업 명시 버전 상세 응답 조립기 */
    private final ProjectQueryAssembler projectQueryAssembler;

    /**
     * 정보화사업 목록 조회 (검색 조건 지원)
     *
     * <p>DEL_YN='N'인 삭제되지 않은 정보화사업 목록을 반환합니다. Query Parameter로 검색 조건을 전달하면 필터링된 결과를 반환합니다. 각 프로젝트의
     * 최신 신청서 정보(신청서관리번호, 신청서상태)도 포함됩니다.
     *
     * <p>검색 조건 예시:
     *
     * <ul>
     *   <li>{@code GET /api/projects} → 전체 조회
     *   <li>{@code GET /api/projects?apfSts=none} → 신청서가 없는 프로젝트만
     *   <li>{@code GET /api/projects?apfSts=결재중} → 결재중인 프로젝트만
     *   <li>{@code GET /api/projects?bgYy=2026} → 2026년 사업만
     *   <li>{@code GET /api/projects?apfSts=none&bgYy=2026} → 복합 조건
     *   <li>{@code GET /api/projects?bgYy=2026&page=0&size=100} → 해당 조건의 첫 100건
     * </ul>
     *
     * <p>{@code size}를 지정하면 그 구간만 조회하고 응답 헤더 {@code X-Total-Count}에 조건에 맞는 전체 건수를 담습니다. 지정하지 않으면
     * 기존과 같이 목록 상한까지 한 번에 반환합니다.
     *
     * @param condition 검색 조건 (apfSts, bseYy, stsTc, bzTpC, dvmDpmC, svnDpmC, odnYn). 미입력 시 전체 조회
     * @param paging 페이지 파라미터 (page, size). 미입력 시 상한까지 조회
     * @return HTTP 200 + 정보화사업 목록 ({@link ProjectDto.Response} 리스트)
     */
    @GetMapping
    @Operation(
            summary = "정보화사업 목록 조회",
            description =
                    "정보화사업 목록을 조회합니다. "
                            + "Query Parameter로 조건을 지정하면 필터링된 결과를 반환합니다. "
                            + "apfSts=none은 신청서가 없는 프로젝트, "
                            + "apfSts=결재중/결재완료 등은 해당 결재상태의 프로젝트를 조회합니다. "
                            + "size를 지정하면 해당 페이지만 반환하고 X-Total-Count 헤더에 전체 건수를 담습니다.")
    public ResponseEntity<List<ProjectDto.Response>> getProjects(
            @ParameterObject @ModelAttribute ProjectDto.SearchCondition condition,
            @ParameterObject @ModelAttribute ListPageParams paging,
            @AuthenticationPrincipal CustomUserDetails user) {
        List<ProjectDto.Response> body = projectService.searchProjectList(condition, user, paging);
        if (!paging.isPaged()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.ok()
                .header(
                        ListPageParams.TOTAL_COUNT_HEADER,
                        String.valueOf(projectService.countProjectList(condition, user)))
                .body(body);
    }

    /**
     * 특정 정보화사업 단건 조회
     *
     * <p>프로젝트 관리번호(PRJ_MNG_NO)로 정보화사업 상세 정보를 조회합니다. 품목 목록(TPRMPP_BITEMM)과 최신 신청서 정보도 함께 반환됩니다.
     *
     * @param prjMngNo 프로젝트 관리번호 (예: {@code PRJ-2026-0001})
     * @return HTTP 200 + 정보화사업 상세 정보 ({@link ProjectDto.Response})
     */
    @GetMapping("/{prjMngNo}")
    @Operation(summary = "특정 정보화사업 조회", description = "특정 정보화사업을 조회합니다.")
    public ResponseEntity<ProjectDto.Response> getProject(
            @PathVariable("prjMngNo") String prjMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        ProjectDto.Response response = projectService.getProject(prjMngNo, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 정보화사업의 최종본·과거본·재신청 초안 이력을 조회합니다.
     *
     * @param prjMngNo 프로젝트관리번호
     * @param user 인증 사용자
     * @return 순번 오름차순 이력 상세 목록
     */
    @GetMapping("/{prjMngNo}/history")
    @Operation(summary = "정보화사업 이력 조회", description = "사업관리번호의 최종본과 재신청 이력을 조회합니다.")
    public ResponseEntity<List<ProjectDto.Response>> getProjectHistory(
            @PathVariable("prjMngNo") String prjMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(
                projectVersionService.findHistory(prjMngNo, user).stream()
                        .map(projectQueryAssembler::assembleDetail)
                        .toList());
    }

    /**
     * 정보화사업의 명시적 순번 상세를 조회합니다.
     *
     * @param prjMngNo 프로젝트관리번호
     * @param sno 개정 순번
     * @param user 인증 사용자
     * @return 선택한 개정본의 상세
     */
    @GetMapping("/{prjMngNo}/versions/{sno}")
    @Operation(summary = "정보화사업 개정본 상세 조회", description = "사업관리번호와 순번으로 과거본 또는 재신청 초안을 조회합니다.")
    public ResponseEntity<ProjectDto.Response> getProjectVersion(
            @PathVariable("prjMngNo") String prjMngNo,
            @PathVariable("sno") Integer sno,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(
                projectVersionService
                        .findVersion(prjMngNo, sno, user)
                        .map(projectQueryAssembler::assembleDetail)
                        .orElseThrow(
                                () ->
                                        new com.kdb.it.exception.NotFoundException(
                                                "정보화사업 개정본을 찾을 수 없습니다: "
                                                        + prjMngNo
                                                        + ", sno="
                                                        + sno)));
    }

    /**
     * 결재완료된 최종 정보화사업을 다음 순번의 재신청 초안으로 복제합니다.
     *
     * @param prjMngNo 프로젝트관리번호
     * @param user 인증 사용자
     * @return 생성된 초안의 관리번호·순번·최종여부
     */
    @PostMapping("/{prjMngNo}/reapplications")
    @Operation(summary = "정보화사업 수정 후 재신청", description = "결재완료 최종 사업을 다음 순번의 비최종 초안으로 복제합니다.")
    public ResponseEntity<ProjectVersionService.ProjectVersion> createProjectReapplication(
            @PathVariable("prjMngNo") String prjMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        ProjectVersionService.ProjectVersion draft =
                projectVersionService.createReapplication(prjMngNo, user);
        return ResponseEntity.created(
                        URI.create(
                                "/api/projects/%s/versions/%s"
                                        .formatted(draft.abusMngNo(), draft.sno())))
                .body(draft);
    }

    /**
     * 신규 정보화사업 생성
     *
     * <p>새로운 정보화사업을 등록합니다.
     *
     * <p>채번 규칙:
     *
     * <ul>
     *   <li>형식: {@code PRJ-{사업연도}-{4자리 시퀀스}} (예: {@code PRJ-2026-0001})
     *   <li>사업연도가 누락된 경우 서버 시간 기준 현재 연도를 사용합니다.
     * </ul>
     *
     * @param request 정보화사업 생성 요청 ({@link ProjectDto.CreateRequest})
     * @return HTTP 201 Created + 생성된 프로젝트 관리번호 (Location 헤더 포함)
     */
    @PostMapping
    @Operation(summary = "신규 정보화사업 생성", description = "신규 정보화사업을 생성합니다.")
    public ResponseEntity<String> createProject(
            @Valid @RequestBody ProjectDto.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        String prjMngNo = projectService.createProject(request, user);
        // 201 Created 응답 + Location 헤더에 생성된 리소스 URL 포함
        return ResponseEntity.created(URI.create("/api/projects/" + prjMngNo)).body(prjMngNo);
    }

    /**
     * 정보화사업 정보 수정
     *
     * <p>정보화사업 정보를 수정합니다. 품목 목록도 함께 동기화됩니다.
     *
     * <p>품목 동기화 규칙:
     *
     * <ul>
     *   <li>요청에 있는 기존 품목 → 수정
     *   <li>요청에 있는 신규 품목 (gclMngNo 없음) → 추가
     *   <li>요청에 없는 기존 품목 → Soft Delete (DEL_YN='Y')
     * </ul>
     *
     * <p>⚠ 결재중/결재완료 상태인 경우 수정 불가 (400 에러 반환)
     *
     * <p>사용자 저장 경로는 조회 응답의 {@code concurrencyStamp}를 함께 보내야 하며, 누락되면 400, 다른 사용자가 먼저 저장했으면 현재 상태를
     * 담은 409로 병합을 유도합니다.
     *
     * @param prjMngNo 수정할 프로젝트 관리번호
     * @param request 수정 요청 데이터 ({@link ProjectDto.UpdateRequest})
     * @return HTTP 200 + 수정된 프로젝트 관리번호
     */
    @PutMapping("/{prjMngNo}")
    @Operation(summary = "정보화사업 수정", description = "정보화사업을 수정합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "수정 성공 (반환값: 프로젝트관리번호)",
                        content = @Content(schema = @Schema(implementation = String.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "다른 사용자가 원장을 변경했거나 잠금 대기를 초과함",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                ProjectConflictResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "동시성 스탬프 누락 또는 형식 오류, 결재 진행 중",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                ProjectConflictResponse.class)))
            })
    public ResponseEntity<String> updateProject(
            @PathVariable("prjMngNo") String prjMngNo,
            @RequestParam(value = "sno", required = false) Integer sno,
            @Valid @RequestBody ProjectDto.UpdateRequest request) {
        String updatedPrjMngNo =
                sno == null
                        ? projectService.updateProject(prjMngNo, request)
                        : projectService.updateProject(prjMngNo, sno, request);
        return ResponseEntity.ok(updatedPrjMngNo);
    }

    /**
     * 정보화사업 삭제 (Soft Delete)
     *
     * <p>정보화사업과 관련 품목을 논리 삭제(DEL_YN='Y')합니다.
     *
     * <p>임시저장·작성완료·반려·회수 상태인 지정 순번의 사업과 품목만 삭제합니다.
     *
     * @param prjMngNo 삭제할 프로젝트 관리번호
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{prjMngNo}")
    @Operation(summary = "정보화사업 삭제", description = "정보화사업을 삭제합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content),
                @ApiResponse(responseCode = "400", description = "삭제 불가", content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 사업", content = @Content)
            })
    public ResponseEntity<Void> deleteProject(
            @PathVariable("prjMngNo") String prjMngNo,
            @Parameter(description = "삭제할 사업 순번", required = true, example = "1")
                    @RequestParam(value = "sno", required = false)
                    Integer sno) {
        if (sno == null || sno < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제할 사업 순번이 필요합니다.");
        }
        projectService.deleteProject(prjMngNo, sno);
        return ResponseEntity.noContent().build();
    }

    /**
     * 정보화사업 일괄 조회
     *
     * <p>여러 프로젝트 관리번호를 한 번에 조회합니다. 존재하지 않는 프로젝트 관리번호는 {@code failedIds}로 함께 반환됩니다 (부분 성공).
     *
     * @param request 조회할 프로젝트 관리번호 목록 ({@link ProjectDto.BulkGetRequest})
     * @return HTTP 200 + 조회 성공 항목({@code items})과 미존재 관리번호({@code failedIds})를 담은 결과
     */
    @PostMapping("/bulk-get")
    @Operation(
            summary = "정보화사업 일괄 조회",
            description = "여러 개의 정보화사업을 한 번에 조회합니다. 존재하지 않는 프로젝트는 failedIds로 함께 반환됩니다 (부분 성공).")
    public ResponseEntity<ProjectDto.BulkResponse> bulkGetProjects(
            @Valid @RequestBody ProjectDto.BulkGetRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        ProjectDto.BulkResponse responses = projectService.getProjectsByIds(request, user);
        return ResponseEntity.ok(responses);
    }
}
