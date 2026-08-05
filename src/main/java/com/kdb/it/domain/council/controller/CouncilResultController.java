package com.kdb.it.domain.council.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CouncilApprovalService;
import com.kdb.it.domain.council.service.CouncilService;
import com.kdb.it.domain.council.service.ResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정보화실무협의회 결과서·통보 REST 컨트롤러 (M7)
 *
 * <p>기본 URL: {@code /api/council} — URL은 다른 협의회 컨트롤러와 공유하고 클래스만 책임별로 분리했습니다(CQ-01).
 *
 * <p>담당 범위: 결과서 CRUD·확정({@code /{asctId}/result}), 위원 검토({@code /result/review}), 결재 요청({@code
 * /result/approval}), 결과 통보({@code /{asctId}/notify}).
 *
 * <p>{@code POST /{asctId}/result/review/sync}는 메서드 수준 {@code @PreAuthorize("hasRole('ADMIN')")}로
 * 보호합니다. 원본에서 옮길 때 이 애노테이션을 반드시 함께 가져가십시오.
 */
@RestController
@RequestMapping("/api/council")
@RequiredArgsConstructor
@Tag(name = "Council", description = "정보화실무협의회 관리 API")
public class CouncilResultController {

    /** 결과서 서비스 */
    private final ResultService resultService;

    /** 협의회 기본 서비스 (결과서 저장·확정 시 상태 조회·전이에 사용) */
    private final CouncilService councilService;

    /** 협의회 결재 연동 서비스 */
    private final CouncilApprovalService councilApprovalService;

    /**
     * 결과서 조회 (IT관리자)
     *
     * <p>결과서 내용(종합의견, 타당성검토의견, 첨부파일)과 점검항목별 평균점수를 함께 반환합니다. 아직 작성 전이면 avgScores만 채워진 빈 결과서를 반환합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 결과서 내용 + 항목별 평균점수
     */
    @Operation(summary = "결과서 조회", description = "결과서 내용과 점검항목별 평균점수를 조회합니다.")
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
                                                                CouncilDto.ResultResponse.class))),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/result")
    public ResponseEntity<CouncilDto.ResultResponse> getResult(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(resultService.getResult(asctId));
    }

    /**
     * 결과서 저장 (IT관리자)
     *
     * <p>종합의견, 타당성검토의견, 첨부파일을 저장합니다. 최초 저장 시 협의회 상태를 EVALUATING → RESULT_WRITING으로 전이합니다.
     *
     * @param asctId 협의회ID
     * @param request 결과서 작성 요청
     * @return HTTP 200
     */
    @Operation(summary = "결과서 저장", description = "결과서를 저장합니다. 최초 저장 시 RESULT_WRITING으로 상태 전이.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "저장 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PostMapping("/{asctId}/result")
    public ResponseEntity<Void> saveResult(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.ResultRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        resultService.saveResult(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 결과서 수정 (IT관리자)
     *
     * <p>POST와 동일한 로직으로 upsert 처리합니다.
     *
     * @param asctId 협의회ID
     * @param request 결과서 수정 요청
     * @return HTTP 200
     */
    @Operation(summary = "결과서 수정", description = "기존 결과서를 수정합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "수정 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PutMapping("/{asctId}/result")
    public ResponseEntity<Void> updateResult(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.ResultRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        resultService.saveResult(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 결과서 확정 (IT관리자)
     *
     * <p>작성 완료된 결과서를 확정하고 협의회 상태를 RESULT_REVIEW로 전이합니다. RESULT_REVIEW 단계에서 평가위원들이 결과서를 최종 검토합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "결과서 확정", description = "결과서를 확정하고 상태를 RESULT_REVIEW로 전이합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "확정 성공"),
                @ApiResponse(responseCode = "400", description = "결과서 미작성", content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PutMapping("/{asctId}/result/confirm")
    public ResponseEntity<Void> confirmResult(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        resultService.confirmResult(asctId);
        return ResponseEntity.ok().build();
    }

    /**
     * 평가위원 결과서 검토 확인 (평가위원)
     *
     * <p>RESULT_REVIEW 상태에서 평가위원(MAND/CALL)이 결과서 확인 완료를 처리합니다. 전원 확인 완료 시 협의회 상태가 FINAL_APPROVAL로
     * 자동 전이됩니다.
     *
     * @param asctId 협의회ID
     * @param userDetails 로그인한 평가위원
     * @return HTTP 200
     */
    @Operation(
            summary = "결과서 검토 확인",
            description = "평가위원이 결과서를 확인합니다. 전원 완료 시 FINAL_APPROVAL 자동 전이.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "확인 처리 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "RESULT_REVIEW 상태 아님",
                        content = @Content),
                @ApiResponse(
                        responseCode = "403",
                        description = "평가위원 아님 또는 간사",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PostMapping("/{asctId}/result/review")
    public ResponseEntity<Void> reviewResult(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        resultService.reviewResult(asctId, userDetails);
        return ResponseEntity.ok().build();
    }

    /**
     * 결과서 검토 진행상황 동기화 (010 → 011 자동 전이 트리거)
     *
     * <p>평가위원(간사 제외) 전원의 CNFM_YN이 'Y'면 협의회 상태를 RESULT_REVIEW → FINAL_APPROVAL로 전이합니다. 데이터를 직접 수정한
     * 경우 또는 화면 진입 시점에 호출해 자동 전이가 누락되지 않도록 보장합니다.
     *
     * <p>상태를 임의로 전이시킬 수 있는 관리 성격의 API이므로 서버에서 관리자 권한을 강제합니다. 프론트 라우트 가드는 UX 보조일 뿐 보안 경계가 아닙니다.
     *
     * @param asctId 협의회ID
     * @return 이번 호출에서 결과 승인 대기 상태로 전이했으면 true
     * @throws IllegalArgumentException 협의회가 없는 경우
     */
    @Operation(summary = "검토 진행상황 동기화", description = "위원 전원 확인 시 010→011 전이를 보장합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{asctId}/result/review/sync")
    public ResponseEntity<Boolean> syncReviewStatus(@PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(resultService.syncReviewStatus(asctId));
    }

    /**
     * 본인 결과서 검토 확인 여부 조회 (평가위원)
     *
     * <p>페이지 진입 시 이미 결과서 확인을 완료했는지 조회합니다. 완료 시 버튼 대신 완료 UI를 표시하는 데 사용합니다.
     *
     * @param asctId 협의회ID
     * @param userDetails 로그인한 평가위원
     * @return true: 이미 확인 완료, false: 미확인
     */
    @Operation(summary = "본인 결과서 확인 여부 조회", description = "평가위원 본인의 결과서 검토 확인 여부를 조회합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "조회 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/result/review/my")
    public ResponseEntity<Boolean> getMyResultReview(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(resultService.getMyReviewStatus(asctId, userDetails));
    }

    /**
     * 개최결과서 결재 요청 (IT관리자)
     *
     * <p>FINAL_APPROVAL 상태에서 IT관리자가 부장에게 결재를 요청합니다. 전자결재 시스템에 신청서를 등록하고 협의회 상태를
     * RESULT_APPROVAL_PENDING으로 전이합니다.
     *
     * @param asctId 협의회ID
     * @param request 결재 요청 (부장 사번, 신청의견)
     * @param userDetails 신청자 정보
     * @return HTTP 200 + 신청관리번호 (APF_... 형식)
     */
    @Operation(
            summary = "개최결과서 결재 요청",
            description = "부장에게 개최결과서 결재를 요청합니다. FINAL_APPROVAL 상태에서만 가능합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "결재 요청 성공",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                CouncilDto.ApprovalResponse
                                                                        .class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "FINAL_APPROVAL 상태 아님",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PostMapping("/{asctId}/result/approval")
    public ResponseEntity<CouncilDto.ApprovalResponse> requestResultApproval(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.ResultApprovalRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        CouncilDto.ApprovalResponse response =
                councilApprovalService.requestResultApproval(asctId, request, userDetails);
        return ResponseEntity.ok(response);
    }

    /**
     * 추진부서 통보 처리 (IT관리자)
     *
     * <p>협의회가 완료된 후 IT관리자가 추진부서 담당자에게 결과를 통보합니다. 사업 상태(BPROJM.PRJ_STS)를 '요건 상세화'로 변경합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "추진부서 통보", description = "협의회 결과를 추진부서에 통보합니다. COMPLETED 상태에서만 가능합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "통보 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "COMPLETED 상태 아님",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PostMapping("/{asctId}/notify")
    public ResponseEntity<CouncilDto.NotifyResponse> notifyCouncil(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        CouncilDto.NotifyResponse response = councilService.notifyCouncil(asctId);
        return ResponseEntity.ok(response);
    }
}
