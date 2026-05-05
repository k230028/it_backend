package com.kdb.it.domain.budget.work.controller;

import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.service.BudgetWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 예산 작업 REST 컨트롤러
 *
 * <p>예산 편성률 적용(TAAABB_BBUGTM)의 CRUD 기능을 담당합니다.</p>
 *
 * <p>기본 URL: {@code /api/budget/work}</p>
 *
 * <p>
 * 제공 API:
 * </p>
 * <ul>
 * <li>GET /ioe-categories: 편성비목 목록 조회 (DUP_IOE 코드 + 결재완료 요청금액)</li>
 * <li>POST /apply: 편성률 일괄 적용 (Upsert BBUGTM)</li>
 * <li>GET /summary: 편성 결과 조회 (비목별 요청금액/편성금액 집계)</li>
 * </ul>
 *
 * // Design Ref: §4.2 — BudgetWorkController (REST 엔드포인트 3개)
 */
@RestController
@RequestMapping("/api/budget/work")
@RequiredArgsConstructor
@Tag(name = "BudgetWork", description = "예산 작업 API")
public class BudgetWorkController {

    /** 예산 작업 비즈니스 로직 서비스 */
    private final BudgetWorkService budgetWorkService;

    /**
     * 편성비목 목록 조회 (API-01)
     *
     * <p>
     * CCODEM에서 CTT_TP='DUP_IOE'인 편성비목을 조회하고,
     * 각 비목별 결재완료 요청금액 합계와 기존 편성률을 반환합니다.
     * </p>
     *
     * @param bgYy 예산년도 (예: 2026)
     * @return HTTP 200 + 편성비목 목록
     */
    @Operation(summary = "편성비목 목록 조회",
            description = "편성비목(DUP_IOE) 코드 목록과 각 비목별 결재완료 요청금액 합계를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = BudgetWorkDto.IoeCategoryResponse.class)))
    })
    @GetMapping("/ioe-categories")
    public ResponseEntity<List<BudgetWorkDto.IoeCategoryResponse>> getIoeCategories(
            @Parameter(description = "예산년도", required = true, example = "2026")
            @RequestParam("bgYy") String bgYy) {
        return ResponseEntity.ok(budgetWorkService.getIoeCategories(bgYy));
    }

    /**
     * 편성률 일괄 적용 (API-02)
     *
     * <p>
     * 비목별 편성률을 결재완료 원본 데이터에 적용하여 BBUGTM에 저장합니다.
     * 기존 레코드가 있으면 UPDATE, 없으면 INSERT (Upsert 패턴).
     * </p>
     *
     * @param request 편성률 적용 요청 (예산년도 + 비목별 편성률 목록)
     * @return HTTP 200 + 적용 결과 (처리 메시지, 레코드 수, 요약)
     */
    @Operation(summary = "편성률 일괄 적용",
            description = """
                    예산년도와 비목별 편성률을 받아 결재완료 원본 데이터에 적용합니다.

                    - 원본 데이터: 정보화사업 품목(BITEMM) 및 전산업무비(BCOSTM) 중 결재완료 건
                    - 처리 방식: 기존 편성 데이터가 있으면 수정하고, 없으면 신규 생성합니다.
                    - 편성률: 0~100 정수이며 비목 코드(DUP_IOE) 기준으로 매칭합니다.
                    - 저장 대상: TAAABB_BBUGTM
                    """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "적용 성공",
                    content = @Content(schema = @Schema(implementation = BudgetWorkDto.ApplyResponse.class)))
    })
    @PostMapping("/apply")
    public ResponseEntity<BudgetWorkDto.ApplyResponse> applyRates(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "편성률 일괄 적용 요청. bgYy와 rates 목록을 전달합니다.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = BudgetWorkDto.ApplyRequest.class),
                            examples = @ExampleObject(
                                    name = "비목별 편성률 적용 예시",
                                    value = """
                                            {
                                              "bgYy": "2026",
                                              "rates": [
                                                {
                                                  "cdId": "DUP-IOE-237",
                                                  "dupRt": 80
                                                },
                                                {
                                                  "cdId": "DUP-IOE-238",
                                                  "dupRt": 65
                                                }
                                              ]
                                            }
                                            """)))
            @RequestBody BudgetWorkDto.ApplyRequest request) {
        return ResponseEntity.ok(budgetWorkService.applyRates(request));
    }

    /**
     * 사업별 편성률 적용 (API-05, REQ-2)
     *
     * <p>
     * 각 사업별로 자본예산/일반관리비 편성률을 분리 적용합니다.
     * </p>
     *
     * @param request 사업별 편성률 적용 요청 (예산년도 + 사업별 편성률 목록)
     * @return HTTP 200 + 적용 결과 (처리 메시지, 레코드 수, 요약)
     */
    @Operation(summary = "사업별 편성률 적용",
            description = """
                    사업 또는 전산업무비 단위로 자본예산/일반관리비 편성률을 분리 적용합니다.

                    - BPROJM 원본은 자본예산(assetDupRt)과 일반관리비(costDupRt)를 함께 적용할 수 있습니다.
                    - BCOSTM 원본은 일반관리비(costDupRt) 중심으로 적용합니다.
                    - 동일 원본 PK의 기존 편성 데이터는 갱신하고, 미존재 시 신규 생성합니다.
                    - 저장 대상: TAAABB_BBUGTM
                    """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "적용 성공",
                    content = @Content(schema = @Schema(implementation = BudgetWorkDto.ApplyResponse.class)))
    })
    @PostMapping("/apply-items")
    public ResponseEntity<BudgetWorkDto.ApplyResponse> applyItemRates(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "사업별 편성률 적용 요청. 원본 테이블, 원본 PK, 자본/일반관리비 편성률을 전달합니다.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = BudgetWorkDto.ItemApplyRequest.class),
                            examples = @ExampleObject(
                                    name = "사업별 편성률 적용 예시",
                                    value = """
                                            {
                                              "bgYy": "2026",
                                              "items": [
                                                {
                                                  "orcTb": "BPROJM",
                                                  "orcPkVl": "PRJ-2026-0001",
                                                  "assetDupRt": 90,
                                                  "costDupRt": 70
                                                },
                                                {
                                                  "orcTb": "BCOSTM",
                                                  "orcPkVl": "COST-2026-0001",
                                                  "assetDupRt": null,
                                                  "costDupRt": 80
                                                }
                                              ]
                                            }
                                            """)))
            @RequestBody BudgetWorkDto.ItemApplyRequest request) {
        return ResponseEntity.ok(budgetWorkService.applyItemRates(request));
    }

    /**
     * 편성 결과 조회 (API-03)
     *
     * <p>
     * BBUGTM에서 예산년도별 편성 결과를 비목 접두어 기준으로 집계하여 반환합니다.
     * </p>
     *
     * @param bgYy 예산년도 (예: 2026)
     * @return HTTP 200 + 편성 결과 요약 (비목별 요청금액/편성금액 + 합계)
     */
    @Operation(summary = "편성 결과 조회",
            description = "예산년도별 편성 결과를 비목 접두어 기준으로 집계하여 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = BudgetWorkDto.SummaryResponse.class)))
    })
    @GetMapping("/summary")
    public ResponseEntity<BudgetWorkDto.SummaryResponse> getSummary(
            @Parameter(description = "예산년도", required = true, example = "2026")
            @RequestParam("bgYy") String bgYy) {
        return ResponseEntity.ok(budgetWorkService.getSummary(bgYy));
    }

    /**
     * 사업별 편성 결과 조회 (API-04)
     *
     * <p>
     * BBUGTM에서 예산년도별 편성 결과를 사업(원본PK) 기준으로 집계하여 반환합니다.
     * </p>
     *
     * @param bgYy 예산년도 (예: 2026)
     * @return HTTP 200 + 사업별 편성 결과 요약 (사업명, 요청금액, 편성금액 + 합계)
     */
    @Operation(summary = "사업별 편성 결과 조회",
            description = "예산년도별 편성 결과를 사업(정보화사업/전산업무비)별로 집계하여 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = BudgetWorkDto.ProjectSummaryResponse.class)))
    })
    @GetMapping("/project-summary")
    public ResponseEntity<BudgetWorkDto.ProjectSummaryResponse> getProjectSummary(
            @Parameter(description = "예산년도", required = true, example = "2026")
            @RequestParam("bgYy") String bgYy) {
        return ResponseEntity.ok(budgetWorkService.getProjectSummary(bgYy));
    }
}
