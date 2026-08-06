package com.kdb.it.infra.file.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.FileOwnershipChecker;
import com.kdb.it.infra.file.authz.FileTargetWriteAuthorizerRegistry;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공통첨부파일기본 REST 컨트롤러
 *
 * <p>시스템 전역에서 사용하는 첨부파일·이미지를 관리하는 엔드포인트입니다. TPRMPP_CFILEM 테이블과 연동됩니다.
 *
 * <p>기본 URL: {@code /api/files}
 *
 * <p>보안: JWT 토큰 인증 필요 (모든 엔드포인트)
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "공통첨부파일기본 API")
public class FileController {

    private final FileService fileService;
    private final FileOwnershipChecker fileOwnershipChecker;
    private final FileTargetWriteAuthorizerRegistry targetWriteAuthorizerRegistry;

    // ─────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────

    /**
     * 검색 조건과 사용자 읽기 범위에 맞는 파일 목록을 조회합니다.
     *
     * @param condition 파일 검색 조건
     * @param userDetails 인증 사용자
     * @return 접근 가능한 파일 목록
     */
    @GetMapping
    @Operation(
            summary = "파일 목록 조회",
            description =
                    "주식별자컬럼명(pkColNm) 기준으로 파일 목록을 조회합니다. "
                            + "pkCone(주식별자내용)을 추가하면 특정 레코드의 파일만 조회합니다. "
                            + "flTpCone('이미지' 또는 '첨부파일')로 파일 종류를 필터링할 수 있습니다.")
    public ResponseEntity<List<FileDto.Response>> getFiles(
            @ParameterObject @ModelAttribute FileDto.SearchCondition condition,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(fileService.getFiles(condition, userDetails));
    }

    /**
     * 여러 부모 키에 연결된 파일을 한 번에 조회합니다.
     *
     * @param pkColNm 주식별자컬럼명
     * @param pkCones 반복 가능한 주식별자내용
     * @param userDetails 인증 사용자
     * @return 요청한 부모 키별 접근 가능한 파일 목록
     * @throws com.kdb.it.exception.CustomGeneralException 종류나 부모 키가 비어 있거나 공백인 경우
     */
    @GetMapping("/batch")
    @Operation(
            summary = "여러 부모의 파일 일괄 조회",
            description =
                    "pkCone 쿼리 파라미터를 반복해 여러 부모의 파일을 한 번에 조회합니다. "
                            + "파일이 없거나 읽기 권한이 없는 부모는 빈 목록으로 반환합니다.")
    public ResponseEntity<Map<String, List<FileDto.Response>>> getFilesBatch(
            @RequestParam("pkColNm") String pkColNm,
            @RequestParam("pkCone") List<String> pkCones,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(fileService.getFilesBatch(pkColNm, pkCones, userDetails));
    }

    /**
     * 파일 메타데이터를 조회합니다.
     *
     * @param flMpnId 파일 매핑 ID
     * @param userDetails 인증 사용자
     * @return 파일 정보
     * @throws org.springframework.security.access.AccessDeniedException 읽기 권한이 없는 경우
     */
    @GetMapping("/{flMpnId}")
    @Operation(summary = "파일 단건 조회", description = "파일매핑ID로 첨부파일 상세 정보를 조회합니다.")
    public ResponseEntity<FileDto.Response> getFile(
            @PathVariable("flMpnId") String flMpnId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 읽기 권한 검증 — 게시판 비공개 파일 등 접근 불가 시 예외 발생
        fileOwnershipChecker.checkReadAccess(flMpnId, userDetails);
        return ResponseEntity.ok(fileService.getFile(flMpnId));
    }

    // ─────────────────────────────────────────
    // 등록
    // ─────────────────────────────────────────

    /**
     * 파일 한 건을 검증하고 저장합니다.
     *
     * @param file 업로드 파일
     * @param flTpCone 파일 유형
     * @param pkCone 원본 식별값
     * @param pkColNm 원본 식별 컬럼명
     * @param userDetails 인증 사용자
     * @return 생성된 파일 정보
     * @throws com.kdb.it.exception.CustomGeneralException 파일 검증 또는 저장에 실패한 경우
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "파일 단건 업로드",
            description =
                    "multipart/form-data 형식으로 파일 1개를 업로드합니다. "
                            + "공통게시판과 검토의견 첨부는 활성 부모 작성자 또는 관리자만 업로드할 수 있습니다. "
                            + "파일물리명은 {서버ID}_{타임스탬프}_{UUID}.{확장자} 형식으로 자동 채번됩니다. "
                            + "파일매핑ID는 Oracle 시퀀스(SQ_TPRMPP_CFILEM_1) 기반으로 FL-{8자리} 형식으로 생성됩니다.")
    public ResponseEntity<FileDto.Response> uploadFile(
            @Parameter(description = "업로드할 파일", required = true) @RequestPart("file")
                    MultipartFile file,
            @Parameter(description = "파일유형내용 ('이미지' 또는 '첨부파일')", required = true)
                    @RequestPart("flTpCone")
                    String flTpCone,
            @Parameter(description = "주식별자내용 (연결할 도메인 레코드 기본키)")
                    @RequestPart(value = "pkCone", required = false)
                    String pkCone,
            @Parameter(description = "주식별자컬럼명 (연결할 도메인 종류, 예: 요구사항정의서)", required = true)
                    @RequestPart("pkColNm")
                    String pkColNm,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder()
                        .flTpCone(flTpCone)
                        .pkCone(pkCone)
                        .pkColNm(pkColNm)
                        .build();

        targetWriteAuthorizerRegistry.verifyTargetWriteAccess(pkColNm, pkCone, userDetails);
        // 업로드 후 전체 파일 정보(previewUrl, downloadUrl 포함) 반환
        FileDto.Response response = fileService.uploadFileAndGet(file, request);
        return ResponseEntity.created(URI.create("/api/files/" + response.getFlMpnId()))
                .body(response);
    }

    /**
     * 여러 파일을 개별 처리하여 성공·실패 결과를 반환합니다.
     *
     * @param files 업로드 파일 목록
     * @param flTpCone 파일 유형
     * @param pkCone 원본 식별값
     * @param pkColNm 원본 식별 컬럼명
     * @param userDetails 인증 사용자
     * @return 파일별 업로드 결과
     */
    @PostMapping(path = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "파일 다건 일괄 업로드",
            description =
                    "여러 파일을 한 번에 업로드합니다. 일부 파일이 실패해도 나머지는 계속 처리됩니다. "
                            + "공통게시판과 검토의견 첨부는 활성 부모 작성자 또는 관리자만 업로드할 수 있습니다. "
                            + "응답에 성공한 파일 목록(successList)과 실패한 파일명 목록(failList)이 포함됩니다.")
    public ResponseEntity<FileDto.BulkUploadResponse> uploadFiles(
            @Parameter(description = "업로드할 파일 목록", required = true) @RequestPart("files")
                    List<MultipartFile> files,
            @Parameter(description = "파일유형내용 ('이미지' 또는 '첨부파일')", required = true)
                    @RequestPart("flTpCone")
                    String flTpCone,
            @Parameter(description = "주식별자내용 (연결할 도메인 레코드 기본키)")
                    @RequestPart(value = "pkCone", required = false)
                    String pkCone,
            @Parameter(description = "주식별자컬럼명 (연결할 도메인 종류)", required = true)
                    @RequestPart("pkColNm")
                    String pkColNm,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder()
                        .flTpCone(flTpCone)
                        .pkCone(pkCone)
                        .pkColNm(pkColNm)
                        .build();

        targetWriteAuthorizerRegistry.verifyTargetWriteAccess(pkColNm, pkCone, userDetails);
        return ResponseEntity.ok(fileService.uploadFiles(files, request));
    }

    // ─────────────────────────────────────────
    // 수정
    // ─────────────────────────────────────────

    /**
     * 파일의 원본 연결 메타데이터를 수정합니다.
     *
     * @param flMpnId 파일 매핑 ID
     * @param request 수정 요청
     * @param userDetails 인증 사용자
     * @return 수정된 파일 매핑 ID
     * @throws org.springframework.security.access.AccessDeniedException 쓰기 권한이 없는 경우
     */
    @PutMapping("/{flMpnId}")
    @Operation(
            summary = "파일 메타데이터 수정",
            description =
                    "파일이 연결된 원본 도메인 정보(주식별자컬럼명, 주식별자내용)를 변경합니다. "
                            + "파일 자체(파일물리명, 저장경로)는 변경되지 않습니다. "
                            + "파일 교체가 필요하면 삭제 후 재업로드를 사용하세요. "
                            + "현재 파일 쓰기 권한과 새 첨부 대상 쓰기 권한을 모두 검증합니다. "
                            + "공통게시판과 검토의견 대상은 활성 부모 작성자 또는 관리자만 선택할 수 있습니다.")
    public ResponseEntity<String> updateFileMeta(
            @PathVariable("flMpnId") String flMpnId,
            @org.springframework.web.bind.annotation.RequestBody FileDto.UpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 쓰기 권한 검증 — 파일 종류별 작성자 또는 관리자 정책을 적용한다.
        fileOwnershipChecker.verifyWriteAccess(flMpnId, userDetails);
        targetWriteAuthorizerRegistry.verifyTargetWriteAccess(
                request.getPkColNm(), request.getPkCone(), userDetails);
        String updatedFlMpnId = fileService.updateFileMeta(flMpnId, request);
        return ResponseEntity.ok(updatedFlMpnId);
    }

    // ─────────────────────────────────────────
    // 삭제
    // ─────────────────────────────────────────

    /**
     * 파일을 논리 삭제합니다.
     *
     * @param flMpnId 파일 매핑 ID
     * @param userDetails 인증 사용자
     * @return 응답 본문이 없는 성공 응답
     * @throws org.springframework.security.access.AccessDeniedException 삭제 권한이 없는 경우
     */
    @DeleteMapping("/{flMpnId}")
    @Operation(
            summary = "파일 단건 삭제",
            description =
                    "파일을 논리 삭제합니다(DEL_YN='Y'). 검토의견 첨부는 활성 댓글 작성자 또는 관리자만 삭제할 수 있으며, "
                            + "다른 종류는 업로더 또는 관리자만 삭제할 수 있습니다. 물리 파일은 서버에 유지됩니다.")
    public ResponseEntity<Void> deleteFile(
            @PathVariable("flMpnId") String flMpnId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 쓰기 권한 검증 — 파일 종류별 작성자 또는 관리자 정책을 적용한다.
        fileOwnershipChecker.verifyWriteAccess(flMpnId, userDetails);
        fileService.deleteFile(flMpnId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 원본 식별정보에 연결된 파일을 권한 범위 안에서 일괄 논리 삭제합니다.
     *
     * @param request 일괄 삭제 요청
     * @param userDetails 인증 사용자
     * @return 삭제된 파일 수
     * @throws org.springframework.security.access.AccessDeniedException 삭제 권한이 없는 파일이 포함된 경우
     */
    @DeleteMapping("/bulk")
    @Operation(
            summary = "원본 기준 파일 일괄 삭제",
            description =
                    "특정 도메인 레코드(pkColNm + pkCone)에 연결된 모든 파일을 일괄 논리 삭제합니다. "
                            + "프로젝트나 문서 삭제 시 연관 파일을 일괄 정리할 때 사용합니다. "
                            + "삭제된 파일 수를 반환합니다.")
    public ResponseEntity<Integer> deleteFilesByOrc(
            @org.springframework.web.bind.annotation.RequestBody FileDto.BulkDeleteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 소유권 검증은 서비스 계층에서 수행 — 비관리자는 본인 소유 파일만 일괄 삭제 가능
        int deletedCount =
                fileService.deleteFilesByOrc(
                        request.getPkColNm(), request.getPkCone(), userDetails);
        return ResponseEntity.ok(deletedCount);
    }

    // ─────────────────────────────────────────
    // 다운로드
    // ─────────────────────────────────────────

    /**
     * 파일을 첨부 응답으로 내려받습니다.
     *
     * @param flMpnId 파일 매핑 ID
     * @param userDetails 인증 사용자
     * @return 파일 리소스와 다운로드 헤더
     * @throws org.springframework.security.access.AccessDeniedException 읽기 권한이 없는 경우
     */
    @GetMapping("/{flMpnId}/download")
    @Operation(
            summary = "파일 다운로드",
            description =
                    "파일매핑ID로 파일을 다운로드합니다. "
                            + "응답 헤더에 Content-Disposition: attachment가 설정되어 브라우저에서 자동 다운로드됩니다. "
                            + "파일명이 그대로 사용되며 한글 파일명도 UTF-8로 지원합니다.")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(
            @PathVariable("flMpnId") String flMpnId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 읽기 권한 검증 — 게시판 비공개 파일 등 접근 불가 시 예외 발생
        fileOwnershipChecker.checkReadAccess(flMpnId, userDetails);

        FileService.FileDownloadResult result = fileService.downloadFile(flMpnId);

        // Content-Disposition: attachment 헤더 설정 (한글 파일명 UTF-8 인코딩)
        ContentDisposition contentDisposition =
                ContentDisposition.attachment()
                        .filename(result.originalFilename(), StandardCharsets.UTF_8)
                        .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(result.resource());
    }

    /**
     * 파일을 브라우저 미리보기 응답으로 반환합니다.
     *
     * @param flMpnId 파일 매핑 ID
     * @param userDetails 인증 사용자
     * @return 파일 리소스와 인라인 표시 헤더
     * @throws org.springframework.security.access.AccessDeniedException 읽기 권한이 없는 경우
     */
    @GetMapping("/{flMpnId}/preview")
    @Operation(
            summary = "이미지 미리보기",
            description =
                    "이미지 파일을 브라우저에서 인라인으로 표시합니다. "
                            + "파일유형내용이 '이미지'인 파일에 사용하세요. "
                            + "Content-Type이 파일 확장자 기반으로 자동 감지되어 브라우저에서 이미지가 올바르게 렌더링됩니다. "
                            + "Tiptap 에디터의 img src로 사용 시 httpOnly 쿠키 인증이 자동 적용됩니다.")
    public ResponseEntity<org.springframework.core.io.Resource> previewFile(
            @PathVariable("flMpnId") String flMpnId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 읽기 권한 검증 — 게시판 비공개 파일 등 접근 불가 시 예외 발생
        fileOwnershipChecker.checkReadAccess(flMpnId, userDetails);

        FileService.FileDownloadResult result = fileService.downloadFile(flMpnId);

        ContentDisposition contentDisposition =
                ContentDisposition.inline()
                        .filename(result.originalFilename(), StandardCharsets.UTF_8)
                        .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);

        MediaType mediaType = MediaType.parseMediaType(result.contentType());

        return ResponseEntity.ok().headers(headers).contentType(mediaType).body(result.resource());
    }
}
