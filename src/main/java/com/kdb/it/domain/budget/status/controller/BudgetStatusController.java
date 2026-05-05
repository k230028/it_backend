package com.kdb.it.domain.budget.status.controller;

import com.kdb.it.domain.budget.status.dto.BudgetStatusDto;
import com.kdb.it.domain.budget.status.service.BudgetStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 예산 현황 컨트롤러
 *
 * <p>
 * 3개 탭(정보화사업/전산업무비/경상사업)의 예산 현황 조회 API를 제공합니다.
 * 각 탭은 편성요청 금액과 조정(편성) 금액을 병렬로 포함합니다.
 * </p>
 *
 * // Design Ref: §3.6 — BudgetStatusController 설계
 */
@RestController
@RequestMapping("/api/budget/status")
@RequiredArgsConstructor
@Tag(name = "BudgetStatus", description = "예산 현황 API")
public class BudgetStatusController {

    private final BudgetStatusService budgetStatusService;

    /**
     * 정보화사업 예산 현황 조회
     *
     * @param bgYy 예산년도 (예: 2026)
     * @return 정보화사업별 편성요청/조정 금액 목록
     */
    @Operation(summary = "정보화사업 예산 현황 조회",
            description = """
                    예산년도 기준 정보화사업의 편성요청 금액과 조정(편성) 금액을 함께 조회합니다.

                    - 조회 대상: TAAABB_BPROJM, TAAABB_BITEMM, TAAABB_BBUGTM
                    - 금액 구분: 개발비/기계장치/기타무형자산/임차료/여비/용역비/기타/합계
                    - 화면 용도: 예산 현황 화면의 '정보화사업' 탭
                    """,
            responses = @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = BudgetStatusDto.ProjectResponse.class),
                            examples = @ExampleObject(
                                    name = "정보화사업 예산 현황 응답 예시",
                                    value = """
                                            [
                                              {
                                                "prjMngNo": "PRJ-2026-0001",
                                                "prjNm": "차세대 IT 포털 구축",
                                                "svnDpmNm": "디지털기획부",
                                                "reqTotalBg": 1200000000,
                                                "adjTotalBg": 960000000
                                              }
                                            ]
                                            """))))
    @GetMapping("/projects")
    public ResponseEntity<List<BudgetStatusDto.ProjectResponse>> getProjects(
            @Parameter(description = "조회할 예산년도(YYYY)", required = true, example = "2026")
            @RequestParam("bgYy") String bgYy) {
        return ResponseEntity.ok(budgetStatusService.getProjectStatus(bgYy));
    }

    /**
     * 전산업무비 예산 현황 조회
     *
     * @param bgYy 예산년도 (예: 2026)
     * @return 전산업무비별 편성요청/조정 금액 목록
     */
    @Operation(summary = "전산업무비 예산 현황 조회",
            description = """
                    예산년도 기준 전산업무비의 편성요청 금액과 조정(편성) 금액을 조회합니다.

                    - 조회 대상: TAAABB_BCOSTM, TAAABB_BBUGTM
                    - 금액 구분: 임차료/여비/용역비/기타/합계
                    - 화면 용도: 예산 현황 화면의 '전산업무비' 탭
                    """,
            responses = @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = BudgetStatusDto.CostResponse.class),
                            examples = @ExampleObject(
                                    name = "전산업무비 예산 현황 응답 예시",
                                    value = """
                                            [
                                              {
                                                "itMngcNo": "COST-2026-0001",
                                                "cttNm": "2026년 서버 유지보수 계약",
                                                "biceDpm": "D001",
                                                "reqTotalBg": 300000000,
                                                "adjTotalBg": 240000000
                                              }
                                            ]
                                            """))))
    @GetMapping("/costs")
    public ResponseEntity<List<BudgetStatusDto.CostResponse>> getCosts(
            @Parameter(description = "조회할 예산년도(YYYY)", required = true, example = "2026")
            @RequestParam("bgYy") String bgYy) {
        return ResponseEntity.ok(budgetStatusService.getCostStatus(bgYy));
    }

    /**
     * 경상사업 예산 현황 조회
     *
     * @param bgYy 예산년도 (예: 2026)
     * @return 경상사업별 기계장치/기타무형자산 상세 목록
     */
    @Operation(summary = "경상사업 예산 현황 조회",
            description = """
                    예산년도 기준 경상사업의 품목별 기계장치/기타무형자산 금액을 조회합니다.

                    - 조회 대상: TAAABB_BPROJM 중 경상사업, TAAABB_BITEMM
                    - 금액 구분: 수량, 단가, 원화 환산금액
                    - 화면 용도: 예산 현황 화면의 '경상사업' 탭
                    """,
            responses = @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = BudgetStatusDto.OrdinaryResponse.class),
                            examples = @ExampleObject(
                                    name = "경상사업 예산 현황 응답 예시",
                                    value = """
                                            [
                                              {
                                                "prjMngNo": "PRJ-2026-0100",
                                                "prjNm": "노후 장비 교체",
                                                "machCur": "KRW",
                                                "machAmtKrw": 150000000,
                                                "intanAmtKrw": 50000000
                                              }
                                            ]
                                            """))))
    @GetMapping("/ordinary")
    public ResponseEntity<List<BudgetStatusDto.OrdinaryResponse>> getOrdinary(
            @Parameter(description = "조회할 예산년도(YYYY)", required = true, example = "2026")
            @RequestParam("bgYy") String bgYy) {
        return ResponseEntity.ok(budgetStatusService.getOrdinaryStatus(bgYy));
    }
}
