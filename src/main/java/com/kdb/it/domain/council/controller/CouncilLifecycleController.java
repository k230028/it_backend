package com.kdb.it.domain.council.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CouncilApprovalService;
import com.kdb.it.domain.council.service.CouncilService;
import com.kdb.it.domain.council.service.CouncilSkipService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정보화실무협의회 상태 전이·결재·생략요청 REST 컨트롤러
 *
 * <p>기본 URL: {@code /api/council} — URL은 다른 협의회 컨트롤러와 공유하고 클래스만 책임별로 분리했습니다(CQ-01).
 *
 * <p>담당 범위: 결재 요청·콜백({@code /approval}), 상태 전이({@code /start}, {@code /complete}, {@code /skip},
 * {@code /start-preparation}), 생략요청({@code /skip-request}, {@code /skip-requests}).
 */
@RestController
@RequestMapping("/api/council")
@RequiredArgsConstructor
@Tag(name = "Council", description = "정보화실무협의회 관리 API")
public class CouncilLifecycleController {

    /** 협의회 기본 서비스 (목록/상태 관리) */
    private final CouncilService councilService;

    /** 협의회 결재 연동 서비스 */
    private final CouncilApprovalService councilApprovalService;

    /** 협의회 생략요청 서비스 */
    private final CouncilSkipService councilSkipService;

    /**
     * 타당성검토표 결재 요청 (소관부서 담당자 → 팀장)
     *
     * <p>SUBMITTED 상태인 협의회에 대해 팀장 결재를 요청합니다. 전자결재 시스템에 신청서를 등록하고 협의회 상태를 APPROVAL_PENDING으로 전이합니다.
     *
     * @param asctId 협의회ID
     * @param request 결재 요청 (팀장 사번, 신청의견)
     * @param userDetails 신청자 정보
     * @return HTTP 200 + 신청관리번호 (APF_... 형식)
     */
    @Operation(
            summary = "타당성검토표 결재 요청",
            description = "팀장에게 타당성검토표 결재를 요청합니다. SUBMITTED 상태에서만 가능합니다.")
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
                        description = "잘못된 상태 또는 요청",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PostMapping("/{asctId}/approval")
    public ResponseEntity<CouncilDto.ApprovalResponse> requestApproval(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.ApprovalRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CouncilDto.ApprovalResponse response =
                councilApprovalService.requestApproval(asctId, request, userDetails);
        return ResponseEntity.ok(response);
    }

    /**
     * 결재 완료/반려 콜백 처리
     *
     * <p>전자결재 시스템에서 팀장이 승인 또는 반려 처리 후 이 API로 협의회 상태를 업데이트합니다.
     *
     * <ul>
     *   <li>승인(approved=true): APPROVAL_PENDING → APPROVED
     *   <li>반려(approved=false): APPROVAL_PENDING → DRAFT (재작성)
     * </ul>
     *
     * @param asctId 협의회ID
     * @param request 콜백 요청 (approved: 승인/반려 여부)
     * @return HTTP 200
     */
    @Operation(summary = "결재 콜백 처리", description = "전자결재 시스템에서 결재 완료/반려 시 협의회 상태를 업데이트합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "상태 업데이트 성공"),
                @ApiResponse(responseCode = "400", description = "잘못된 상태", content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PatchMapping("/{asctId}/approval")
    public ResponseEntity<Void> processApprovalCallback(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.ApprovalCallbackRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyAdmin(userDetails);
        councilApprovalService.processApprovalCallback(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 협의회 개최 시작 (SCHEDULED → IN_PROGRESS)
     *
     * <p>IT관리자가 오프라인 협의회 개최를 확인하고 진행 상태로 전이합니다. SCHEDULED 상태에서만 호출 가능합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "협의회 개최 시작", description = "SCHEDULED 상태의 협의회를 IN_PROGRESS로 전이합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "개최 시작 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "SCHEDULED 상태가 아닌 경우",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PatchMapping("/{asctId}/start")
    public ResponseEntity<Void> startCouncil(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        councilService.startCouncil(asctId);
        return ResponseEntity.ok().build();
    }

    /**
     * 협의회 완료 처리 (IN_PROGRESS → RESULT_WRITING)
     *
     * <p>모든 평가위원의 평가 제출이 확인된 후 IT관리자가 호출합니다. 협의회 상태를 RESULT_WRITING으로 전이하여 개최결과서 작성 단계로 전환합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "협의회 완료 처리", description = "IN_PROGRESS 상태의 협의회를 RESULT_WRITING으로 전이합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "완료 처리 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "IN_PROGRESS 상태가 아닌 경우",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PatchMapping("/{asctId}/complete")
    public ResponseEntity<Void> completeCouncil(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        councilService.completeCouncil(asctId);
        return ResponseEntity.ok().build();
    }

    /**
     * 정보화실무협의회 생략 처리 (APPROVED(04) → 생략(99))
     *
     * <p>IT관리자가 타당성검토표 검토 후 협의회 생략 대상으로 판단한 경우 호출합니다. 협의회 상태를 생략(99)으로 전이하고, 사업 상태를 '요건 상세화'로
     * 변경합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "협의회 생략 처리", description = "APPROVED 상태에서 협의회를 생략하고 사업을 요건 상세화 단계로 전환합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "생략 처리 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "APPROVED 상태가 아닌 경우",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PatchMapping("/{asctId}/skip")
    public ResponseEntity<Void> skipCouncil(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 직접 생략은 IT관리자 전용(정보보호시스템 사업은 판정 요청→IT기획 결재를 거쳐야 함)
        councilService.verifyAdmin(userDetails);
        councilService.skipCouncil(asctId);
        return ResponseEntity.ok().build();
    }

    /**
     * 정보화실무협의회 개최준비 시작 (일반 협의회 04→05, 계획협의회 01→05)
     *
     * <p>IT관리자가 타당성검토표 검토 후 '개최준비 진행'을 선택한 경우 호출합니다. 일반 협의회는 04→05, 타당성검토·결재 단계가 없는 계획협의회({@code
     * dbrTc=02})는 01→05로 전이합니다. 평가위원 저장의 부수효과가 아닌 명시적 액션으로 분리했습니다. (PRD_c_20260620 #2)
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "협의회 개최준비 시작", description = "APPROVED(04) 상태에서 협의회를 개최준비(05) 단계로 전이합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "개최준비 전이 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "APPROVED 상태가 아닌 경우",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PatchMapping("/{asctId}/start-preparation")
    public ResponseEntity<Void> startPreparation(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        councilService.startPreparation(asctId);
        return ResponseEntity.ok().build();
    }

    /**
     * 타당성검토 생략 판정 요청 등록 (정보보호기획 ITPAD002)
     *
     * <p>결재완료(04) 정보보호시스템(dbrTc=04) 협의회에 대해 생략 사유·설명·첨부(사업계획서/타당성검토표)를 담아 IT기획에 생략 판정을 요청합니다. 협의회
     * 상태는 04를 유지합니다.
     *
     * @param asctId 협의회ID
     * @param request 생략 판정 요청 (사유코드/설명/첨부 2종)
     * @param userDetails 요청자 (정보보호관리자)
     * @return HTTP 200
     */
    @Operation(
            summary = "타당성검토 생략 판정 요청",
            description = "정보보호기획이 IT기획에 생략 판정을 요청합니다(dbrTc=04, 상태 04).")
    @PostMapping("/{asctId}/skip-request")
    public ResponseEntity<Void> createSkipRequest(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.SkipRequestCreate request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilSkipService.createSkipRequest(asctId, request, userDetails);
        return ResponseEntity.ok().build();
    }

    /**
     * 생략여부 판정 + 전자결재 상신 (IT기획 ITPAD001)
     *
     * <p>IT기획이 생략여부(Y=생략/N=개최)와 확인사유를 입력하고 결재선(IT기획팀장→부장)으로 전자결재를 상신합니다. 결재 완료 콜백에서 생략→{@code
     * skipCouncil}, 개최→{@code startPreparation}로 분기됩니다.
     *
     * @param asctId 협의회ID
     * @param request 판정 내용 (생략여부/확인사유/결재선)
     * @param userDetails 판정자 (IT관리자)
     * @return HTTP 200
     */
    @Operation(summary = "생략 판정 + 결재 상신", description = "IT기획이 생략여부를 판정하고 팀장→부장 전자결재를 상신합니다.")
    @PostMapping("/{asctId}/skip-request/decision")
    public ResponseEntity<Void> decideSkipRequest(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.SkipDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilSkipService.submitDecision(asctId, request, userDetails);
        return ResponseEntity.ok().build();
    }

    /**
     * 활성 생략 판정 요청 목록 (IT기획 판정함 — 협의회 목록 배지/판정용)
     *
     * @return 활성(미삭제) 생략 판정 요청 목록
     */
    @Operation(summary = "생략 판정 요청 목록", description = "활성 생략 판정 요청 전체(판정함 배지/판정용).")
    @GetMapping("/skip-requests")
    public ResponseEntity<List<CouncilDto.SkipRequestResponse>> getSkipRequests() {
        return ResponseEntity.ok(councilSkipService.getActiveSkipRequests());
    }

    /**
     * 협의회별 생략 판정 요청 단건 조회 (상태 확인 / 판정 화면)
     *
     * @param asctId 협의회ID
     * @return 생략 판정 요청(없으면 본문 null)
     */
    @Operation(summary = "생략 판정 요청 단건 조회", description = "협의회의 생략 판정 요청을 조회합니다(없으면 null).")
    @GetMapping("/{asctId}/skip-request")
    public ResponseEntity<CouncilDto.SkipRequestResponse> getSkipRequest(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(councilSkipService.getSkipRequest(asctId));
    }
}
