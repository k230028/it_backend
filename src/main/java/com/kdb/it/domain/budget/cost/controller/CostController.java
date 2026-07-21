package com.kdb.it.domain.budget.cost.controller;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.service.CostService;
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

    /**
     * 특정 전산관리비 단건 조회
     *
     * <p>전산관리비 관리번호(IT_MNGC_NO)로 해당 전산관리비의 상세 정보를 조회합니다. 복합키 구조이므로 같은 관리번호에 여러 일련번호가 존재할 수 있으며, 이
     * 경우 LST_YN='Y'인 최신 항목을 반환합니다.
     *
     * @param itMngcNo 전산관리비 관리번호 (예: {@code COST_2026_0001})
     * @return HTTP 200 + 전산관리비 상세 정보, HTTP 404 전산관리비가 없는 경우
     */
    @Operation(summary = "특정 전산관리비 조회", description = "전산관리비 관리번호(IT_MNGC_NO)로 전산관리비 상세 정보를 조회합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content =
                                @Content(
                                        schema = @Schema(implementation = CostDto.Response.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "존재하지 않는 전산관리비",
                        content = @Content)
            })
    @GetMapping("/{itMngcNo}")
    public ResponseEntity<CostDto.Response> getCost(
            @Parameter(description = "전산관리비 관리번호", required = true, example = "COST_2026_0001")
                    @PathVariable("itMngcNo")
                    String itMngcNo) {
        return ResponseEntity.ok(costService.getCost(itMngcNo));
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
                        content = @Content)
            })
    @PutMapping("/{itMngcNo}")
    public ResponseEntity<String> updateCost(
            @Parameter(description = "전산관리비 관리번호", required = true, example = "COST_2026_0001")
                    @PathVariable("itMngcNo")
                    String itMngcNo,
            @Valid @RequestBody CostDto.UpdateRequest request) {
        return ResponseEntity.ok(costService.updateCost(itMngcNo, request));
    }

    /**
     * 전산관리비 삭제 (Soft Delete)
     *
     * <p>전산관리비를 물리적으로 삭제하지 않고, DEL_YN 컬럼을 'Y'로 변경하여 논리 삭제(Soft Delete)를 수행합니다. 삭제된 항목은 조회에서 제외됩니다.
     *
     * @param itMngcNo 삭제할 전산관리비 관리번호
     * @return HTTP 200 (본문 없음), HTTP 404 전산관리비가 없는 경우
     */
    @Operation(summary = "전산관리비 삭제", description = "전산관리비를 삭제(Soft Delete)합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "존재하지 않는 전산관리비",
                        content = @Content)
            })
    @DeleteMapping("/{itMngcNo}")
    public ResponseEntity<Void> deleteCost(
            @Parameter(description = "전산관리비 관리번호", required = true, example = "COST_2026_0001")
                    @PathVariable("itMngcNo")
                    String itMngcNo) {
        costService.deleteCost(itMngcNo);
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
     *   <li>{@code GET /api/cost} → 전체 조회
     *   <li>{@code GET /api/cost?apfSts=none} → 신청서가 없는 전산관리비만
     *   <li>{@code GET /api/cost?apfSts=결재중} → 결재중인 전산관리비만
     * </ul>
     *
     * @param condition 검색 조건 (apfSts, biceDpmC, biceTemC, infPrtYn). 미입력 시 전체 조회
     * @return HTTP 200 + 전산관리비 목록 ({@link CostDto.Response} 리스트)
     */
    @Operation(
            summary = "전산관리비 목록 조회",
            description =
                    "전산관리비 목록을 조회합니다. "
                            + "Query Parameter로 조건을 지정하면 필터링된 결과를 반환합니다. "
                            + "apfSts=none은 신청서가 없는 항목, "
                            + "apfSts=결재중/결재완료 등은 해당 결재상태의 항목을 조회합니다.")
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
            @ModelAttribute CostDto.SearchCondition condition) {
        return ResponseEntity.ok(costService.searchCostList(condition));
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
    public ResponseEntity<String> createCost(@Valid @RequestBody CostDto.CreateRequest request) {
        String itMngcNo = costService.createCost(request);
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
            @RequestBody CostDto.BulkGetRequest request) {
        return ResponseEntity.ok(costService.getCostsByIds(request));
    }
}
