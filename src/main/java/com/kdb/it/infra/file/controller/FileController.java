package com.kdb.it.infra.file.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.FileOwnershipChecker;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.service.FileService;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 공통첨부파일기본 REST 컨트롤러
 *
 * <p>
 * 시스템 전역에서 사용하는 첨부파일·이미지를 관리하는 엔드포인트입니다.
 * TPRMPP_CFILEM 테이블과 연동됩니다.
 * </p>
 *
 * <p>기본 URL: {@code /api/files}</p>
 *
 * <p>보안: JWT 토큰 인증 필요 (모든 엔드포인트)</p>
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "공통첨부파일기본 API")
public class FileController {

        private final FileService fileService;
        private final FileOwnershipChecker fileOwnershipChecker;

        // ─────────────────────────────────────────
        // 조회
        // ─────────────────────────────────────────

        @GetMapping
        @Operation(summary = "파일 목록 조회", description = "주식별자컬럼명(pkColNm) 기준으로 파일 목록을 조회합니다. " +
                        "pkCone(주식별자내용)을 추가하면 특정 레코드의 파일만 조회합니다. " +
                        "flTpCone('이미지' 또는 '첨부파일')로 파일 종류를 필터링할 수 있습니다.")
        public ResponseEntity<List<FileDto.Response>> getFiles(
                        @ModelAttribute FileDto.SearchCondition condition,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ResponseEntity.ok(fileService.getFiles(condition, userDetails));
        }

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

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "파일 단건 업로드", description = "multipart/form-data 형식으로 파일 1개를 업로드합니다. " +
                        "파일물리명은 {서버ID}_{타임스탬프}_{UUID}.{확장자} 형식으로 자동 채번됩니다. " +
                        "파일매핑ID는 Oracle 시퀀스(SEQ_CFILEM) 기반으로 FL_{8자리} 형식으로 생성됩니다.")
        public ResponseEntity<FileDto.Response> uploadFile(
                        @Parameter(description = "업로드할 파일", required = true) @RequestPart("file") MultipartFile file,
                        @Parameter(description = "파일유형내용 ('이미지' 또는 '첨부파일')", required = true) @RequestPart("flTpCone") String flTpCone,
                        @Parameter(description = "주식별자내용 (연결할 도메인 레코드 기본키)") @RequestPart(value = "pkCone", required = false) String pkCone,
                        @Parameter(description = "주식별자컬럼명 (연결할 도메인 종류, 예: 요구사항정의서)", required = true) @RequestPart("pkColNm") String pkColNm) {

                FileDto.UploadRequest request = FileDto.UploadRequest.builder()
                                .flTpCone(flTpCone)
                                .pkCone(pkCone)
                                .pkColNm(pkColNm)
                                .build();

                // 업로드 후 전체 파일 정보(previewUrl, downloadUrl 포함) 반환
                FileDto.Response response = fileService.uploadFileAndGet(file, request);
                return ResponseEntity
                                .created(URI.create("/api/files/" + response.getFlMpnId()))
                                .body(response);
        }

        @PostMapping(path = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "파일 다건 일괄 업로드", description = "여러 파일을 한 번에 업로드합니다. 일부 파일이 실패해도 나머지는 계속 처리됩니다. " +
                        "응답에 성공한 파일 목록(successList)과 실패한 파일명 목록(failList)이 포함됩니다.")
        public ResponseEntity<FileDto.BulkUploadResponse> uploadFiles(
                        @Parameter(description = "업로드할 파일 목록", required = true) @RequestPart("files") List<MultipartFile> files,
                        @Parameter(description = "파일유형내용 ('이미지' 또는 '첨부파일')", required = true) @RequestPart("flTpCone") String flTpCone,
                        @Parameter(description = "주식별자내용 (연결할 도메인 레코드 기본키)") @RequestPart(value = "pkCone", required = false) String pkCone,
                        @Parameter(description = "주식별자컬럼명 (연결할 도메인 종류)", required = true) @RequestPart("pkColNm") String pkColNm) {

                FileDto.UploadRequest request = FileDto.UploadRequest.builder()
                                .flTpCone(flTpCone)
                                .pkCone(pkCone)
                                .pkColNm(pkColNm)
                                .build();

                return ResponseEntity.ok(fileService.uploadFiles(files, request));
        }

        // ─────────────────────────────────────────
        // 수정
        // ─────────────────────────────────────────

        @PutMapping("/{flMpnId}")
        @Operation(summary = "파일 메타데이터 수정", description = "파일이 연결된 원본 도메인 정보(주식별자컬럼명, 주식별자내용)를 변경합니다. " +
                        "파일 자체(파일물리명, 저장경로)는 변경되지 않습니다. " +
                        "파일 교체가 필요하면 삭제 후 재업로드를 사용하세요.")
        public ResponseEntity<String> updateFileMeta(
                        @PathVariable("flMpnId") String flMpnId,
                        @org.springframework.web.bind.annotation.RequestBody FileDto.UpdateRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                // 소유권 검증 — 업로드자 ≠ 현재 사용자이면 예외 발생 (단건 삭제와 동일 정책)
                fileOwnershipChecker.checkOwnership(flMpnId, userDetails.getUsername());
                String updatedFlMpnId = fileService.updateFileMeta(flMpnId, request);
                return ResponseEntity.ok(updatedFlMpnId);
        }

        // ─────────────────────────────────────────
        // 삭제
        // ─────────────────────────────────────────

        @DeleteMapping("/{flMpnId}")
        @Operation(summary = "파일 단건 삭제", description = "파일을 논리 삭제합니다(DEL_YN='Y'). 본인이 업로드한 파일만 삭제 가능합니다. 물리 파일은 서버에 유지됩니다.")
        public ResponseEntity<Void> deleteFile(
                        @PathVariable("flMpnId") String flMpnId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                // 소유권 검증 — 업로드자 ≠ 현재 사용자이면 예외 발생 (SEC-02)
                fileOwnershipChecker.checkOwnership(flMpnId, userDetails.getUsername());
                fileService.deleteFile(flMpnId);
                return ResponseEntity.noContent().build();
        }

        @DeleteMapping("/bulk")
        @Operation(summary = "원본 기준 파일 일괄 삭제", description = "특정 도메인 레코드(pkColNm + pkCone)에 연결된 모든 파일을 일괄 논리 삭제합니다. " +
                        "프로젝트나 문서 삭제 시 연관 파일을 일괄 정리할 때 사용합니다. " +
                        "삭제된 파일 수를 반환합니다.")
        public ResponseEntity<Integer> deleteFilesByOrc(
                        @org.springframework.web.bind.annotation.RequestBody FileDto.BulkDeleteRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                // 소유권 검증은 서비스 계층에서 수행 — 비관리자는 본인 소유 파일만 일괄 삭제 가능
                int deletedCount = fileService.deleteFilesByOrc(request.getPkColNm(), request.getPkCone(), userDetails);
                return ResponseEntity.ok(deletedCount);
        }

        // ─────────────────────────────────────────
        // 다운로드
        // ─────────────────────────────────────────

        @GetMapping("/{flMpnId}/download")
        @Operation(summary = "파일 다운로드", description = "파일매핑ID로 파일을 다운로드합니다. " +
                        "응답 헤더에 Content-Disposition: attachment가 설정되어 브라우저에서 자동 다운로드됩니다. " +
                        "파일명이 그대로 사용되며 한글 파일명도 UTF-8로 지원합니다.")
        public ResponseEntity<org.springframework.core.io.Resource> downloadFile(
                        @PathVariable("flMpnId") String flMpnId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {

                // 읽기 권한 검증 — 게시판 비공개 파일 등 접근 불가 시 예외 발생
                fileOwnershipChecker.checkReadAccess(flMpnId, userDetails);

                FileService.FileDownloadResult result = fileService.downloadFile(flMpnId);

                // Content-Disposition: attachment 헤더 설정 (한글 파일명 UTF-8 인코딩)
                ContentDisposition contentDisposition = ContentDisposition.attachment()
                                .filename(result.originalFilename(), StandardCharsets.UTF_8)
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentDisposition(contentDisposition);

                return ResponseEntity.ok()
                                .headers(headers)
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .body(result.resource());
        }

        @GetMapping("/{flMpnId}/preview")
        @Operation(summary = "이미지 미리보기", description = "이미지 파일을 브라우저에서 인라인으로 표시합니다. " +
                        "파일유형내용이 '이미지'인 파일에 사용하세요. " +
                        "Content-Type이 파일 확장자 기반으로 자동 감지되어 브라우저에서 이미지가 올바르게 렌더링됩니다. " +
                        "Tiptap 에디터의 img src로 사용 시 httpOnly 쿠키 인증이 자동 적용됩니다.")
        public ResponseEntity<org.springframework.core.io.Resource> previewFile(
                        @PathVariable("flMpnId") String flMpnId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {

                // 읽기 권한 검증 — 게시판 비공개 파일 등 접근 불가 시 예외 발생
                fileOwnershipChecker.checkReadAccess(flMpnId, userDetails);

                FileService.FileDownloadResult result = fileService.downloadFile(flMpnId);

                ContentDisposition contentDisposition = ContentDisposition.inline()
                                .filename(result.originalFilename(), StandardCharsets.UTF_8)
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentDisposition(contentDisposition);

                MediaType mediaType = MediaType.parseMediaType(result.contentType());

                return ResponseEntity.ok()
                                .headers(headers)
                                .contentType(mediaType)
                                .body(result.resource());
        }
}
