package com.kdb.it.common.approval.controller;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.dto.ApprovalHomeInboxDto;
import com.kdb.it.common.approval.service.ApplicationDashboardService;
import com.kdb.it.common.approval.service.ApplicationService;
import com.kdb.it.common.approval.service.ApprovalHomeInboxService;
import com.kdb.it.common.approval.service.ApprovalLineManagementService;
import com.kdb.it.common.approval.service.ApprovalLineSuggestionService;
import com.kdb.it.common.approval.service.PendingApproverService;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.security.MfaRequired;
import com.kdb.it.common.system.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신청서 관리 REST 컨트롤러
 *
 * <p>신청서(결재 요청)의 생성, 조회, 승인/반려 처리를 담당합니다.
 *
 * <p>기본 URL: {@code /api/applications}
 *
 * <p>주요 기능:
 *
 * <ul>
 *   <li>신청서 생성 (결재선 포함)
 *   <li>단건/전체/일괄 신청서 조회
 *   <li>단건/일괄 신청서 승인·반려 처리
 * </ul>
 *
 * <p>보안: JWT 토큰 인증 필요 (SecurityConfig에서 설정)
 */
@RestController // REST API 컨트롤러로 등록 (@Controller + @ResponseBody)
@RequestMapping("/api/applications") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Tag(name = "Application", description = "신청서 API") // Swagger UI 그룹 태그
public class ApplicationController {

    /** 신청서 비즈니스 로직 서비스 */
    private final ApplicationService applicationService;

    private final ApplicationDashboardService applicationDashboardService;

    /** 전자결재 Home 결재함·기안함 조회 서비스 */
    private final ApprovalHomeInboxService approvalHomeInboxService;

    private final PendingApproverService pendingApproverService;

    private final ApprovalLineManagementService approvalLineManagementService;

    private final ApprovalLineSuggestionService approvalLineSuggestionService;

    /**
     * 전체 신청서 목록 조회
     *
     * <p>결재선이 없는 작성완료({@code 0})와 결재를 거치지 않는 수기등록({@code 9}) 신청서는 결재함 대상이 아니므로 제외하고, 그 외 신청서를 각각의
     * 결재자 목록과 함께 반환합니다.
     *
     * @return HTTP 200 + 신청서 목록 ({@link ApplicationDto.Response} 리스트)
     */
    @GetMapping
    @Operation(summary = "전체 신청서 조회", description = "작성완료(0)·수기등록(9) 신청서를 제외한 신청서 정보를 조회합니다.")
    public ResponseEntity<java.util.List<ApplicationDto.Response>> getApplications(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(value = "allDepartments", defaultValue = "false")
                    boolean allDepartments) {
        return ResponseEntity.ok(applicationService.getApplications(user, allDepartments));
    }

    /**
     * 본인 결재 대기 신청서 목록 조회
     *
     * <p>결재중인 신청서 중 인증 주체가 아직 처리하지 않은 결재선을 가진 건만 반환합니다. 대상 판정은 사이드바 배지(결재 대기 건수)와 같은 조건이므로 배지 건수와
     * 목록 건수가 일치합니다.
     *
     * <p>대상 사번은 요청 파라미터가 아니라 인증 주체에서 얻습니다. 다른 사람의 결재함을 조회할 수 없습니다.
     *
     * @param auth 인증 정보 (결재자 사번)
     * @return HTTP 200 + 결재 대기 신청서 목록 (최신순)
     */
    @GetMapping("/pending")
    @Operation(summary = "본인 결재 대기 신청서 조회", description = "결재중이면서 본인 결재선이 미처리인 신청서만 최신순으로 조회합니다.")
    public ResponseEntity<java.util.List<ApplicationDto.Response>> getPendingApplications(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(value = "allDepartments", defaultValue = "false")
                    boolean allDepartments) {
        return ResponseEntity.ok(applicationService.getPendingApplications(user, allDepartments));
    }

    /**
     * 미상신(결재 신청 이력 없음) 건수 조회
     *
     * <p>정보화사업(BPROJM)과 전산업무비(BCOSTM) 중 결재 신청이 없는 건수를 각각 집계하여 반환합니다. 사이드바의 [결재 상신] 메뉴 배지 등 건수 정보만
     * 필요한 위치에서 사용합니다.
     *
     * <p>전체 목록을 반환하지 않아 데이터 전송량과 프론트 처리 비용이 최소화됩니다.
     *
     * @param bgYy 기준연도 (미지정 시 전체 연도)
     * @param apfSts 결재상태 (미지정 시 작성완료(0) = 상신 대상)
     * @param user 인증 사용자 (역할과 관계없이 소속 부서로 제한)
     * @return HTTP 200 + 결재상태별 건수 응답 ({@link ApplicationDto.PendingCountResponse})
     */
    @GetMapping("/pending-count")
    @Operation(
            summary = "결재상태별 건수 조회",
            description =
                    "정보화사업/전산업무비 건수를 인증 사용자의 조회 범위로 집계합니다. "
                            + "역할과 관계없이 인증 사용자의 소속 부서가 대상이며 "
                            + "apfSts 미지정 시 작성완료(0), 즉 상신 대상으로 집계합니다.")
    public ResponseEntity<ApplicationDto.PendingCountResponse> getPendingCount(
            @RequestParam(value = "bgYy", required = false) String bgYy,
            @RequestParam(value = "apfSts", required = false) String apfSts,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(applicationService.getPendingCount(bgYy, apfSts, user));
    }

    /**
     * 결재라인 자동지정 제안
     *
     * <p>로그인 사용자를 기안자로 보고 동일팀 팀장·CO(1차), 동일부점 부점장급(2차)을 직위코드로 찾아 돌려줍니다. 국외점포는 빈 결과입니다.
     *
     * @param auth 인증 주체 (사번)
     * @return HTTP 200 + 제안 결과
     */
    @GetMapping("/approval-line/suggestion")
    @Operation(
            summary = "결재라인 자동지정 제안",
            description = "국내점포 기안자의 1차(동일팀 팀장·CO)·2차(동일부점 부점장급) 결재자를 직위코드로 제안합니다.")
    public ResponseEntity<ApplicationDto.ApprovalLineSuggestion> suggestApprovalLine(
            Authentication auth) {
        return ResponseEntity.ok(approvalLineSuggestionService.suggest(auth.getName()));
    }

    /**
     * 특정 신청서 단건 조회
     *
     * <p>신청서식별번호(APF_DCM_NO)로 신청서 상세 정보와 결재자 목록을 조회합니다.
     *
     * @param apfMngNo 신청서식별번호 (예: {@code APF-2026-00000001})
     * @return HTTP 200 + 신청서 상세 정보 ({@link ApplicationDto.Response})
     */
    @GetMapping("/{apfMngNo}")
    @Operation(summary = "특정 신청서 조회", description = "특정 신청서를 조회합니다.")
    public ResponseEntity<ApplicationDto.Response> getApplication(
            @PathVariable("apfMngNo") String apfMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        ApplicationDto.Response response = applicationService.getApplication(apfMngNo, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 신청서 결재요청정보(DCD_REQ_INF) 조회
     *
     * <p>신청서식별번호(APF_DCM_NO)로 해당 신청서의 결재요청정보({@code DCD_REQ_INF})만 조회합니다. 전체 신청서 정보가 필요 없고 본문 JSON만
     * 필요한 경우에 사용합니다.
     *
     * @param apfMngNo 신청서식별번호 (예: {@code APF-2026-00000001})
     * @return HTTP 200 + 신청관리번호 및 세부내용 ({@link ApplicationDto.ApfDtlConeResponse})
     */
    @GetMapping("/{apfMngNo}/apfDtlCone")
    @Operation(
            summary = "신청서 세부내용 조회",
            description = "신청서 관리번호(apfMngNo)로 세부내용(APF_DTL_CONE)을 조회합니다.")
    public ResponseEntity<ApplicationDto.ApfDtlConeResponse> getApfDtlCone(
            @PathVariable("apfMngNo") String apfMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(applicationService.getApfDtlCone(apfMngNo, user));
    }

    /**
     * 신청서 일괄 조회
     *
     * <p>여러 신청서 관리번호를 한 번에 조회합니다. 조회 성공 항목({@code items})과 미존재로 실패한 신청관리번호 목록({@code failedIds})을
     * 함께 반환합니다 (부분 성공).
     *
     * @param request 조회할 신청서 관리번호 목록 ({@link ApplicationDto.BulkGetRequest})
     * @return HTTP 200 + 조회 성공 항목 및 실패 ID 목록 ({@link ApplicationDto.BulkResponse})
     */
    @PostMapping("/bulk-get")
    @Operation(
            summary = "신청서 일괄 조회",
            description =
                    "여러 개의 신청서를 한 번에 조회합니다. 조회 성공 항목(items)과 미존재 신청관리번호 목록(failedIds)을 함께 반환합니다.")
    public ResponseEntity<ApplicationDto.BulkResponse> bulkGetApplications(
            @Valid @RequestBody ApplicationDto.BulkGetRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        ApplicationDto.BulkResponse response =
                applicationService.getApplicationsByIds(request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 신청서 단건 승인/반려 처리
     *
     * <p>현재 결재 차례의 결재자가 해당 신청서를 승인하거나 반려합니다.
     *
     * <p>처리 규칙:
     *
     * <ul>
     *   <li>결재자 순서대로만 처리 가능 (순차 결재)
     *   <li>동일 결재자가 연속으로 있는 경우 일괄 승인
     *   <li>반려 시: 신청서 상태 → "반려"
     *   <li>최종 결재자 승인 시: 신청서 상태 → "결재완료"
     * </ul>
     *
     * @param apfMngNo 신청서 관리번호
     * @param request 결재 요청 (의견, 승인/반려 상태)
     * @param user 인증 사용자
     * @return HTTP 200 (본문 없음)
     */
    @PostMapping("/{apfMngNo}/approve")
    @MfaRequired(purpose = MfaPurpose.APPROVAL)
    @Operation(summary = "신청서 승인", description = "신청서를 승인합니다.")
    public ResponseEntity<Void> approve(
            @PathVariable("apfMngNo") String apfMngNo,
            @Valid @RequestBody ApplicationDto.ApproveRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        applicationService.approve(apfMngNo, request, user.getEno());
        return ResponseEntity.ok().build();
    }

    /**
     * 신청서 일괄 승인/반려 처리
     *
     * <p>여러 신청서를 한 번에 승인/반려 처리합니다. 전체를 하나의 트랜잭션으로 처리하며, 하나라도 실패하면 전체가 롤백됩니다.
     *
     * @param request 일괄 승인 요청 목록 ({@link ApplicationDto.BulkApproveRequest})
     * @return HTTP 200 + 처리 결과 요약 ({@link ApplicationDto.BulkApproveResponse}) (총 건수, 성공 건수, 실패 건수,
     *     개별 결과)
     */
    @PostMapping("/bulk-approve")
    @MfaRequired(purpose = MfaPurpose.APPROVAL)
    @Operation(
            summary = "신청서 일괄 승인",
            description = "여러 개의 신청서를 한 번에 승인합니다. 전체를 하나의 트랜잭션으로 처리하며, 하나라도 실패하면 전체 롤백됩니다.")
    public ResponseEntity<ApplicationDto.BulkApproveResponse> bulkApprove(
            @Valid @RequestBody ApplicationDto.BulkApproveRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        ApplicationDto.BulkApproveResponse response =
                applicationService.bulkApprove(request, user.getEno());
        return ResponseEntity.ok(response);
    }

    /**
     * 신청서 회수
     *
     * <p>결재중 신청서를 기안자가 회수합니다. 최종 결재자 승인 전까지 가능합니다.
     *
     * @param apfMngNo 신청서 관리번호
     * @param request 회수 요청 ({@link ApplicationDto.RecallRequest})
     * @return HTTP 204 No Content
     */
    @PostMapping("/{apfMngNo}/recall")
    @MfaRequired(purpose = MfaPurpose.APPROVAL)
    @Operation(summary = "신청서 회수", description = "결재중 신청서를 기안자가 회수합니다. 최종 결재자 승인 전까지 가능합니다.")
    public ResponseEntity<Void> recall(
            @PathVariable("apfMngNo") String apfMngNo,
            @Valid @RequestBody ApplicationDto.RecallRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentEno = auth.getName();
        boolean isAdmin =
                auth.getAuthorities().stream().anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
        applicationService.recall(apfMngNo, request, currentEno, isAdmin);
        return ResponseEntity.noContent().build();
    }

    /** 대기 중인 결재 순번의 담당자를 변경합니다. */
    @PatchMapping("/{apfMngNo}/approvers/{dcdSqn}")
    @MfaRequired(purpose = MfaPurpose.APPROVAL)
    @Operation(summary = "미결재 결재자 변경", description = "관리자 또는 해당 결재선 직원이 미결재 결재자를 변경합니다.")
    public ResponseEntity<Void> changePendingApprover(
            @PathVariable("apfMngNo") String apfMngNo,
            @PathVariable("dcdSqn") int dcdSqn,
            @Valid @RequestBody ApplicationDto.ChangeApproverRequest request,
            Authentication auth) {
        boolean isAdmin =
                auth.getAuthorities().stream().anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
        pendingApproverService.changePendingApprover(
                apfMngNo, dcdSqn, request.getNewApproverEno(), auth.getName(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    /** 결재중 신청서의 마지막 순번 뒤에 추가 결재자를 등록합니다. */
    @PostMapping("/{apfMngNo}/approvers")
    @MfaRequired(purpose = MfaPurpose.APPROVAL)
    @Operation(summary = "추가 결재자 등록", description = "결재중 신청서의 결재선 마지막에 결재자를 추가합니다.")
    public ResponseEntity<Void> addApprover(
            @PathVariable("apfMngNo") String apfMngNo,
            @Valid @RequestBody ApplicationDto.AddApproverRequest request,
            Authentication auth) {
        boolean isAdmin =
                auth.getAuthorities().stream().anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
        approvalLineManagementService.addApprover(
                apfMngNo, request.getApproverEno(), auth.getName(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    /** 결재 완료자를 유지한 채 미결재 결재선을 요청한 전체 순서로 교체합니다. */
    @PutMapping("/{apfMngNo}/approvers")
    @MfaRequired(purpose = MfaPurpose.APPROVAL)
    @Operation(summary = "미결재 결재선 일괄 변경", description = "결재 완료자를 고정하고 미결재 결재자 전체를 교체합니다.")
    public ResponseEntity<Void> replacePendingApprovers(
            @PathVariable("apfMngNo") String apfMngNo,
            @Valid @RequestBody ApplicationDto.ReplacePendingApproversRequest request,
            Authentication auth) {
        boolean isAdmin =
                auth.getAuthorities().stream().anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
        approvalLineManagementService.replacePendingApprovers(
                apfMngNo, request.getApproverEnos(), auth.getName(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    /** 결재중 신청서의 미결재 추가 결재자를 삭제합니다. */
    @DeleteMapping("/{apfMngNo}/approvers/{dcdSqn}")
    @MfaRequired(purpose = MfaPurpose.APPROVAL)
    @Operation(summary = "추가 결재자 삭제", description = "결재중 신청서의 미결재 추가 결재자를 삭제합니다.")
    public ResponseEntity<Void> deleteApprover(
            @PathVariable("apfMngNo") String apfMngNo,
            @PathVariable("dcdSqn") int dcdSqn,
            Authentication auth) {
        boolean isAdmin =
                auth.getAuthorities().stream().anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
        approvalLineManagementService.deleteApprover(apfMngNo, dcdSqn, auth.getName(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    /** 결재중 신청서의 미결재 결재자 순서를 변경합니다. */
    @PatchMapping("/{apfMngNo}/approvers/order")
    @MfaRequired(purpose = MfaPurpose.APPROVAL)
    @Operation(summary = "미결재 결재자 순서 변경", description = "승인 완료자를 고정하고 미결재 결재자 순서만 변경합니다.")
    public ResponseEntity<Void> reorderApprovers(
            @PathVariable("apfMngNo") String apfMngNo,
            @Valid @RequestBody ApplicationDto.ReorderApproversRequest request,
            Authentication auth) {
        boolean isAdmin =
                auth.getAuthorities().stream().anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
        approvalLineManagementService.reorderPendingApprovers(
                apfMngNo, request.getOrderedDcdSqns(), auth.getName(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    /**
     * 전자결재 대시보드 집계 조회
     *
     * @param bbrC 부서코드 (필수)
     * @param eno 사원번호 (필수)
     * @return HTTP 200 + 대시보드 집계 응답
     */
    @GetMapping("/dashboard")
    @Operation(summary = "전자결재 대시보드 조회", description = "부서코드 기준 KPI, 월별 추이, 본인 결재 대기 목록을 반환합니다.")
    public ResponseEntity<ApplicationDto.DashboardResponse> getDashboard(
            @RequestParam("bbrC") String bbrC,
            @RequestParam("eno") String eno,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(applicationDashboardService.getDashboard(bbrC, eno, user));
    }

    /** 인증 사용자의 전자결재 Home 결재함·기안함 전체 목록을 반환합니다. */
    @GetMapping("/home-inbox")
    @Operation(summary = "전자결재 Home 목록 조회", description = "인증 사용자의 결재함과 기안함을 상태별로 반환합니다.")
    public ResponseEntity<ApprovalHomeInboxDto.Response> getHomeInbox(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(approvalHomeInboxService.getHomeInbox(user));
    }

    /**
     * 사이드바 배지용 결재 현황 수 조회
     *
     * @param bbrC 부서코드 (필수)
     * @param eno 사원번호 (필수)
     * @return HTTP 200 + 배지 건수
     */
    @GetMapping("/approval-badge")
    @Operation(summary = "사이드바 배지 건수 조회", description = "결재 대기 수와 기안 진행 중 수를 반환합니다.")
    public ResponseEntity<ApplicationDto.ApprovalBadgeCountResponse> getApprovalBadgeCount(
            @RequestParam("bbrC") String bbrC,
            @RequestParam("eno") String eno,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(
                applicationDashboardService.getApprovalBadgeCount(bbrC, eno, user));
    }
}
