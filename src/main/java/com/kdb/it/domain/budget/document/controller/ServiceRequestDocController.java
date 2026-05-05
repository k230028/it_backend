package com.kdb.it.domain.budget.document.controller;

import com.kdb.it.domain.budget.document.dto.ServiceRequestDocDto;
import com.kdb.it.domain.budget.document.service.ServiceRequestDocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/**
 * 요구사항 정의서 관리 REST 컨트롤러
 *
 * <p>
 * 요구사항 정의서(TAAABB_BRDOCM)의 CRUD 및 버전 관리 기능을 담당합니다.
 * </p>
 *
 * <p>
 * 기본 URL: {@code /api/documents}
 * </p>
 *
 * <p>
 * 보안: JWT 토큰 인증 필요
 * </p>
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Document", description = "요구사항 정의서 API")
public class ServiceRequestDocController {

    /** 요구사항 정의서 비즈니스 로직 서비스 */
    private final ServiceRequestDocService serviceRequestDocService;

    /**
     * 요구사항 정의서 목록 조회
     *
     * <p>
     * DEL_YN='N'인 삭제되지 않은 요구사항 정의서 전체 목록을 반환합니다.
     * 각 문서관리번호별 최신 버전만 포함됩니다.
     * </p>
     *
     * @return HTTP 200 + 요구사항 정의서 목록
     */
    @GetMapping
    @Operation(summary = "요구사항 정의서 목록 조회", description = "삭제되지 않은 요구사항 정의서 전체 목록을 조회합니다.")
    public ResponseEntity<List<ServiceRequestDocDto.Response>> getDocuments() {
        return ResponseEntity.ok(serviceRequestDocService.getDocumentList());
    }

    /**
     * 요구사항 정의서 단건 조회
     *
     * <p>
     * {@code version} 쿼리 파라미터가 없으면 최신 버전을, 지정되면 해당 버전을 반환합니다.
     * </p>
     *
     * @param docMngNo 문서관리번호 (예: DOC-2026-0001)
     * @param version  문서버전 (선택, 미지정 시 최신 버전)
     * @return HTTP 200 + 요구사항 정의서 상세 정보
     */
    @GetMapping("/{docMngNo}")
    @Operation(summary = "요구사항 정의서 단건 조회", description = "문서관리번호로 요구사항 정의서를 조회합니다. version 미지정 시 최신 버전을 반환합니다.")
    public ResponseEntity<ServiceRequestDocDto.Response> getDocument(
            @PathVariable("docMngNo") String docMngNo,
            @RequestParam(value = "version", required = false) BigDecimal version) {
        return ResponseEntity.ok(serviceRequestDocService.getDocument(docMngNo, version));
    }

    /**
     * 요구사항 정의서 버전 히스토리 조회
     *
     * <p>
     * 동일 문서관리번호의 전체 버전 목록을 버전 내림차순으로 반환합니다.
     * 본문은 제외하고 메타 정보만 포함됩니다.
     * </p>
     *
     * @param docMngNo 문서관리번호
     * @return HTTP 200 + 버전 히스토리 목록 (버전 내림차순)
     */
    @GetMapping("/{docMngNo}/versions")
    @Operation(summary = "요구사항 정의서 버전 히스토리 조회", description = "문서관리번호에 해당하는 전체 버전 목록을 조회합니다.")
    public ResponseEntity<List<ServiceRequestDocDto.VersionResponse>> getVersionHistory(
            @PathVariable("docMngNo") String docMngNo) {
        return ResponseEntity.ok(serviceRequestDocService.getVersionHistory(docMngNo));
    }

    /**
     * 요구사항 정의서 생성
     *
     * <p>
     * 채번 규칙: {@code DOC-{연도}-{4자리 시퀀스}} (예: DOC-2026-0001).
     * {@code docMngNo}를 미입력 시 자동 채번됩니다. 최초 버전은 0.01입니다.
     * </p>
     *
     * @param request 요구사항 정의서 생성 요청
     * @return HTTP 201 Created + 생성된 문서관리번호 (Location 헤더 포함)
     */
    @PostMapping
    @Operation(summary = "요구사항 정의서 생성", description = "신규 요구사항 정의서를 생성합니다. docMngNo 미입력 시 자동 채번됩니다.")
    public ResponseEntity<String> createDocument(@RequestBody ServiceRequestDocDto.CreateRequest request) {
        String docMngNo = serviceRequestDocService.createDocument(request);
        return ResponseEntity.created(URI.create("/api/documents/" + docMngNo)).body(docMngNo);
    }

    /**
     * 요구사항 정의서 새 버전 생성
     *
     * <p>
     * 기존 최신 버전의 업무 필드를 복제하여 버전을 {@code +0.01} 증가시킨 새 레코드를 생성합니다.
     * </p>
     *
     * @param docMngNo 문서관리번호
     * @return HTTP 200 + 생성된 새 버전 안내 메시지
     */
    @PostMapping("/{docMngNo}/versions")
    @Operation(summary = "요구사항 정의서 새 버전 생성", description = "기존 최신 버전을 복제하여 버전 번호를 0.01 증가시킨 새 버전을 생성합니다.")
    public ResponseEntity<String> createNewVersion(@PathVariable("docMngNo") String docMngNo) {
        BigDecimal newVrs = serviceRequestDocService.createNewVersion(docMngNo);
        return ResponseEntity.created(URI.create("/api/documents/" + docMngNo + "/versions"))
                .body(newVrs.toPlainString());
    }

    /**
     * 요구사항 정의서 수정
     *
     * @param docMngNo 수정할 문서관리번호
     * @param request  수정 요청 데이터
     * @return HTTP 200 + 수정된 문서관리번호
     */
    @PutMapping("/{docMngNo}")
    @Operation(summary = "요구사항 정의서 수정", description = "요구사항 정의서 최신 버전을 수정합니다.")
    public ResponseEntity<String> updateDocument(
            @PathVariable("docMngNo") String docMngNo,
            @RequestBody ServiceRequestDocDto.UpdateRequest request) {
        return ResponseEntity.ok(serviceRequestDocService.updateDocument(docMngNo, request));
    }

    /**
     * 요구사항 정의서 삭제 (Soft Delete)
     *
     * <p>
     * {@code version} 미지정 시 해당 문서관리번호의 전체 버전을 일괄 논리 삭제합니다.
     * {@code version} 지정 시 해당 버전만 논리 삭제합니다.
     * DEL_YN='Y'로 논리 삭제하며, 물리 삭제는 수행하지 않습니다.
     * </p>
     *
     * @param docMngNo 삭제할 문서관리번호
     * @param version  삭제할 문서버전 (선택, 미지정 시 전체 버전 일괄 삭제)
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{docMngNo}")
    @Operation(summary = "요구사항 정의서 삭제", description = "요구사항 정의서를 논리 삭제합니다 (DEL_YN='Y'). version 미지정 시 전체 버전 일괄 삭제.")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable("docMngNo") String docMngNo,
            @RequestParam(value = "version", required = false) BigDecimal version) {
        serviceRequestDocService.deleteDocument(docMngNo, version);
        return ResponseEntity.noContent().build();
    }

    /**
     * 요구사항 정의서 대시보드 집계 조회
     *
     * @param bbrC 부서코드 (필수)
     * @return HTTP 200 + 대시보드 집계 응답
     */
    @GetMapping("/dashboard")
    @Operation(summary = "요구사항 정의서 대시보드 조회",
               description = "부서코드 기준 KPI, 월별 추이, 검토 진행 중 목록을 반환합니다.")
    public ResponseEntity<ServiceRequestDocDto.DashboardResponse> getDashboard(
            @RequestParam("bbrC") String bbrC) {
        return ResponseEntity.ok(serviceRequestDocService.getDashboard(bbrC));
    }

    /**
     * 사이드바 배지용 검토 중 문서 수 조회
     *
     * @param bbrC 부서코드 (필수)
     * @return HTTP 200 + 배지 건수
     */
    @GetMapping("/badge-count")
    @Operation(summary = "사이드바 배지 건수 조회",
               description = "검토 진행 중인 문서 수를 반환합니다.")
    public ResponseEntity<ServiceRequestDocDto.BadgeCountResponse> getBadgeCount(
            @RequestParam("bbrC") String bbrC) {
        return ResponseEntity.ok(serviceRequestDocService.getBadgeCount(bbrC));
    }
}
