package com.kdb.it.common.admin.controller;

import com.kdb.it.common.admin.dto.AdminDto;
import com.kdb.it.common.admin.dto.AdminLogDto;
import com.kdb.it.common.admin.service.AdminLogService;
import com.kdb.it.common.admin.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 시스템 관리자 전용 API 컨트롤러
 *
 * <p>
 * 모든 엔드포인트는 {@code ROLE_ADMIN} (ITPAD001) 역할 보유자만 접근 가능합니다.
 * SecurityConfig에서 {@code /api/admin/**} 경로에 대해 {@code hasRole("ADMIN")} 설정이
 * 이미 적용되어 있으며, 메서드 레벨에서도 이중으로 보호합니다.
 * </p>
 *
 * <p>
 * Design Ref: §2.2 — AdminController 설계, §6.1 — 보안 설계
 * </p>
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "시스템 관리자 전용 API (ITPAD001 역할 필요)")
public class AdminController {

    private final AdminService adminService;
    private final AdminLogService adminLogService;

    // =========================================================================
    // 공통코드 관리 (TAAABB_CCODEM)
    // =========================================================================

    /**
     * 공통코드 목록 조회
     * 삭제되지 않은(DEL_YN='N') 전체 공통코드를 코드순서 오름차순으로 반환합니다.
     *
     * @return 공통코드 응답 DTO 목록
     */
    @GetMapping("/codes")
    @Operation(summary = "공통코드 목록 조회", description = "삭제되지 않은 전체 공통코드를 코드순서 오름차순으로 반환합니다.")
    public ResponseEntity<List<AdminDto.CodeResponse>> getCodes() {
        return ResponseEntity.ok(adminService.getCodes());
    }

    /**
     * 공통코드 추가
     *
     * @param req 공통코드 생성 요청 DTO
     * @return 201 Created
     */
    @PostMapping("/codes")
    @Operation(summary = "공통코드 추가", description = "새로운 공통코드를 추가합니다. C_ID 중복 시 400 반환.")
    public ResponseEntity<Void> createCode(@Valid @RequestBody AdminDto.CodeRequest req) {
        adminService.createCode(req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 공통코드 수정 (인라인 편집 즉시 저장)
     *
     * @param cdId 코드ID
     * @param sttDt 시작일자
     * @param req  공통코드 수정 요청 DTO
     * @return 200 OK
     */
    @PutMapping("/codes/{cdId}")
    @Operation(summary = "공통코드 수정", description = "공통코드 정보를 수정합니다. 인라인 편집 즉시 저장에 사용됩니다.")
    public ResponseEntity<Void> updateCode(
            @PathVariable("cdId") String cdId,
            @Parameter(description = "시작일자 (yyyy-MM-dd)", required = true) @RequestParam("sttDt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sttDt,
            @Valid @RequestBody AdminDto.CodeRequest req) {
        adminService.updateCode(cdId, sttDt, req);
        return ResponseEntity.ok().build();
    }

    /**
     * 공통코드 삭제 (Soft Delete)
     * DEL_YN='Y' 처리 — 물리 삭제 아님.
     *
     * @param cdId 코드ID
     * @param sttDt 시작일자
     * @return 204 No Content
     */
    @DeleteMapping("/codes/{cdId}")
    @Operation(summary = "공통코드 삭제(논리)", description = "DEL_YN='Y'로 논리 삭제합니다. 물리 삭제 아님.")
    public ResponseEntity<Void> deleteCode(
            @PathVariable("cdId") String cdId,
            @Parameter(description = "시작일자 (yyyy-MM-dd)", required = true) @RequestParam("sttDt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sttDt) {
        adminService.deleteCode(cdId, sttDt);
        return ResponseEntity.noContent().build();
    }

    /**
     * 공통코드 일괄 업로드 (Upsert)
     * 코드ID가 이미 존재하면 수정, 없으면 신규 생성합니다.
     *
     * @param req 일괄 업로드 요청 DTO (codes 목록)
     * @return 200 OK + { created, updated } 건수
     */
    @PostMapping("/codes/bulk")
    @Operation(summary = "공통코드 일괄 업로드", description = "엑셀에서 파싱한 코드 목록을 일괄 생성/수정(Upsert)합니다.")
    public ResponseEntity<java.util.Map<String, Integer>> bulkUpsertCodes(
            @RequestBody AdminDto.BulkCodeRequest req) {
        return ResponseEntity.ok(adminService.bulkUpsertCodes(req));
    }

    // =========================================================================
    // 자격등급 관리 (TAAABB_CAUTHI) — M3
    // =========================================================================

    /**
     * 자격등급 목록 조회
     * 삭제되지 않은(DEL_YN='N') 전체 자격등급을 반환합니다.
     *
     * @return 자격등급 응답 DTO 목록
     */
    @GetMapping("/auth-grades")
    @Operation(summary = "자격등급 목록 조회", description = "삭제되지 않은 전체 자격등급을 반환합니다.")
    public ResponseEntity<List<AdminDto.AuthGradeResponse>> getAuthGrades() {
        return ResponseEntity.ok(adminService.getAuthGrades());
    }

    /**
     * 자격등급 추가
     * ATH_ID 중복 시 400 Bad Request를 반환합니다.
     *
     * @param req 자격등급 생성 요청 DTO
     * @return 201 Created
     */
    @PostMapping("/auth-grades")
    @Operation(summary = "자격등급 추가", description = "새로운 자격등급을 추가합니다. ATH_ID 중복 시 400 반환.")
    public ResponseEntity<Void> createAuthGrade(@Valid @RequestBody AdminDto.AuthGradeRequest req) {
        adminService.createAuthGrade(req);
        return ResponseEntity.status(201).build();
    }

    /**
     * 자격등급 수정
     *
     * @param athId 자격등급ID
     * @param req   자격등급 수정 요청 DTO
     * @return 200 OK
     */
    @PutMapping("/auth-grades/{athId}")
    @Operation(summary = "자격등급 수정", description = "자격등급 정보를 수정합니다.")
    public ResponseEntity<Void> updateAuthGrade(
            @PathVariable("athId") String athId,
            @Valid @RequestBody AdminDto.AuthGradeRequest req) {
        adminService.updateAuthGrade(athId, req);
        return ResponseEntity.ok().build();
    }

    /**
     * 자격등급 논리 삭제 (Soft Delete)
     * DEL_YN='Y' 처리 -- 물리 삭제 아님.
     *
     * @param athId 자격등급ID
     * @return 204 No Content
     */
    @DeleteMapping("/auth-grades/{athId}")
    @Operation(summary = "자격등급 삭제(논리)", description = "DEL_YN='Y'로 논리 삭제합니다.")
    public ResponseEntity<Void> deleteAuthGrade(@PathVariable("athId") String athId) {
        adminService.deleteAuthGrade(athId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // 역할 관리 (TAAABB_CROLEI) — M4
    // =========================================================================

    /**
     * 역할(사용자-자격등급 매핑) 목록 조회
     * 삭제되지 않은(DEL_YN='N') 전체 역할 목록을 반환합니다.
     *
     * @return 역할 응답 DTO 목록
     */
    @GetMapping("/roles")
    @Operation(summary = "역할 목록 조회", description = "삭제되지 않은 전체 역할(사용자↔자격등급 매핑)을 반환합니다.")
    public ResponseEntity<List<AdminDto.RoleResponse>> getRoles() {
        return ResponseEntity.ok(adminService.getRoles());
    }

    /**
     * 역할 추가 (사용자에게 자격등급 부여)
     * 복합키(athId+eno) 중복 시 400 Bad Request를 반환합니다.
     *
     * @param req 역할 생성 요청 DTO
     * @return 201 Created
     */
    @PostMapping("/roles")
    @Operation(summary = "역할 추가", description = "사용자에게 자격등급을 부여합니다. 복합키 중복 시 400 반환.")
    public ResponseEntity<Void> createRole(@Valid @RequestBody AdminDto.RoleRequest req) {
        adminService.createRole(req);
        return ResponseEntity.status(201).build();
    }

    /**
     * 역할 사용여부 수정
     *
     * @param athId 자격등급ID
     * @param eno   사원번호
     * @param req   역할 수정 요청 DTO
     * @return 200 OK
     */
    @PutMapping("/roles/{athId}/{eno}")
    @Operation(summary = "역할 수정", description = "역할 사용여부를 수정합니다.")
    public ResponseEntity<Void> updateRole(
            @PathVariable("athId") String athId,
            @PathVariable("eno") String eno,
            @Valid @RequestBody AdminDto.RoleRequest req) {
        adminService.updateRole(athId, eno, req);
        return ResponseEntity.ok().build();
    }

    /**
     * 역할 논리 삭제 (Soft Delete)
     *
     * @param athId 자격등급ID
     * @param eno   사원번호
     * @return 204 No Content
     */
    @DeleteMapping("/roles/{athId}/{eno}")
    @Operation(summary = "역할 삭제(논리)", description = "DEL_YN='Y'로 논리 삭제합니다.")
    public ResponseEntity<Void> deleteRole(
            @PathVariable("athId") String athId,
            @PathVariable("eno") String eno) {
        adminService.deleteRole(athId, eno);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // 사용자 관리 (TAAABB_CUSERI) — M5
    // =========================================================================

    /**
     * 사용자 목록 조회
     * 삭제되지 않은(DEL_YN='N') 전체 사용자를 반환합니다.
     *
     * @return 사용자 응답 DTO 목록
     */
    @GetMapping("/users")
    @Operation(summary = "사용자 목록 조회", description = "삭제되지 않은 전체 사용자를 반환합니다.")
    public ResponseEntity<List<AdminDto.UserResponse>> getUsers() {
        return ResponseEntity.ok(adminService.getUsers());
    }

    /**
     * 사용자 추가
     * ENO 중복 시 400 Bad Request를 반환합니다.
     *
     * @param req 사용자 생성 요청 DTO
     * @return 201 Created
     */
    @PostMapping("/users")
    @Operation(summary = "사용자 추가", description = "신규 사용자를 추가합니다. ENO 중복 시 400 반환.")
    public ResponseEntity<Void> createUser(@Valid @RequestBody AdminDto.UserRequest req) {
        adminService.createUser(req);
        return ResponseEntity.status(201).build();
    }

    /**
     * 사용자 기본정보 수정
     * password 필드가 포함된 경우 비밀번호도 함께 변경됩니다.
     *
     * @param eno 사원번호
     * @param req 사용자 수정 요청 DTO
     * @return 200 OK
     */
    @PutMapping("/users/{eno}")
    @Operation(summary = "사용자 수정", description = "사용자 기본정보를 수정합니다. password 포함 시 비밀번호도 변경됩니다.")
    public ResponseEntity<Void> updateUser(
            @PathVariable("eno") String eno,
            @Valid @RequestBody AdminDto.UserRequest req) {
        adminService.updateUser(eno, req);
        return ResponseEntity.ok().build();
    }

    /**
     * 사용자 논리 삭제 (Soft Delete)
     *
     * @param eno 사원번호
     * @return 204 No Content
     */
    @DeleteMapping("/users/{eno}")
    @Operation(summary = "사용자 삭제(논리)", description = "DEL_YN='Y'로 논리 삭제합니다.")
    public ResponseEntity<Void> deleteUser(@PathVariable("eno") String eno) {
        adminService.deleteUser(eno);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // 조직 관리 (TAAABB_CORGNI) — M6
    // =========================================================================

    /**
     * 조직 목록 조회
     * 삭제되지 않은(DEL_YN='N') 전체 조직을 반환합니다.
     *
     * @return 조직 응답 DTO 목록
     */
    @GetMapping("/organizations")
    @Operation(summary = "조직 목록 조회", description = "삭제되지 않은 전체 조직을 반환합니다.")
    public ResponseEntity<List<AdminDto.OrgResponse>> getOrganizations() {
        return ResponseEntity.ok(adminService.getOrganizations());
    }

    /**
     * 조직 추가
     * 조직코드 중복 시 400 Bad Request를 반환합니다.
     *
     * @param req 조직 생성 요청 DTO
     * @return 201 Created
     */
    @PostMapping("/organizations")
    @Operation(summary = "조직 추가", description = "신규 조직을 추가합니다. 조직코드 중복 시 400 반환.")
    public ResponseEntity<Void> createOrganization(@Valid @RequestBody AdminDto.OrgRequest req) {
        adminService.createOrganization(req);
        return ResponseEntity.status(201).build();
    }

    /**
     * 조직 정보 수정
     *
     * @param orgC 조직코드
     * @param req  조직 수정 요청 DTO
     * @return 200 OK
     */
    @PutMapping("/organizations/{orgC}")
    @Operation(summary = "조직 수정", description = "조직 정보를 수정합니다.")
    public ResponseEntity<Void> updateOrganization(
            @PathVariable("orgC") String orgC,
            @Valid @RequestBody AdminDto.OrgRequest req) {
        adminService.updateOrganization(orgC, req);
        return ResponseEntity.ok().build();
    }

    /**
     * 조직 논리 삭제 (Soft Delete)
     *
     * @param orgC 조직코드
     * @return 204 No Content
     */
    @DeleteMapping("/organizations/{orgC}")
    @Operation(summary = "조직 삭제(논리)", description = "DEL_YN='Y'로 논리 삭제합니다.")
    public ResponseEntity<Void> deleteOrganization(@PathVariable("orgC") String orgC) {
        adminService.deleteOrganization(orgC);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // 로그인 이력 조회 (TAAABB_CLOGNH) — M7
    // =========================================================================

    /**
     * 전체 로그인 이력 페이지네이션 조회 (최신순)
     *
     * @param pageable 페이지 정보 (기본: 50건, lgnDtm 내림차순)
     * @return 페이지네이션된 로그인 이력 응답
     */
    @GetMapping("/login-history")
    @Operation(summary = "로그인 이력 조회", description = "전체 로그인 이력을 최신순으로 페이지네이션하여 반환합니다.")
    public ResponseEntity<Page<AdminDto.LoginHistoryResponse>> getLoginHistory(
            @PageableDefault(size = 50, sort = "lgnDtm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminService.getLoginHistory(pageable));
    }

    // =========================================================================
    // JWT 토큰 조회 (TAAABB_CRTOKM) — M7
    // =========================================================================

    /**
     * JWT 갱신토큰 목록 조회
     * 보안상 토큰값은 앞 20자만 마스킹하여 표시합니다.
     *
     * @return 갱신토큰 응답 DTO 목록
     */
    @GetMapping("/tokens")
    @Operation(summary = "JWT 갱신토큰 목록 조회", description = "전체 갱신토큰 목록을 반환합니다. 토큰값은 앞 20자만 표시.")
    public ResponseEntity<List<AdminDto.TokenResponse>> getTokens() {
        return ResponseEntity.ok(adminService.getTokens());
    }

    // =========================================================================
    // 첨부파일 조회 (TAAABB_CFILEM) — M7
    // =========================================================================

    /**
     * 첨부파일 목록 조회
     * 삭제되지 않은(DEL_YN='N') 전체 첨부파일을 반환합니다.
     *
     * @return 첨부파일 응답 DTO 목록
     */
    @GetMapping("/files")
    @Operation(summary = "첨부파일 목록 조회", description = "삭제되지 않은 전체 첨부파일 목록을 반환합니다.")
    public ResponseEntity<List<AdminDto.FileResponse>> getFiles() {
        return ResponseEntity.ok(adminService.getFiles());
    }

    // =========================================================================
    // 상세 로그 조회 (TAAABB_*L) — M9
    // =========================================================================

    /**
     * 상세 로그 테이블 목록 조회
     *
     * @return 조회 가능한 로그 테이블 메타 정보 목록
     */
    @GetMapping("/logs/tables")
    @Operation(summary = "상세 로그 테이블 목록 조회", description = "관리자가 조회할 수 있는 변경 로그 테이블 목록을 반환합니다.")
    public ResponseEntity<List<AdminLogDto.LogTableResponse>> getLogTables() {
        return ResponseEntity.ok(adminLogService.getTables());
    }

    /**
     * 상세 로그 목록 조회
     *
     * @param logKey   로그 테이블 키
     * @param pageable 페이지 정보 (기본: 100건)
     * @return 로그 목록과 컬럼 메타 정보
     */
    @GetMapping("/logs/{logKey}")
    @Operation(summary = "상세 로그 목록 조회", description = "선택한 로그 테이블의 변경 이력을 최신순으로 조회합니다.")
    public ResponseEntity<AdminLogDto.LogPageResponse> getLogs(
            @PathVariable("logKey") String logKey,
            @PageableDefault(size = 100) Pageable pageable) {
        return ResponseEntity.ok(adminLogService.getLogs(logKey, pageable));
    }

    /**
     * 상세 로그 단건 조회
     *
     * @param logKey 로그 테이블 키
     * @param logSno 로그 일련번호
     * @return 로그 상세 스냅샷
     */
    @GetMapping("/logs/{logKey}/{logSno}")
    @Operation(summary = "상세 로그 단건 조회", description = "로그 일련번호에 해당하는 전체 스냅샷을 조회합니다.")
    public ResponseEntity<AdminLogDto.LogDetailResponse> getLogDetail(
            @PathVariable("logKey") String logKey,
            @PathVariable("logSno") String logSno) {
        return ResponseEntity.ok(adminLogService.getLogDetail(logKey, logSno));
    }

    // =========================================================================
    // 대시보드 통계 — M8
    // =========================================================================

    /**
     * 일별 로그인 통계 조회 (대시보드용)
     * 최근 30일간 일별 로그인 성공 건수를 집계하여 반환합니다.
     *
     * @return 일별 로그인 통계 DTO 목록 (날짜 오름차순)
     */
    @GetMapping("/dashboard/login-stats")
    @Operation(summary = "일별 로그인 통계", description = "최근 30일간 일별 로그인 성공 건수를 반환합니다.")
    public ResponseEntity<List<AdminDto.LoginStatResponse>> getLoginStats() {
        return ResponseEntity.ok(adminService.getLoginStats());
    }
}
