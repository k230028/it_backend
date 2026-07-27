package com.kdb.it.domain.budget.document.controller;

import com.kdb.it.domain.budget.document.dto.GuideDocDto;
import com.kdb.it.domain.budget.document.service.GuideDocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가이드 문서 관리 REST 컨트롤러
 *
 * <p>가이드 문서(TPRMPP_BGDOCM)의 CRUD 기능을 담당합니다.
 *
 * <p>기본 URL: {@code /api/guide-documents}
 *
 * <p>첨부파일 연동: 공통 첨부파일 API({@code /api/files})에서 {@code orcDtt=가이드문서}, {@code orcPkVl={docMngNo}}로
 * 파일을 관리합니다.
 *
 * <p>보안: JWT 토큰 인증 필요
 */
@RestController
@RequestMapping("/api/guide-documents")
@RequiredArgsConstructor
@Tag(name = "GuideDocument", description = "가이드 문서 API")
public class GuideDocController {

    /** 가이드 문서 비즈니스 로직 서비스 */
    private final GuideDocService guideDocService;

    /**
     * 가이드 문서 목록 조회
     *
     * <p>DEL_YN='N'인 삭제되지 않은 가이드 문서 전체 목록을 반환합니다. 목록 응답은 본문({@code nacTxtInf})을 제외한 경량 응답입니다. 문서 본문이
     * 필요하면 단건 조회({@code GET /api/guide-documents/{docMngNo}})를 사용합니다.
     *
     * @return HTTP 200 + 가이드 문서 목록 (본문 제외)
     */
    @GetMapping
    @Operation(
            summary = "가이드 문서 목록 조회",
            description =
                    "삭제되지 않은 가이드 문서 전체 목록을 조회합니다. 본문(nacTxtInf)은 포함되지 않으며 "
                            + "본문이 필요하면 단건 조회(GET /api/guide-documents/{docMngNo})를 사용합니다.")
    public ResponseEntity<List<GuideDocDto.ListResponse>> getDocuments() {
        return ResponseEntity.ok(guideDocService.getDocumentList());
    }

    /**
     * 가이드 문서 단건 조회
     *
     * @param docMngNo 문서관리번호 (예: GDOC-2026-0001)
     * @return HTTP 200 + 가이드 문서 상세 정보
     */
    @GetMapping("/{docMngNo}")
    @Operation(summary = "가이드 문서 단건 조회", description = "문서관리번호로 가이드 문서를 조회합니다.")
    public ResponseEntity<GuideDocDto.Response> getDocument(
            @PathVariable("docMngNo") String docMngNo) {
        return ResponseEntity.ok(guideDocService.getDocument(docMngNo));
    }

    /**
     * 가이드 문서 생성
     *
     * <p>채번 규칙: {@code GDOC-{연도}-{4자리 시퀀스}} (예: GDOC-2026-0001) {@code docMngNo}를 미입력 시 자동 채번됩니다.
     *
     * <p>생성 후 첨부파일 등록은 {@code POST /api/files}에서 {@code orcDtt=가이드문서}, {@code orcPkVl={docMngNo}}로
     * 요청합니다.
     *
     * @param request 가이드 문서 생성 요청
     * @return HTTP 201 Created + 생성된 문서관리번호 (Location 헤더 포함)
     */
    @PostMapping
    @Operation(
            summary = "가이드 문서 생성",
            description =
                    "신규 가이드 문서를 생성합니다. docMngNo 미입력 시 자동 채번됩니다. "
                            + "첨부파일은 생성 후 POST /api/files (orcDtt=가이드문서, orcPkVl={docMngNo})로 별도 등록합니다.")
    public ResponseEntity<String> createDocument(
            @Valid @RequestBody GuideDocDto.CreateRequest request) {
        String docMngNo = guideDocService.createDocument(request);
        return ResponseEntity.created(URI.create("/api/guide-documents/" + docMngNo))
                .body(docMngNo);
    }

    /**
     * 가이드 문서 수정
     *
     * @param docMngNo 수정할 문서관리번호
     * @param request 수정 요청 데이터
     * @return HTTP 200 + 수정된 문서관리번호
     */
    @PutMapping("/{docMngNo}")
    @Operation(summary = "가이드 문서 수정", description = "가이드 문서 정보를 수정합니다.")
    public ResponseEntity<String> updateDocument(
            @PathVariable("docMngNo") String docMngNo,
            @Valid @RequestBody GuideDocDto.UpdateRequest request) {
        return ResponseEntity.ok(guideDocService.updateDocument(docMngNo, request));
    }

    /**
     * 가이드 문서 삭제 (Soft Delete)
     *
     * <p>DEL_YN='Y'로 논리 삭제합니다. 물리 삭제는 수행하지 않습니다. 연결된 첨부파일은 {@code DELETE /api/files/bulk}로 별도
     * 정리합니다.
     *
     * @param docMngNo 삭제할 문서관리번호
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{docMngNo}")
    @Operation(
            summary = "가이드 문서 삭제",
            description =
                    "가이드 문서를 논리 삭제합니다 (DEL_YN='Y'). "
                            + "연결된 첨부파일은 DELETE /api/files/bulk (orcDtt=가이드문서, orcPkVl={docMngNo})로 별도 정리합니다.")
    public ResponseEntity<Void> deleteDocument(@PathVariable("docMngNo") String docMngNo) {
        guideDocService.deleteDocument(docMngNo);
        return ResponseEntity.noContent().build();
    }
}
