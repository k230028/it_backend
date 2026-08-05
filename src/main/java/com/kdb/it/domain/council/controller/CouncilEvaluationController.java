package com.kdb.it.domain.council.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.EvaluationService;
import com.kdb.it.domain.council.service.PlanEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정보화실무협의회 평가의견 REST 컨트롤러 (M7)
 *
 * <p>기본 URL: {@code /api/council} — URL은 다른 협의회 컨트롤러와 공유하고 클래스만 책임별로 분리했습니다(CQ-01).
 *
 * <p>담당 범위: 일반 협의회 평가의견({@code /{asctId}/evaluation})과 정보기술부문계획 협의회(dbrTc='02')의 심의 대상·사업별
 * 적정/유보({@code /{asctId}/plan-targets}, {@code /{asctId}/plan-evaluation}).
 */
@RestController
@RequestMapping("/api/council")
@RequiredArgsConstructor
@Tag(name = "Council", description = "정보화실무협의회 관리 API")
public class CouncilEvaluationController {

    /** 평가의견 서비스 */
    private final EvaluationService evaluationService;

    /** 계획협의회 평가 서비스 */
    private final PlanEvaluationService planEvaluationService;

    /**
     * 평가의견 전체 현황 조회 (IT관리자)
     *
     * <p>전체 위원의 6개 항목 평가의견과 항목별 평균점수를 반환합니다. 결과서 작성 시 참고 데이터로 활용합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 위원별 평가의견 + 항목별 평균점수
     */
    @Operation(summary = "평가의견 전체 현황 조회", description = "전체 위원의 평가의견과 항목별 평균점수를 조회합니다.")
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
                                                                CouncilDto.EvaluationSummaryResponse
                                                                        .class))),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/evaluation")
    public ResponseEntity<CouncilDto.EvaluationSummaryResponse> getAllEvaluations(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(evaluationService.getAllEvaluations(asctId));
    }

    /**
     * 내 평가의견 조회 (로그인한 평가위원 본인)
     *
     * <p>로그인한 평가위원이 이미 제출한 6개 항목의 평가의견을 반환합니다. 아직 제출 이력이 없으면 빈 배열을 반환합니다.
     *
     * @param asctId 협의회ID
     * @param userDetails 로그인한 평가위원
     * @return 내 평가의견 목록 (최대 6개, 없으면 빈 배열)
     */
    @Operation(summary = "내 평가의견 조회", description = "로그인한 평가위원 본인의 평가의견을 조회합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "조회 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/evaluation/my")
    public ResponseEntity<List<CouncilDto.EvaluationItemResponse>> getMyEvaluation(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(evaluationService.getMyEvaluation(asctId, userDetails));
    }

    /**
     * 평가의견 작성/수정 (평가위원)
     *
     * <p>6개 점검항목에 대한 점수와 의견을 저장합니다. 1~2점 입력 시 의견 작성이 필수입니다. 첫 제출 시 협의회 상태를 IN_PROGRESS →
     * EVALUATING으로 전이합니다.
     *
     * @param asctId 협의회ID
     * @param request 평가의견 요청 (6개 항목)
     * @param userDetails 로그인한 평가위원
     * @return HTTP 200
     */
    @Operation(summary = "평가의견 작성", description = "6개 점검항목에 대한 점수와 의견을 저장합니다. 1~2점 시 의견 필수.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "저장 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "1~2점인데 의견 미작성",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PostMapping("/{asctId}/evaluation")
    public ResponseEntity<Void> saveEvaluation(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.EvaluationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        evaluationService.saveEvaluation(asctId, request, userDetails);
        return ResponseEntity.ok().build();
    }

    /**
     * 계획협의회 심의 대상 조회 (dbrTc='02')
     *
     * <p>협의회에 연결된 계획(BPLANM)의 상세(사업 카드·예산 스냅샷)를 반환합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 대상 계획 상세
     */
    @Operation(
            summary = "계획협의회 심의 대상 조회",
            description = "dbrTc='02' 협의회의 대상 계획 상세(사업 카드·예산)를 조회합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "조회 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/plan-targets")
    public ResponseEntity<CouncilDto.PlanTargetsResponse> getPlanTargets(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(planEvaluationService.getPlanTargets(asctId));
    }

    /**
     * 계획협의회 평가 현황 조회 (IT관리자)
     *
     * <p>위원별 사업 평가 목록과 사업별 최종 판정(위원 1명이라도 유보면 유보)을 반환합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 평가 현황 + 사업별 판정
     */
    @Operation(summary = "계획협의회 평가 현황 조회", description = "위원별 사업 평가와 사업별 최종 판정을 조회합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "조회 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/plan-evaluation")
    public ResponseEntity<CouncilDto.PlanEvaluationSummaryResponse> getPlanEvaluations(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(planEvaluationService.getAllEvaluations(asctId));
    }

    /**
     * 내 계획협의회 평가 조회 (평가위원 본인)
     *
     * @param asctId 협의회ID
     * @param userDetails 로그인한 평가위원
     * @return HTTP 200 + 내 사업별 적정/유보 목록 (없으면 빈 배열)
     */
    @Operation(summary = "내 계획협의회 평가 조회", description = "로그인한 평가위원 본인의 사업별 적정/유보를 조회합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "조회 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/plan-evaluation/my")
    public ResponseEntity<List<CouncilDto.PlanEvaluationItemResponse>> getMyPlanEvaluation(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(planEvaluationService.getMyEvaluation(asctId, userDetails));
    }

    /**
     * 계획협의회 사업별 적정/유보 저장 (평가위원)
     *
     * <p>각 사업에 대해 적정/유보(Y/N)와 사유를 저장합니다. 사유는 필수입니다. 첫 제출 시 협의회 상태를 07 → 08로 전이합니다.
     *
     * @param asctId 협의회ID
     * @param request 사업별 적정/유보 요청
     * @param userDetails 로그인한 평가위원
     * @return HTTP 200
     */
    @Operation(
            summary = "계획협의회 사업별 적정/유보 저장",
            description = "평가위원이 사업별 적정/유보와 사유를 저장합니다. 첫 제출 시 상태 08로 전이.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "저장 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "적정여부 값 오류 또는 사유 미작성",
                        content = @Content),
                @ApiResponse(
                        responseCode = "403",
                        description = "해당 협의회 평가위원 아님",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PostMapping("/{asctId}/plan-evaluation")
    public ResponseEntity<Void> savePlanEvaluation(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.PlanEvaluationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        planEvaluationService.saveEvaluation(asctId, request, userDetails);
        return ResponseEntity.ok().build();
    }

    /**
     * 계획협의회 결과서 프리필 요약 (IT관리자)
     *
     * <p>사업별 판정 요약 표(HTML)와 구조화 판정을 반환해 결과서 본문 프리필에 사용합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 요약 HTML + 사업별 판정
     */
    @Operation(
            summary = "계획협의회 결과서 프리필 요약",
            description = "사업별 판정 요약 표(HTML)를 결과서 본문 프리필용으로 반환합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "조회 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/plan-evaluation/result-summary")
    public ResponseEntity<CouncilDto.PlanResultSummaryResponse> getPlanResultSummary(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(planEvaluationService.buildResultSummary(asctId));
    }
}
