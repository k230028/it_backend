package com.kdb.it.domain.council.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CouncilApprovalService;
import com.kdb.it.domain.council.service.CouncilService;
import com.kdb.it.domain.council.service.CommitteeService;
import com.kdb.it.domain.council.service.EvaluationService;
import com.kdb.it.domain.council.service.FeasibilityService;
import com.kdb.it.domain.council.service.CouncilSkipService;
import com.kdb.it.domain.council.service.ResultService;
import com.kdb.it.domain.council.service.ScheduleService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;

import java.util.List;

/**
 * 정보화실무협의회 REST 컨트롤러
 *
 * <p>기본 URL: {@code /api/council}</p>
 *
 * <p>전체 협의회 API 엔드포인트를 단일 컨트롤러에서 관리합니다 (Design §2.1 Clean Architecture).</p>
 *
 * <p>구현 범위 (Module별 추가):</p>
 * <ul>
 *   <li>M3: 목록/단건 조회, 신규 신청</li>
 *   <li>M4: 타당성검토표 CRUD</li>
 *   <li>M6: 평가위원 선정, 일정 취합/확정, 사전질의응답</li>
 *   <li>M7: 평가의견, 결과서</li>
 * </ul>
 *
 * <p>Design Ref: §2.5 API 설계</p>
 */
@RestController
@RequestMapping("/api/council")
@RequiredArgsConstructor
@Tag(name = "Council", description = "정보화실무협의회 관리 API")
public class CouncilController {

    /** 협의회 기본 서비스 (목록/상태 관리) */
    private final CouncilService councilService;

    /** 타당성검토표 서비스 (Step 1) */
    private final FeasibilityService feasibilityService;

    /** 전자결재 연동 서비스 (Step 1 — 타당성검토표 결재) */
    private final CouncilApprovalService councilApprovalService;

    /** 평가위원 서비스 (Step 2) */
    private final CommitteeService committeeService;

    /** 일정 서비스 (Step 2) */
    private final ScheduleService scheduleService;

    /** 평가의견 서비스 (Step 3) */
    private final EvaluationService evaluationService;

    /** 결과서 서비스 (Step 3) */
    private final ResultService resultService;

    /** 타당성검토 생략 판정 워크플로우 서비스 (PRD_c_20260620 #3) */
    private final CouncilSkipService councilSkipService;

    // =========================================================================
    // M3: 협의회 목록/기본
    // =========================================================================

    /**
     * 내 협의회 목록 조회
     *
     * <p>권한별 필터링:</p>
     * <ul>
     *   <li>일반사용자(ITPZZ001): 소속 부서 사업의 협의회</li>
     *   <li>관리자(ITPAD001): 전체 협의회</li>
     *   <li>평가위원: 배정된 협의회</li>
     * </ul>
     *
     * @param userDetails 현재 로그인한 사용자 (JWT에서 자동 주입)
     * @return HTTP 200 + 협의회 목록
     */
    @Operation(summary = "협의회 목록 조회", description = "권한에 따라 내 부서/전체/배정된 협의회 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<CouncilDto.ListResponse>> getCouncilList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(councilService.getCouncilList(userDetails));
    }

    /**
     * 협의회 신규 신청
     *
     * <p>소관부서 담당자(ITPZZ001)가 타당성검토표 작성 전 협의회를 신청합니다.
     * 초기 상태 DRAFT로 생성됩니다.</p>
     *
     * @param request     협의회 신청 정보 (프로젝트관리번호, 심의유형)
     * @param userDetails 신청자 정보
     * @return HTTP 200 + 생성된 협의회ID
     */
    @Operation(summary = "협의회 신청", description = "새로운 협의회를 신청합니다. 초기 상태는 DRAFT입니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "신청 성공", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content)
    })
    @PostMapping
    public ResponseEntity<String> createCouncil(
            @Valid @RequestBody CouncilDto.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String asctId = councilService.createCouncil(request, userDetails);
        return ResponseEntity.ok(asctId);
    }

    /**
     * 협의회 단건 상세 조회
     *
     * <p>협의회 기본 정보를 반환합니다.
     * 타당성검토표, 평가위원 등 상세 데이터는 별도 API로 조회합니다.</p>
     *
     * @param asctId 협의회ID (예: ASCT-2026-0001)
     * @return HTTP 200 + 협의회 상세 정보, HTTP 404 존재하지 않는 경우
     */
    @Operation(summary = "협의회 단건 조회", description = "협의회ID로 기본 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CouncilDto.DetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @GetMapping("/{asctId}")
    public ResponseEntity<CouncilDto.DetailResponse> getCouncil(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(councilService.getCouncil(asctId));
    }

    // =========================================================================
    // M4: 타당성검토표 (Step 1)
    // =========================================================================

    /**
     * 타당성검토표 조회
     *
     * <p>사업개요 + 타당성 자체점검(6개) + 성과지표 목록을 통합 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 타당성검토표 전체 데이터
     */
    @Operation(summary = "타당성검토표 조회", description = "사업개요, 자체점검, 성과지표를 통합 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CouncilDto.FeasibilityResponse.class))),
            @ApiResponse(responseCode = "404", description = "협의회 또는 타당성검토표 없음", content = @Content)
    })
    @GetMapping("/{asctId}/feasibility")
    public ResponseEntity<CouncilDto.FeasibilityResponse> getFeasibility(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(feasibilityService.getFeasibility(asctId));
    }

    /**
     * 타당성검토표 신규 저장 (임시저장 / 작성완료)
     *
     * <p>kpnTc=001: 임시저장, 상태 DRAFT 유지</p>
     * <p>kpnTc=002: 작성완료, 첨부파일 필수, 상태 SUBMITTED 전이</p>
     *
     * @param asctId  협의회ID
     * @param request 타당성검토표 저장 요청
     * @return HTTP 200
     */
    @Operation(summary = "타당성검토표 저장", description = "임시저장(TEMP) 또는 작성완료(COMPLETE)로 저장합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (작성완료 시 첨부파일 없음 등)", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PostMapping("/{asctId}/feasibility")
    public ResponseEntity<Void> saveFeasibility(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.FeasibilityRequest request) {
        feasibilityService.saveFeasibility(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 타당성검토표 수정 (임시저장 / 작성완료)
     *
     * <p>POST와 동일한 로직으로 upsert 처리합니다 (기존 데이터 있으면 update).</p>
     *
     * @param asctId  협의회ID
     * @param request 타당성검토표 수정 요청
     * @return HTTP 200
     */
    @Operation(summary = "타당성검토표 수정", description = "기존 타당성검토표를 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PutMapping("/{asctId}/feasibility")
    public ResponseEntity<Void> updateFeasibility(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.FeasibilityRequest request) {
        feasibilityService.saveFeasibility(asctId, request);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // M5: 전자결재 연동 (타당성검토표 팀장 결재)
    // =========================================================================

    /**
     * 타당성검토표 결재 요청 (소관부서 담당자 → 팀장)
     *
     * <p>SUBMITTED 상태인 협의회에 대해 팀장 결재를 요청합니다.
     * 전자결재 시스템에 신청서를 등록하고 협의회 상태를 APPROVAL_PENDING으로 전이합니다.</p>
     *
     * @param asctId      협의회ID
     * @param request     결재 요청 (팀장 사번, 신청의견)
     * @param userDetails 신청자 정보
     * @return HTTP 200 + 신청관리번호 (APF_... 형식)
     */
    @Operation(summary = "타당성검토표 결재 요청", description = "팀장에게 타당성검토표 결재를 요청합니다. SUBMITTED 상태에서만 가능합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결재 요청 성공",
                    content = @Content(schema = @Schema(implementation = CouncilDto.ApprovalResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 상태 또는 요청", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PostMapping("/{asctId}/approval")
    public ResponseEntity<CouncilDto.ApprovalResponse> requestApproval(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.ApprovalRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CouncilDto.ApprovalResponse response = councilApprovalService.requestApproval(asctId, request, userDetails);
        return ResponseEntity.ok(response);
    }

    /**
     * 결재 완료/반려 콜백 처리
     *
     * <p>전자결재 시스템에서 팀장이 승인 또는 반려 처리 후 이 API로 협의회 상태를 업데이트합니다.</p>
     * <ul>
     *   <li>승인(approved=true): APPROVAL_PENDING → APPROVED</li>
     *   <li>반려(approved=false): APPROVAL_PENDING → DRAFT (재작성)</li>
     * </ul>
     *
     * @param asctId  협의회ID
     * @param request 콜백 요청 (approved: 승인/반려 여부)
     * @return HTTP 200
     */
    @Operation(summary = "결재 콜백 처리", description = "전자결재 시스템에서 결재 완료/반려 시 협의회 상태를 업데이트합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "상태 업데이트 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 상태", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PatchMapping("/{asctId}/approval")
    public ResponseEntity<Void> processApprovalCallback(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.ApprovalCallbackRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyAdmin(userDetails);
        councilApprovalService.processApprovalCallback(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 협의회 개최 시작 (SCHEDULED → IN_PROGRESS)
     *
     * <p>IT관리자가 오프라인 협의회 개최를 확인하고 진행 상태로 전이합니다.
     * SCHEDULED 상태에서만 호출 가능합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "협의회 개최 시작", description = "SCHEDULED 상태의 협의회를 IN_PROGRESS로 전이합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "개최 시작 성공"),
            @ApiResponse(responseCode = "400", description = "SCHEDULED 상태가 아닌 경우", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PatchMapping("/{asctId}/start")
    public ResponseEntity<Void> startCouncil(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        councilService.startCouncil(asctId);
        return ResponseEntity.ok().build();
    }

    /**
     * 협의회 완료 처리 (IN_PROGRESS → RESULT_WRITING)
     *
     * <p>모든 평가위원의 평가 제출이 확인된 후 IT관리자가 호출합니다.
     * 협의회 상태를 RESULT_WRITING으로 전이하여 개최결과서 작성 단계로 전환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "협의회 완료 처리", description = "IN_PROGRESS 상태의 협의회를 RESULT_WRITING으로 전이합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "완료 처리 성공"),
            @ApiResponse(responseCode = "400", description = "IN_PROGRESS 상태가 아닌 경우", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PatchMapping("/{asctId}/complete")
    public ResponseEntity<Void> completeCouncil(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        councilService.completeCouncil(asctId);
        return ResponseEntity.ok().build();
    }

    /**
     * 정보화실무협의회 생략 처리 (APPROVED(04) → 생략(99))
     *
     * <p>IT관리자가 타당성검토표 검토 후 협의회 생략 대상으로 판단한 경우 호출합니다.
     * 협의회 상태를 생략(99)으로 전이하고, 사업 상태를 '요건 상세화'로 변경합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "협의회 생략 처리", description = "APPROVED 상태에서 협의회를 생략하고 사업을 요건 상세화 단계로 전환합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "생략 처리 성공"),
            @ApiResponse(responseCode = "400", description = "APPROVED 상태가 아닌 경우", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PatchMapping("/{asctId}/skip")
    public ResponseEntity<Void> skipCouncil(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 직접 생략은 IT관리자 전용(정보보호시스템 사업은 판정 요청→IT기획 결재를 거쳐야 함)
        councilService.verifyAdmin(userDetails);
        councilService.skipCouncil(asctId);
        return ResponseEntity.ok().build();
    }

    /**
     * 정보화실무협의회 개최준비 시작 (APPROVED → PREPARING)
     *
     * <p>IT관리자가 타당성검토표 검토 후 '개최준비 진행'을 선택한 경우 호출합니다.
     * 04→05 전이를 평가위원 저장의 부수효과가 아닌 명시적 액션으로 분리했습니다. (PRD_c_20260620 #2)</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "협의회 개최준비 시작", description = "APPROVED(04) 상태에서 협의회를 개최준비(05) 단계로 전이합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "개최준비 전이 성공"),
            @ApiResponse(responseCode = "400", description = "APPROVED 상태가 아닌 경우", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PatchMapping("/{asctId}/start-preparation")
    public ResponseEntity<Void> startPreparation(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        councilService.startPreparation(asctId);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // PRD_c_20260620 #3: 타당성검토 생략 판정 요청 (정보보호기획 → IT기획)
    // =========================================================================

    /**
     * 타당성검토 생략 판정 요청 등록 (정보보호기획 ITPAD002)
     *
     * <p>결재완료(04) 정보보호시스템(dbrTc=04) 협의회에 대해 생략 사유·설명·첨부(사업계획서/타당성검토표)를
     * 담아 IT기획에 생략 판정을 요청합니다. 협의회 상태는 04를 유지합니다.</p>
     *
     * @param asctId      협의회ID
     * @param request     생략 판정 요청 (사유코드/설명/첨부 2종)
     * @param userDetails 요청자 (정보보호관리자)
     * @return HTTP 200
     */
    @Operation(summary = "타당성검토 생략 판정 요청", description = "정보보호기획이 IT기획에 생략 판정을 요청합니다(dbrTc=04, 상태 04).")
    @PostMapping("/{asctId}/skip-request")
    public ResponseEntity<Void> createSkipRequest(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @RequestBody CouncilDto.SkipRequestCreate request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilSkipService.createSkipRequest(asctId, request, userDetails);
        return ResponseEntity.ok().build();
    }

    /**
     * 생략여부 판정 + 전자결재 상신 (IT기획 ITPAD001)
     *
     * <p>IT기획이 생략여부(Y=생략/N=개최)와 확인사유를 입력하고 결재선(IT기획팀장→부장)으로 전자결재를 상신합니다.
     * 결재 완료 콜백에서 생략→{@code skipCouncil}, 개최→{@code startPreparation}로 분기됩니다.</p>
     *
     * @param asctId      협의회ID
     * @param request     판정 내용 (생략여부/확인사유/결재선)
     * @param userDetails 판정자 (IT관리자)
     * @return HTTP 200
     */
    @Operation(summary = "생략 판정 + 결재 상신", description = "IT기획이 생략여부를 판정하고 팀장→부장 전자결재를 상신합니다.")
    @PostMapping("/{asctId}/skip-request/decision")
    public ResponseEntity<Void> decideSkipRequest(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @RequestBody CouncilDto.SkipDecisionRequest request,
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
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(councilSkipService.getSkipRequest(asctId));
    }

    // =========================================================================
    // M6: 평가위원 선정 (Step 2)
    // =========================================================================

    /**
     * 심의유형별 당연위원 후보 조회 (IT관리자)
     *
     * <p>협의회 심의유형(dbrTc)을 기반으로 당연위원 대상 팀에서 위원 후보를 반환합니다.
     * IT관리자가 평가위원 선정 화면에서 당연위원을 자동표출하는 데 사용합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 당연위원 후보 목록
     */
    @Operation(summary = "당연위원 후보 조회", description = "심의유형별 당연위원 대상 팀에서 위원 후보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @GetMapping("/{asctId}/committee/default")
    public ResponseEntity<List<CouncilDto.CommitteeMemberResponse>> getDefaultCommittee(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(committeeService.getDefaultCommittee(asctId));
    }

    /**
     * 평가위원 목록 조회
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 위원유형별 목록
     */
    @Operation(summary = "평가위원 목록 조회", description = "협의회의 당연/소집/간사 위원 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CouncilDto.CommitteeListResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @GetMapping("/{asctId}/committee")
    public ResponseEntity<CouncilDto.CommitteeListResponse> getCommittee(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(committeeService.getCommittee(asctId));
    }

    /**
     * 평가위원 선정 (신규 또는 수정)
     *
     * <p>IT관리자가 당연위원+소집위원+간사를 확정합니다.
     * 기존 위원 전체 교체 방식으로 저장하고 협의회 상태를 PREPARING으로 전이합니다.</p>
     *
     * @param asctId  협의회ID
     * @param request 위원 선정 요청
     * @return HTTP 200
     */
    @Operation(summary = "평가위원 선정", description = "당연/소집/간사 위원을 선정합니다. APPROVED 상태에서만 가능합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "선정 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PostMapping("/{asctId}/committee")
    public ResponseEntity<Void> saveCommittee(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.CommitteeRequest request) {
        committeeService.saveCommittee(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 평가위원 수정 (전체 교체)
     *
     * <p>POST와 동일한 로직으로 전체 교체 저장합니다.</p>
     *
     * @param asctId  협의회ID
     * @param request 위원 수정 요청
     * @return HTTP 200
     */
    @Operation(summary = "평가위원 수정", description = "평가위원을 수정합니다 (전체 교체).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PutMapping("/{asctId}/committee")
    public ResponseEntity<Void> updateCommittee(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.CommitteeRequest request) {
        committeeService.saveCommittee(asctId, request);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // M6: 일정 취합/확정 (Step 2)
    // =========================================================================

    /**
     * 일정 입력 현황 조회 (IT관리자)
     *
     * <p>전체 위원의 일정 응답 현황과 미응답 위원 수를 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 일정 현황
     */
    @Operation(summary = "일정 입력 현황 조회", description = "전체 위원의 일정 응답 현황을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CouncilDto.ScheduleStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @GetMapping("/{asctId}/schedule")
    public ResponseEntity<CouncilDto.ScheduleStatusResponse> getScheduleStatus(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(scheduleService.getScheduleStatus(asctId));
    }

    /**
     * 내 일정 조회 (평가위원 본인)
     *
     * <p>로그인한 평가위원이 제출한 일정 슬롯 목록을 반환합니다.</p>
     */
    @Operation(summary = "내 일정 조회", description = "로그인한 평가위원 본인이 제출한 일정을 조회합니다.")
    @GetMapping("/{asctId}/schedule/my")
    public ResponseEntity<List<CouncilDto.ScheduleSlotResponse>> getMySchedule(
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(scheduleService.getMySchedule(asctId, userDetails.getEno()));
    }

    /**
     * 일정 입력 (평가위원)
     *
     * <p>평가위원이 날짜×시간대별 가능 여부를 입력합니다.
     * 허용 시간대: 10:00 / 14:00 / 15:00 / 16:00</p>
     *
     * @param asctId      협의회ID
     * @param request     일정 입력 요청
     * @param userDetails 로그인한 평가위원
     * @return HTTP 200
     */
    @Operation(summary = "일정 입력", description = "평가위원이 가능한 날짜/시간대를 입력합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "입력 성공"),
            @ApiResponse(responseCode = "400", description = "허용되지 않은 시간대", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PostMapping("/{asctId}/schedule")
    public ResponseEntity<Void> submitSchedule(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.ScheduleRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        scheduleService.submitSchedule(asctId, request, userDetails);
        return ResponseEntity.ok().build();
    }

    /**
     * 일정 확정 (IT관리자)
     *
     * <p>최종 회의 일정을 확정합니다.
     * BASCTM.CNRC_DT/TM/PLC를 업데이트하고 상태를 SCHEDULED로 전이합니다.</p>
     *
     * @param asctId  협의회ID
     * @param request 일정 확정 요청 (회의일자, 회의시간, 회의장소)
     * @return HTTP 200
     */
    @Operation(summary = "일정 확정", description = "최종 회의 일정을 확정하고 상태를 SCHEDULED로 전이합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "확정 성공"),
            @ApiResponse(responseCode = "400", description = "허용되지 않은 시간대", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PutMapping("/{asctId}/schedule/confirm")
    public ResponseEntity<Void> confirmSchedule(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.ScheduleConfirmRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        scheduleService.confirmSchedule(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 서면개최 확정 (IT관리자) (PRD_c_20260620 #1)
     *
     * <p>위원 전원이 대면을 희망하지 않을 때 서면개최로 확정합니다.
     * 회의일자/시간/장소 없이 BASCTM.CSF_HELD_YN='N'으로 설정하고
     * 상태를 개최준비(05) → 진행중(07)으로 직접 전이합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "서면개최 확정", description = "위원 전원 미희망 시 서면개최로 확정하고 상태를 진행중으로 전이합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "서면개최 확정 성공"),
            @ApiResponse(responseCode = "400", description = "개최준비(05) 상태가 아닌 경우", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PutMapping("/{asctId}/schedule/confirm-written")
    public ResponseEntity<Void> confirmWrittenMeeting(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        scheduleService.confirmWrittenMeeting(asctId);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // M7: 평가의견 (Step 3)
    // =========================================================================

    /**
     * 평가의견 전체 현황 조회 (IT관리자)
     *
     * <p>전체 위원의 6개 항목 평가의견과 항목별 평균점수를 반환합니다.
     * 결과서 작성 시 참고 데이터로 활용합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 위원별 평가의견 + 항목별 평균점수
     */
    @Operation(summary = "평가의견 전체 현황 조회", description = "전체 위원의 평가의견과 항목별 평균점수를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CouncilDto.EvaluationSummaryResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @GetMapping("/{asctId}/evaluation")
    public ResponseEntity<CouncilDto.EvaluationSummaryResponse> getAllEvaluations(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(evaluationService.getAllEvaluations(asctId));
    }

    /**
     * 내 평가의견 조회 (로그인한 평가위원 본인)
     *
     * <p>로그인한 평가위원이 이미 제출한 6개 항목의 평가의견을 반환합니다.
     * 아직 제출 이력이 없으면 빈 배열을 반환합니다.</p>
     *
     * @param asctId      협의회ID
     * @param userDetails 로그인한 평가위원
     * @return 내 평가의견 목록 (최대 6개, 없으면 빈 배열)
     */
    @Operation(summary = "내 평가의견 조회", description = "로그인한 평가위원 본인의 평가의견을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @GetMapping("/{asctId}/evaluation/my")
    public ResponseEntity<List<CouncilDto.EvaluationItemResponse>> getMyEvaluation(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(evaluationService.getMyEvaluation(asctId, userDetails));
    }

    /**
     * 평가의견 작성/수정 (평가위원)
     *
     * <p>6개 점검항목에 대한 점수와 의견을 저장합니다.
     * 1~2점 입력 시 의견 작성이 필수입니다.
     * 첫 제출 시 협의회 상태를 IN_PROGRESS → EVALUATING으로 전이합니다.</p>
     *
     * @param asctId      협의회ID
     * @param request     평가의견 요청 (6개 항목)
     * @param userDetails 로그인한 평가위원
     * @return HTTP 200
     */
    @Operation(summary = "평가의견 작성", description = "6개 점검항목에 대한 점수와 의견을 저장합니다. 1~2점 시 의견 필수.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "1~2점인데 의견 미작성", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PostMapping("/{asctId}/evaluation")
    public ResponseEntity<Void> saveEvaluation(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.EvaluationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        evaluationService.saveEvaluation(asctId, request, userDetails);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // M7: 결과서 (Step 3)
    // =========================================================================

    /**
     * 결과서 조회 (IT관리자)
     *
     * <p>결과서 내용(종합의견, 타당성검토의견, 첨부파일)과
     * 점검항목별 평균점수를 함께 반환합니다.
     * 아직 작성 전이면 avgScores만 채워진 빈 결과서를 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 결과서 내용 + 항목별 평균점수
     */
    @Operation(summary = "결과서 조회", description = "결과서 내용과 점검항목별 평균점수를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CouncilDto.ResultResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @GetMapping("/{asctId}/result")
    public ResponseEntity<CouncilDto.ResultResponse> getResult(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(resultService.getResult(asctId));
    }

    /**
     * 결과서 저장 (IT관리자)
     *
     * <p>종합의견, 타당성검토의견, 첨부파일을 저장합니다.
     * 최초 저장 시 협의회 상태를 EVALUATING → RESULT_WRITING으로 전이합니다.</p>
     *
     * @param asctId  협의회ID
     * @param request 결과서 작성 요청
     * @return HTTP 200
     */
    @Operation(summary = "결과서 저장", description = "결과서를 저장합니다. 최초 저장 시 RESULT_WRITING으로 상태 전이.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PostMapping("/{asctId}/result")
    public ResponseEntity<Void> saveResult(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.ResultRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        resultService.saveResult(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 결과서 수정 (IT관리자)
     *
     * <p>POST와 동일한 로직으로 upsert 처리합니다.</p>
     *
     * @param asctId  협의회ID
     * @param request 결과서 수정 요청
     * @return HTTP 200
     */
    @Operation(summary = "결과서 수정", description = "기존 결과서를 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PutMapping("/{asctId}/result")
    public ResponseEntity<Void> updateResult(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.ResultRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        resultService.saveResult(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 결과서 확정 (IT관리자)
     *
     * <p>작성 완료된 결과서를 확정하고 협의회 상태를 RESULT_REVIEW로 전이합니다.
     * RESULT_REVIEW 단계에서 평가위원들이 결과서를 최종 검토합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "결과서 확정", description = "결과서를 확정하고 상태를 RESULT_REVIEW로 전이합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "확정 성공"),
            @ApiResponse(responseCode = "400", description = "결과서 미작성", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PutMapping("/{asctId}/result/confirm")
    public ResponseEntity<Void> confirmResult(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        resultService.confirmResult(asctId);
        return ResponseEntity.ok().build();
    }

    /**
     * 평가위원 결과서 검토 확인 (평가위원)
     *
     * <p>RESULT_REVIEW 상태에서 평가위원(MAND/CALL)이 결과서 확인 완료를 처리합니다.
     * 전원 확인 완료 시 협의회 상태가 FINAL_APPROVAL로 자동 전이됩니다.</p>
     *
     * @param asctId      협의회ID
     * @param userDetails 로그인한 평가위원
     * @return HTTP 200
     */
    @Operation(summary = "결과서 검토 확인", description = "평가위원이 결과서를 확인합니다. 전원 완료 시 FINAL_APPROVAL 자동 전이.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "확인 처리 성공"),
            @ApiResponse(responseCode = "400", description = "RESULT_REVIEW 상태 아님", content = @Content),
            @ApiResponse(responseCode = "403", description = "평가위원 아님 또는 간사", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PostMapping("/{asctId}/result/review")
    public ResponseEntity<Void> reviewResult(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        resultService.reviewResult(asctId, userDetails);
        return ResponseEntity.ok().build();
    }

    /**
     * 결과서 검토 진행상황 동기화 (010 → 011 자동 전이 트리거)
     *
     * <p>평가위원(간사 제외) 전원의 CNFM_YN이 'Y'면 협의회 상태를
     * RESULT_REVIEW → FINAL_APPROVAL로 전이합니다. 데이터를 직접 수정한 경우
     * 또는 화면 진입 시점에 호출해 자동 전이가 누락되지 않도록 보장합니다.</p>
     */
    @Operation(summary = "검토 진행상황 동기화", description = "위원 전원 확인 시 010→011 전이를 보장합니다.")
    @PostMapping("/{asctId}/result/review/sync")
    public ResponseEntity<Boolean> syncReviewStatus(
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(resultService.syncReviewStatus(asctId));
    }

    /**
     * 본인 결과서 검토 확인 여부 조회 (평가위원)
     *
     * <p>페이지 진입 시 이미 결과서 확인을 완료했는지 조회합니다.
     * 완료 시 버튼 대신 완료 UI를 표시하는 데 사용합니다.</p>
     *
     * @param asctId      협의회ID
     * @param userDetails 로그인한 평가위원
     * @return true: 이미 확인 완료, false: 미확인
     */
    @Operation(summary = "본인 결과서 확인 여부 조회", description = "평가위원 본인의 결과서 검토 확인 여부를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @GetMapping("/{asctId}/result/review/my")
    public ResponseEntity<Boolean> getMyResultReview(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(resultService.getMyReviewStatus(asctId, userDetails));
    }

    /**
     * 개최결과서 결재 요청 (IT관리자)
     *
     * <p>FINAL_APPROVAL 상태에서 IT관리자가 부장에게 결재를 요청합니다.
     * 전자결재 시스템에 신청서를 등록하고 협의회 상태를 RESULT_APPROVAL_PENDING으로 전이합니다.</p>
     *
     * @param asctId      협의회ID
     * @param request     결재 요청 (부장 사번, 신청의견)
     * @param userDetails 신청자 정보
     * @return HTTP 200 + 신청관리번호 (APF_... 형식)
     */
    @Operation(summary = "개최결과서 결재 요청", description = "부장에게 개최결과서 결재를 요청합니다. FINAL_APPROVAL 상태에서만 가능합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결재 요청 성공",
                    content = @Content(schema = @Schema(implementation = CouncilDto.ApprovalResponse.class))),
            @ApiResponse(responseCode = "400", description = "FINAL_APPROVAL 상태 아님", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PostMapping("/{asctId}/result/approval")
    public ResponseEntity<CouncilDto.ApprovalResponse> requestResultApproval(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Valid @RequestBody CouncilDto.ResultApprovalRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        CouncilDto.ApprovalResponse response = councilApprovalService.requestResultApproval(asctId, request, userDetails);
        return ResponseEntity.ok(response);
    }

    /**
     * 추진부서 통보 처리 (IT관리자)
     *
     * <p>협의회가 완료된 후 IT관리자가 추진부서 담당자에게 결과를 통보합니다.
     * 사업 상태(BPROJM.PRJ_STS)를 '요건 상세화'로 변경합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "추진부서 통보", description = "협의회 결과를 추진부서에 통보합니다. COMPLETED 상태에서만 가능합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "통보 성공"),
            @ApiResponse(responseCode = "400", description = "COMPLETED 상태 아님", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PostMapping("/{asctId}/notify")
    public ResponseEntity<CouncilDto.NotifyResponse> notifyCouncil(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        CouncilDto.NotifyResponse response = councilService.notifyCouncil(asctId);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // M6: 사전질의응답 (Step 2)
    // =========================================================================

}
