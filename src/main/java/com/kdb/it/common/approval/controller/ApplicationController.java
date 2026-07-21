package com.kdb.it.common.approval.controller;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * 전체 신청서 목록 조회
     *
     * <p>DB에 등록된 모든 신청서와 각 신청서의 결재자 목록을 반환합니다.
     *
     * @return HTTP 200 + 신청서 목록 ({@link ApplicationDto.Response} 리스트)
     */
    @GetMapping
    @Operation(summary = "전체 신청서 조회", description = "모든 신청서 정보를 조회합니다.")
    public ResponseEntity<java.util.List<ApplicationDto.Response>> getApplications() {
        return ResponseEntity.ok(applicationService.getApplications());
    }

    /**
     * 미상신(결재 신청 이력 없음) 건수 조회
     *
     * <p>정보화사업(BPROJM)과 전산업무비(BCOSTM) 중 결재 신청이 없는 건수를 각각 집계하여 반환합니다. 사이드바의 [결재 상신] 메뉴 배지 등 건수 정보만
     * 필요한 위치에서 사용합니다.
     *
     * <p>전체 목록을 반환하지 않아 데이터 전송량과 프론트 처리 비용이 최소화됩니다.
     *
     * @return HTTP 200 + 미상신 건수 응답 ({@link ApplicationDto.PendingCountResponse})
     */
    @GetMapping("/pending-count")
    @Operation(
            summary = "미상신 건수 조회",
            description =
                    "결재 상신 대기 중인 정보화사업/전산업무비 건수를 집계합니다. 사이드바 배지용. "
                            + "bgYy 미지정 시 모든 연도 합산, 지정 시 해당 회계연도 항목만 카운트.")
    public ResponseEntity<ApplicationDto.PendingCountResponse> getPendingCount(
            @RequestParam(value = "bgYy", required = false) String bgYy) {
        return ResponseEntity.ok(applicationService.getPendingCount(bgYy));
    }

    /**
     * 특정 신청서 단건 조회
     *
     * <p>신청서 관리번호(APF_MNG_NO)로 신청서 상세 정보와 결재자 목록을 조회합니다.
     *
     * @param apfMngNo 신청서 관리번호 (예: {@code APF_20260001})
     * @return HTTP 200 + 신청서 상세 정보 ({@link ApplicationDto.Response})
     */
    @GetMapping("/{apfMngNo}")
    @Operation(summary = "특정 신청서 조회", description = "특정 신청서를 조회합니다.")
    public ResponseEntity<ApplicationDto.Response> getApplication(
            @PathVariable("apfMngNo") String apfMngNo) {
        ApplicationDto.Response response = applicationService.getApplication(apfMngNo);
        return ResponseEntity.ok(response);
    }

    /**
     * 신청서 세부내용(APF_DTL_CONE) 조회
     *
     * <p>신청서 관리번호(APF_MNG_NO)로 해당 신청서의 세부내용({@code APF_DTL_CONE})만 조회합니다. 전체 신청서 정보가 필요 없고 본문 JSON만
     * 필요한 경우에 사용합니다.
     *
     * @param apfMngNo 신청서 관리번호 (예: {@code APF_202600000001})
     * @return HTTP 200 + 신청관리번호 및 세부내용 ({@link ApplicationDto.ApfDtlConeResponse})
     */
    @GetMapping("/{apfMngNo}/apfDtlCone")
    @Operation(
            summary = "신청서 세부내용 조회",
            description = "신청서 관리번호(apfMngNo)로 세부내용(APF_DTL_CONE)을 조회합니다.")
    public ResponseEntity<ApplicationDto.ApfDtlConeResponse> getApfDtlCone(
            @PathVariable("apfMngNo") String apfMngNo) {
        return ResponseEntity.ok(applicationService.getApfDtlCone(apfMngNo));
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
            @RequestBody ApplicationDto.BulkGetRequest request) {
        ApplicationDto.BulkResponse response = applicationService.getApplicationsByIds(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 신규 신청서 생성
     *
     * <p>신청서 마스터, 원본 데이터 연결(Cappla), 결재선(Cdecim)을 일괄 생성합니다.
     *
     * <p>생성 흐름:
     *
     * <ol>
     *   <li>시퀀스로 신청서 관리번호 생성 (예: {@code APF_202600000001})
     *   <li>신청서 마스터(TPRMPP_CAPPLM) 저장
     *   <li>원본 데이터 연결(TPRMPP_CAPPLA) 저장
     *   <li>결재선 목록(TPRMPP_CDECIM) 저장
     * </ol>
     *
     * @param request 신청서 생성 요청 ({@link ApplicationDto.CreateRequest})
     * @return HTTP 201 Created + 생성된 신청서 관리번호 (Location 헤더 포함)
     */
    @PostMapping
    @Operation(summary = "신규 신청서 생성", description = "신규 신청서를 생성합니다.")
    public ResponseEntity<String> submit(@Valid @RequestBody ApplicationDto.CreateRequest request) {
        // 신청서 생성 후 관리번호 반환
        String apfMngNo = applicationService.submit(request);
        // 201 Created 응답 + Location 헤더에 생성된 리소스 URL 포함
        return ResponseEntity.created(URI.create("/api/applications/" + apfMngNo)).body(apfMngNo);
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
     * @param request 결재 요청 (결재자 사번, 의견, 승인/반려 상태)
     * @return HTTP 200 (본문 없음)
     */
    @PostMapping("/{apfMngNo}/approve")
    @Operation(summary = "신청서 승인", description = "신청서를 승인합니다.")
    public ResponseEntity<Void> approve(
            @PathVariable("apfMngNo") String apfMngNo,
            @Valid @RequestBody ApplicationDto.ApproveRequest request) {
        applicationService.approve(apfMngNo, request);
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
    @Operation(
            summary = "신청서 일괄 승인",
            description = "여러 개의 신청서를 한 번에 승인합니다. 전체를 하나의 트랜잭션으로 처리하며, 하나라도 실패하면 전체 롤백됩니다.")
    public ResponseEntity<ApplicationDto.BulkApproveResponse> bulkApprove(
            @RequestBody ApplicationDto.BulkApproveRequest request) {
        ApplicationDto.BulkApproveResponse response = applicationService.bulkApprove(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 신청서 회수
     *
     * <p>결재중 신청서를 회수합니다. 신청자/중간결재자/관리자만 가능, 최종 결재자 승인 전까지.
     *
     * @param apfMngNo 신청서 관리번호
     * @param request 회수 요청 ({@link ApplicationDto.RecallRequest})
     * @return HTTP 204 No Content
     */
    @PostMapping("/{apfMngNo}/recall")
    @Operation(
            summary = "신청서 회수",
            description = "결재중 신청서를 회수합니다. 신청자/중간결재자/관리자만 가능, 최종 결재자 승인 전까지.")
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
            @RequestParam("bbrC") String bbrC, @RequestParam("eno") String eno) {
        return ResponseEntity.ok(applicationService.getDashboard(bbrC, eno));
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
            @RequestParam("bbrC") String bbrC, @RequestParam("eno") String eno) {
        return ResponseEntity.ok(applicationService.getApprovalBadgeCount(bbrC, eno));
    }
}
