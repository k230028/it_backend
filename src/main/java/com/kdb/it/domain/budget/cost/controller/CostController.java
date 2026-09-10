package com.kdb.it.domain.budget.cost.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.ListPageParams;
import com.kdb.it.domain.budget.cost.dto.CostConflictResponse;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.dto.CostTerminalDto;
import com.kdb.it.domain.budget.cost.service.CostQueryAssembler;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.cost.service.CostTerminalLinkService;
import com.kdb.it.domain.budget.cost.service.CostTerminalUpdateService;
import com.kdb.it.domain.budget.cost.service.CostVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
 * 전산관리비(IT 관리비) 관리 REST 컨트롤러
 *
 * <p>전산관리비(TPRMPP_BCOSTM 테이블)의 CRUD 및 일괄 조회 기능을 담당합니다.
 *
 * <p>기본 URL: {@code /api/cost}
 *
 * <p>전산관리비는 IT 인프라 유지보수 계약, 라이선스 비용 등 IT 관련 지출 항목을 관리하는 도메인입니다.
 *
 * <p>복합키 구조: {@code BG_NO} (관리번호) + {@code BG_SNO} (일련번호)
 */
@RestController // REST API 컨트롤러로 등록
@RequestMapping("/api/cost") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Tag(name = "Cost", description = "전산관리비 관리 API") // Swagger UI 그룹 태그
public class CostController {

    /** 전산관리비 비즈니스 로직 서비스 */
    private final CostService costService;

    private final CostVersionService costVersionService;

    private final CostTerminalLinkService terminalLinkService;
    private final CostTerminalUpdateService terminalUpdateService;
    private final CostQueryAssembler costQueryAssembler;

    /**
     * 특정 전산관리비 단건 조회
     *
     * <p>전산관리비 관리번호(IT_MNGC_NO)로 해당 전산관리비의 상세 정보를 조회합니다. 복합키 구조이므로 같은 관리번호에 여러 일련번호가 존재할 수 있으며, 이
     * 경우 LST_YN='Y'인 최신 항목을 반환합니다.
     *
     * <p>시스템관리자가 아니면 담당부서(COST_SVN_DPM_C)가 본인 소속 부서인 항목만 열람할 수 있습니다.
     *
     * @param itMngcNo 전산관리비 관리번호 (예: {@code COST_2026_0001})
     * @param user 인증 사용자
     * @return HTTP 200 + 전산관리비 상세 정보, HTTP 403 다른 부서 항목, HTTP 404 전산관리비가 없는 경우
     */
    @Operation(
            summary = "특정 전산관리비 조회",
            description =
                    "전산관리비 관리번호(IT_MNGC_NO)로 전산관리비 상세 정보를 조회합니다. "
                            + "시스템관리자가 아니면 본인 소속 부서 항목만 조회됩니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content =
                                @Content(
                                        schema = @Schema(implementation = CostDto.Response.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "다른 부서의 전산관리비",
                        content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "존재하지 않는 전산관리비",
                        content = @Content)
            })
    @GetMapping("/{itMngcNo}")
    public ResponseEntity<CostDto.Response> getCost(
            @Parameter(description = "전산관리비 관리번호", required = true, example = "COST_2026_0001")
                    @PathVariable("itMngcNo")
                    String itMngcNo,
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(value = "sno", required = false) Integer bgSno) {
        return ResponseEntity.ok(
                bgSno == null
                        ? costService.getCost(itMngcNo, user)
                        : costService.getCost(itMngcNo, bgSno, user));
    }

    /** 결재 완료본을 다음 예산일련번호의 미상신 초안으로 복제합니다. */
    @PostMapping("/{itMngcNo}/reapplications")
    public ResponseEntity<CostVersionService.CostVersion> createReapplication(
            @PathVariable("itMngcNo") String itMngcNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(costVersionService.createReapplication(itMngcNo, user));
    }

    /** 전산업무비의 개정 이력을 조회합니다. */
    @GetMapping("/{itMngcNo}/history")
    public ResponseEntity<List<CostDto.Response>> getHistory(
            @PathVariable("itMngcNo") String itMngcNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(
                costQueryAssembler.assembleHistory(costVersionService.findHistory(itMngcNo, user)));
    }

    /**
     * 전산관리비 정보 수정
     *
     * <p>전산관리비 관리번호로 조회한 항목 중 LST_YN='Y'인 최신 항목을 수정합니다. 수정 가능한 항목: 비목명, 계약명, 계약구분, 계약상대처, 예산, 지급주기,
     * 지급예정월, 통화, 환율, 정보보호여부, 증감사유, 추진담당자
     *
     * @param itMngcNo 수정할 전산관리비 관리번호
     * @param request 수정 요청 데이터 ({@link CostDto.UpdateRequest})
     * @return HTTP 200 + 수정된 전산관리비 관리번호(IT_MNGC_NO), HTTP 404 전산관리비가 없는 경우
     */
    @Operation(summary = "전산관리비 수정", description = "전산관리비 정보를 수정합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "수정 성공 (반환값: IT_MNGC_NO)",
                        content = @Content(schema = @Schema(implementation = String.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "존재하지 않는 전산관리비",
                        content = @Content),
                @ApiResponse(
                        responseCode = "409",
                        description = "다른 사용자가 원장을 변경했거나 잠금 대기를 초과함",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                CostConflictResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "동시성 스탬프 누락 또는 형식 오류",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                CostConflictResponse.class)))
            })
    @PutMapping("/{itMngcNo}")
    public ResponseEntity<String> updateCost(
            @Parameter(description = "전산관리비 관리번호", required = true, example = "COST_2026_0001")
                    @PathVariable("itMngcNo")
                    String itMngcNo,
            @RequestParam(value = "sno", required = false) Integer bgSno,
            @Valid @RequestBody CostDto.UpdateRequest request) {
        return ResponseEntity.ok(
                bgSno == null
                        ? costService.updateCost(itMngcNo, request)
                        : costService.updateCost(itMngcNo, bgSno, request));
    }

    /**
     * 부모 전산업무비의 업무 필드를 보존하면서 같은 개정본의 금융정보단말기 목록만 치환합니다.
     *
     * @param itMngcNo 부모 전산업무비 관리번호
     * @param bgSno 부모 전산업무비 순번. 생략하면 현재 대표 개정본
     * @param request 단말기 목록과 동시성 스탬프
     * @return HTTP 204
     */
    @Operation(summary = "금융정보단말기 목록 치환", description = "부모 전산업무비의 같은 개정본에 단말기 목록을 저장합니다.")
    @PutMapping("/{itMngcNo}/terminals")
    public ResponseEntity<Void> replaceTerminals(
            @PathVariable("itMngcNo") String itMngcNo,
            @RequestParam(value = "sno", required = false) Integer bgSno,
            @Valid @RequestBody CostTerminalDto.TerminalUpdateRequest request) {
        terminalUpdateService.replaceTerminals(itMngcNo, bgSno, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 연결 단말기 등록 표시
     *
     * <p>부모 전산업무비의 {@code TMN_YN}만 'Y'로 바꿉니다. 이 표시 하나를 위해 수정 API를 쓰면 전체 치환 의미론 때문에 요청에 담기지 않은 부모 업무
     * 필드가 null이 되고 부모의 단말 행이 모두 논리 삭제되므로, 좁은 전용 경로를 둡니다.
     *
     * <p>화면은 더 이상 이 경로를 쓰지 않고 {@link #createLinkedCost}로 생성과 표시를 한 번에 요청합니다. 구버전 프론트 번들이 만료될 때까지
     * 호환용으로만 남겨 두며, 이후 제거 대상입니다.
     *
     * @param itMngcNo 부모 전산업무비 관리번호
     * @return HTTP 204 (본문 없음)
     */
    @Operation(summary = "연결 단말기 등록 표시", description = "부모 전산업무비의 단말기 보유 여부(TMN_YN)만 'Y'로 표시합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", description = "표시 완료", content = @Content),
                @ApiResponse(
                        responseCode = "400",
                        description = "결재 진행 중이라 수정 불가",
                        content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "존재하지 않는 전산업무비",
                        content = @Content)
            })
    @PostMapping("/{itMngcNo}/terminal-link")
    public ResponseEntity<Void> markTerminalLinked(
            @Parameter(description = "부모 전산업무비 관리번호", required = true, example = "COST_2026_0001")
                    @PathVariable("itMngcNo")
                    String itMngcNo) {
        terminalLinkService.markTerminalLinked(itMngcNo);
        return ResponseEntity.noContent().build();
    }

    /**
     * 연결 단말 전산업무비 생성
     *
     * <p>부모 전산업무비에 연결되는 단말 전산업무비를 생성하고 부모의 단말기 보유 여부({@code TMN_YN})를 한 트랜잭션에서 함께 표시합니다. 생성과 표시를 따로
     * 호출하면 두 번째 호출이 결재 상태로 실패했을 때 단말 생성만 적용된 상태가 남으므로 단일 경로로 제공합니다.
     *
     * @param itMngcNo 부모 전산업무비 관리번호
     * @param request 단말 전산업무비 생성 요청 ({@link CostDto.CreateRequest})
     * @return HTTP 201 Created + Location 헤더 + 생성된 단말 전산업무비 관리번호
     */
    @Operation(
            summary = "연결 단말 전산업무비 생성",
            description = "부모 전산업무비에 연결되는 단말 전산업무비를 생성하고 부모의 단말기 보유 여부(TMN_YN)를 같은 트랜잭션에서 표시합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "201",
                        description = "생성 성공 (반환값: 생성된 관리번호)",
                        content = @Content(schema = @Schema(implementation = String.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "부모가 결재 진행 중이거나 예산 신청 기간이 아님",
                        content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "존재하지 않는 부모 전산업무비",
                        content = @Content)
            })
    @PostMapping("/{itMngcNo}/linked-costs")
    public ResponseEntity<String> createLinkedCost(
            @Parameter(description = "부모 전산업무비 관리번호", required = true, example = "COST_2026_0001")
                    @PathVariable("itMngcNo")
                    String itMngcNo,
            @Valid @RequestBody CostDto.CreateRequest request) {
        String createdCostBgNo = terminalLinkService.createLinkedCost(itMngcNo, request);
        return ResponseEntity.created(URI.create("/api/cost/" + createdCostBgNo))
                .body(createdCostBgNo);
    }

    /**
     * 전산관리비 삭제 (Soft Delete)
     *
     * <p>전산관리비를 물리적으로 삭제하지 않고, DEL_YN 컬럼을 'Y'로 변경하여 논리 삭제(Soft Delete)를 수행합니다. 삭제된 항목은 조회에서 제외됩니다.
     *
     * @param itMngcNo 삭제할 전산관리비 관리번호
     *     <p>임시저장 또는 작성완료 상태인 지정 순번의 전산업무비와 단말기만 삭제합니다.
     * @return HTTP 204 (본문 없음), HTTP 404 전산관리비가 없는 경우
     */
    @Operation(summary = "전산관리비 삭제", description = "전산관리비를 삭제(Soft Delete)합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content),
                @ApiResponse(responseCode = "400", description = "삭제 불가", content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "존재하지 않는 전산관리비",
                        content = @Content)
            })
    @DeleteMapping("/{itMngcNo}")
    public ResponseEntity<Void> deleteCost(
            @Parameter(description = "전산관리비 관리번호", required = true, example = "COST_2026_0001")
                    @PathVariable("itMngcNo")
                    String itMngcNo,
            @Parameter(description = "삭제할 전산업무비 순번", required = true, example = "1")
                    @RequestParam(value = "sno", required = false)
                    Integer bgSno) {
        if (bgSno == null || bgSno < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제할 전산업무비 순번이 필요합니다.");
        }
        costService.deleteCost(itMngcNo, bgSno);
        return ResponseEntity.noContent().build();
    }

    /**
     * 전산관리비 목록 조회 (검색 조건 지원)
     *
     * <p>DEL_YN='N'인 삭제되지 않은 전산관리비 목록을 반환합니다. Query Parameter로 검색 조건을 전달하면 필터링된 결과를 반환합니다.
     *
     * <p>검색 조건 예시:
     *
     * <ul>
     *   <li>{@code GET /api/cost} → 일반 사용자는 소속 부서, 시스템관리자는 전체 조회
     *   <li>{@code GET /api/cost?apfSts=none} → 신청서가 없는 전산관리비만
     *   <li>{@code GET /api/cost?apfSts=결재중} → 결재중인 전산관리비만
     *   <li>{@code GET /api/cost?bseYy=2026&page=0&size=100} → 해당 조건의 첫 100건
     * </ul>
     *
     * <p>{@code size}를 지정하면 그 구간만 조회하고 응답 헤더 {@code X-Total-Count}에 조건에 맞는 전체 건수를 담습니다. 지정하지 않으면
     * 기존과 같이 목록 상한까지 한 번에 반환합니다.
     *
     * @param condition 검색 조건 (apfSts, costSvnDpmC, svnTemC, sectSysUtzYn, bseYy, myDeptOnly). 일반
     *     사용자는 항상 소속 부서로 제한되며, 관리자는 myDeptOnly=true일 때 소속 부서로 제한됩니다.
     * @param paging 페이지 파라미터 (page, size). 미입력 시 상한까지 조회
     * @param user 인증 사용자 (목록 범위 결정에 사용)
     * @return HTTP 200 + 전산관리비 목록 ({@link CostDto.Response} 리스트)
     */
    @Operation(
            summary = "전산관리비 목록 조회",
            description =
                    "전산관리비 목록을 조회합니다. "
                            + "Query Parameter로 조건을 지정하면 필터링된 결과를 반환합니다. "
                            + "apfSts=none은 신청서가 없는 항목, "
                            + "apfSts=결재중/결재완료 등은 해당 결재상태의 항목을 조회합니다. "
                            + "일반 사용자는 항상 로그인 사용자 소속 부서 항목만 조회하고, 시스템관리자는 myDeptOnly=true일 때 부서 항목만 조회합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content =
                                @Content(schema = @Schema(implementation = CostDto.Response.class)))
            })
    @GetMapping
    public ResponseEntity<List<CostDto.Response>> getCostList(
            @ParameterObject @ModelAttribute CostDto.SearchCondition condition,
            @ParameterObject @ModelAttribute ListPageParams paging,
            @AuthenticationPrincipal CustomUserDetails user) {
        List<CostDto.Response> body = costService.searchCostList(condition, user, paging);
        if (!paging.isPaged()) {
            return ResponseEntity.ok(body);
        }
        // 건수도 목록과 같은 부서 범위로 집계한다 (범위가 다르면 총건수와 실제 조회 가능 건수가 어긋난다)
        return ResponseEntity.ok()
                .header(
                        ListPageParams.TOTAL_COUNT_HEADER,
                        String.valueOf(costService.countCostList(condition, user)))
                .body(body);
    }

    /**
     * 신규 전산관리비 생성
     *
     * <p>새로운 전산관리비 항목을 생성합니다.
     *
     * <p>관리번호 생성 규칙:
     *
     * <ul>
     *   <li>요청에 itMngcNo 값이 없으면 시퀀스(SQ_TPRMPP_BCOSTM_1)로 자동 생성
     *   <li>형식: {@code COST_{연도}_{4자리 시퀀스}} (예: {@code COST_2026_0001})
     * </ul>
     *
     * @param request 전산관리비 생성 요청 ({@link CostDto.CreateRequest})
     * @return HTTP 201 Created + Location 헤더 + 생성된 전산관리비 관리번호(IT_MNGC_NO)
     */
    @Operation(summary = "신규 전산관리비 생성", description = "새로운 전산관리비를 생성합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "201",
                        description = "생성 성공 (반환값: IT_MNGC_NO)",
                        content = @Content(schema = @Schema(implementation = String.class)))
            })
    @PostMapping
    public ResponseEntity<String> createCost(
            @Valid @RequestBody CostDto.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        String itMngcNo = costService.createCost(request, user);
        return ResponseEntity.created(URI.create("/api/cost/" + itMngcNo)).body(itMngcNo);
    }

    /**
     * 전산관리비 일괄 조회
     *
     * <p>여러 전산관리비 관리번호를 한 번에 조회합니다. 존재하지 않는 관리번호는 결과에서 제외됩니다.
     *
     * @param request 조회할 전산관리비 관리번호 목록 ({@link CostDto.BulkGetRequest})
     * @return HTTP 200 + 전산관리비 목록 (존재하는 항목만 포함)
     */
    @Operation(summary = "전산관리비 일괄 조회", description = "여러 개의 전산관리비 관리번호로 상세 정보를 일괄 조회합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                CostDto.BulkResponse.class)))
            })
    @PostMapping("/bulk-get")
    public ResponseEntity<CostDto.BulkResponse> getCostsByIds(
            @RequestBody CostDto.BulkGetRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(costService.getCostsByIds(request, user));
    }

    /**
     * 단말기 서비스명 입력 후보 목록 조회
     *
     * <p>금융정보단말기 상세목록의 [단말기 서비스(옵션)] 칸에서 직접 입력 대신 고를 수 있는 값을 제공합니다. 최근 3개 예산연도에 등록된 단말기의 서비스명을 중복
     * 제거하여 사용 빈도 내림차순으로 반환합니다.
     *
     * @param tmnClsfC 단말기종류 코드 (생략 시 종류 구분 없이 집계)
     * @return HTTP 200 + 서비스명 목록 (이력이 없으면 빈 배열)
     */
    @Operation(
            summary = "단말기 서비스명 후보 조회",
            description = "최근 3개 예산연도 단말기의 서비스명(SPF_TMN_NM)을 중복 제거·빈도 내림차순으로 반환합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content = @Content(array = @ArraySchema(schema = @Schema(type = "string"))))
            })
    @GetMapping("/terminals/service-names")
    public ResponseEntity<List<String>> getTerminalServiceNames(
            @Parameter(description = "단말기종류 코드 (공통코드 IT_PTL_TMN_SVC_TC)", example = "01")
                    @RequestParam(value = "tmnClsfC", required = false)
                    String tmnClsfC) {
        return ResponseEntity.ok(costService.getTerminalServiceNames(tmnClsfC));
    }
}
