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
 * 공통 첨부파일 관리 REST 컨트롤러
 *
 * <p>
 * 시스템 전역에서 사용하는 첨부파일·이미지를 관리하는 엔드포인트입니다.
 * TAAABB_CFILEM 테이블과 연동됩니다.
 * </p>
 *
 * <p>
 * 기본 URL: {@code /api/files}
 * </p>
 *
 * <p>
 * 서버 파일명 채번 규칙: {@code {서버ID}_{yyyyMMddHHmmss}_{UUID}.{확장자}}
 * → 1번·2번 서버 동시 운영 시 파일명 충돌 완전 방지
 * </p>
 *
 * <p>
 * 보안: JWT 토큰 인증 필요 (모든 엔드포인트)
 * </p>
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "공통 첨부파일 관리 API")
public class FileController {

        /** 공통 첨부파일 비즈니스 로직 서비스 */
        private final FileService fileService;

        /** 파일 소유권 검증 — SEC-02 */
        private final FileOwnershipChecker fileOwnershipChecker;

        // ─────────────────────────────────────────
        // 조회
        // ─────────────────────────────────────────

        /**
         * 파일 목록 조회 (조건 검색)
         *
         * <p>
         * 원본구분(orcDtt)은 필수이며, 원본PK값(orcPkVl)·파일구분(flDtt)은 선택입니다.
         * </p>
         *
         * <p>
         * 조회 예시:
         * </p>
         * <ul>
         * <li>{@code GET /api/files?orcDtt=요구사항정의서} → 요구사항정의서 파일 전체</li>
         * <li>{@code GET /api/files?orcDtt=요구사항정의서&orcPkVl=PRJ-2026-0001} → 특정 레코드
         * 파일</li>
         * <li>{@code GET /api/files?orcDtt=요구사항정의서&orcPkVl=PRJ-2026-0001&flDtt=이미지} →
         * 이미지만</li>
         * </ul>
         *
         * @param condition 검색 조건 (orcDtt 필수, orcPkVl·flDtt 선택)
         * @return HTTP 200 + 파일 목록
         */
        @GetMapping
        @Operation(summary = "파일 목록 조회", description = "원본구분(orcDtt) 기준으로 파일 목록을 조회합니다. " +
                        "orcPkVl(원본PK값)을 추가하면 특정 레코드의 파일만 조회합니다. " +
                        "flDtt('이미지' 또는 '첨부파일')로 파일 종류를 필터링할 수 있습니다.")
        public ResponseEntity<List<FileDto.Response>> getFiles(
                        @ModelAttribute FileDto.SearchCondition condition) {
                return ResponseEntity.ok(fileService.getFiles(condition));
        }

        /**
         * 파일 단건 조회
         *
         * @param flMngNo 파일관리번호 (예: FL_00000001)
         * @return HTTP 200 + 파일 상세 정보
         */
        @GetMapping("/{flMngNo}")
        @Operation(summary = "파일 단건 조회", description = "파일관리번호로 첨부파일 상세 정보를 조회합니다.")
        public ResponseEntity<FileDto.Response> getFile(
                        @PathVariable("flMngNo") String flMngNo) {
                return ResponseEntity.ok(fileService.getFile(flMngNo));
        }

        // ─────────────────────────────────────────
        // 등록
        // ─────────────────────────────────────────

        /**
         * 파일 단건 업로드
         *
         * <p>
         * {@code multipart/form-data} 형식으로 파일과 메타데이터를 함께 전송합니다.
         * </p>
         *
         * <p>
         * 서버 파일명은 {@code {서버ID}_{yyyyMMddHHmmss}_{UUID}.{확장자}} 형식으로 자동 채번됩니다.
         * </p>
         *
         * @param file    업로드 파일 (multipart)
         * @param flDtt   파일구분 ('이미지' 또는 '첨부파일')
         * @param orcPkVl 원본PK값 (연결할 도메인 레코드 기본키, 선택)
         * @param orcDtt  원본구분 (연결할 도메인 종류, 예: 요구사항정의서)
         * @return HTTP 201 Created + 생성된 파일관리번호 (Location 헤더 포함)
         */
        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "파일 단건 업로드", description = "multipart/form-data 형식으로 파일 1개를 업로드합니다. " +
                        "서버 파일명은 {서버ID}_{타임스탬프}_{UUID}.{확장자} 형식으로 자동 채번됩니다. " +
                        "파일관리번호는 Oracle 시퀀스(SEQ_CFILEM) 기반으로 FL_{8자리} 형식으로 생성됩니다. " +
                        "응답에 previewUrl, downloadUrl이 포함되어 Tiptap 에디터에서 바로 사용 가능합니다.")
        public ResponseEntity<FileDto.Response> uploadFile(
                        @Parameter(description = "업로드할 파일", required = true) @RequestPart("file") MultipartFile file,
                        @Parameter(description = "파일구분 ('이미지' 또는 '첨부파일')", required = true) @RequestPart("flDtt") String flDtt,
                        @Parameter(description = "원본PK값 (연결할 도메인 레코드 기본키)") @RequestPart(value = "orcPkVl", required = false) String orcPkVl,
                        @Parameter(description = "원본구분 (연결할 도메인 종류, 예: 요구사항정의서)", required = true) @RequestPart("orcDtt") String orcDtt) {

                FileDto.UploadRequest request = FileDto.UploadRequest.builder()
                                .flDtt(flDtt)
                                .orcPkVl(orcPkVl)
                                .orcDtt(orcDtt)
                                .build();

                // 업로드 후 전체 파일 정보(previewUrl, downloadUrl 포함) 반환
                // Tiptap 에디터에서 response.previewUrl을 img src에 바로 주입하기 위함
                FileDto.Response response = fileService.uploadFileAndGet(file, request);
                return ResponseEntity
                                .created(URI.create("/api/files/" + response.getFlMngNo()))
                                .body(response);
        }

        /**
         * 파일 다건 일괄 업로드
         *
         * <p>
         * 여러 파일을 한 번에 업로드합니다. 개별 파일 실패 시 나머지는 계속 처리됩니다.
         * 응답에 성공 목록과 실패 파일명 목록이 모두 포함됩니다.
         * </p>
         *
         * @param files   업로드할 파일 목록 (multipart)
         * @param flDtt   파일구분 (모든 파일에 동일 적용)
         * @param orcPkVl 원본PK값 (모든 파일에 동일 적용, 선택)
         * @param orcDtt  원본구분 (모든 파일에 동일 적용)
         * @return HTTP 200 + 업로드 결과 (성공 목록 + 실패 파일명 목록)
         */
        @PostMapping(path = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "파일 다건 일괄 업로드", description = "여러 파일을 한 번에 업로드합니다. 일부 파일이 실패해도 나머지는 계속 처리됩니다. " +
                        "응답에 성공한 파일 목록(successList)과 실패한 파일명 목록(failList)이 포함됩니다.")
        public ResponseEntity<FileDto.BulkUploadResponse> uploadFiles(
                        @Parameter(description = "업로드할 파일 목록", required = true) @RequestPart("files") List<MultipartFile> files,
                        @Parameter(description = "파일구분 ('이미지' 또는 '첨부파일')", required = true) @RequestPart("flDtt") String flDtt,
                        @Parameter(description = "원본PK값 (연결할 도메인 레코드 기본키)") @RequestPart(value = "orcPkVl", required = false) String orcPkVl,
                        @Parameter(description = "원본구분 (연결할 도메인 종류)", required = true) @RequestPart("orcDtt") String orcDtt) {

                FileDto.UploadRequest request = FileDto.UploadRequest.builder()
                                .flDtt(flDtt)
                                .orcPkVl(orcPkVl)
                                .orcDtt(orcDtt)
                                .build();

                return ResponseEntity.ok(fileService.uploadFiles(files, request));
        }

        // ─────────────────────────────────────────
        // 수정
        // ─────────────────────────────────────────

        /**
         * 파일 메타데이터 수정
         *
         * <p>
         * 파일이 연결된 원본 도메인 정보(orcPkVl, orcDtt)를 변경합니다.
         * 파일 자체(서버파일명, 저장경로)는 변경되지 않습니다.
         * 파일 교체는 삭제({@code DELETE /api/files/{flMngNo}}) 후 재업로드를 사용하세요.
         * </p>
         *
         * @param flMngNo 수정할 파일관리번호
         * @param request 수정 요청 DTO (orcPkVl, orcDtt)
         * @return HTTP 200 + 수정된 파일관리번호
         */
        @PutMapping("/{flMngNo}")
        @Operation(summary = "파일 메타데이터 수정", description = "파일이 연결된 원본 도메인 정보(원본구분, 원본PK값)를 변경합니다. " +
                        "파일 자체(서버파일명, 저장경로)는 변경되지 않습니다. " +
                        "파일 교체가 필요하면 삭제 후 재업로드를 사용하세요.")
        public ResponseEntity<String> updateFileMeta(
                        @PathVariable("flMngNo") String flMngNo,
                        @org.springframework.web.bind.annotation.RequestBody FileDto.UpdateRequest request) {
                String updatedFlMngNo = fileService.updateFileMeta(flMngNo, request);
                return ResponseEntity.ok(updatedFlMngNo);
        }

        // ─────────────────────────────────────────
        // 삭제
        // ─────────────────────────────────────────

        /**
         * 파일 단건 논리 삭제 (Soft Delete)
         *
         * <p>
         * DB의 DEL_YN을 'Y'로 변경합니다. 물리 파일은 삭제하지 않습니다.
         * </p>
         *
         * @param flMngNo 삭제할 파일관리번호
         * @return HTTP 204 No Content
         */
        @DeleteMapping("/{flMngNo}")
        @Operation(summary = "파일 단건 삭제", description = "파일을 논리 삭제합니다(DEL_YN='Y'). 본인이 업로드한 파일만 삭제 가능합니다. 물리 파일은 서버에 유지됩니다.")
        public ResponseEntity<Void> deleteFile(
                        @PathVariable("flMngNo") String flMngNo,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                // 소유권 검증 — 업로드자 ≠ 현재 사용자이면 예외 발생 (SEC-02)
                fileOwnershipChecker.checkOwnership(flMngNo, userDetails.getUsername());
                fileService.deleteFile(flMngNo);
                return ResponseEntity.noContent().build();
        }

        /**
         * 원본 기준 파일 일괄 논리 삭제
         *
         * <p>
         * 특정 도메인 레코드에 연결된 모든 파일을 일괄 논리 삭제합니다.
         * 프로젝트·문서 삭제 시 연관 파일을 정리할 때 사용합니다.
         * </p>
         *
         * @param request 일괄 삭제 요청 DTO (orcDtt, orcPkVl)
         * @return HTTP 200 + 삭제된 파일 수
         */
        @DeleteMapping("/bulk")
        @Operation(summary = "원본 기준 파일 일괄 삭제", description = "특정 도메인 레코드(orcDtt + orcPkVl)에 연결된 모든 파일을 일괄 논리 삭제합니다. " +
                        "프로젝트나 문서 삭제 시 연관 파일을 일괄 정리할 때 사용합니다. " +
                        "삭제된 파일 수를 반환합니다.")
        public ResponseEntity<Integer> deleteFilesByOrc(
                        @org.springframework.web.bind.annotation.RequestBody FileDto.BulkDeleteRequest request) {
                int deletedCount = fileService.deleteFilesByOrc(request.getOrcDtt(), request.getOrcPkVl());
                return ResponseEntity.ok(deletedCount);
        }

        // ─────────────────────────────────────────
        // 다운로드
        // ─────────────────────────────────────────

        /**
         * 파일 다운로드
         *
         * <p>
         * 파일 바이너리를 스트림으로 반환합니다.
         * {@code Content-Disposition: attachment} 헤더로 브라우저 다운로드가 트리거됩니다.
         * 파일명은 UTF-8 인코딩됩니다 (한글 파일명 지원).
         * </p>
         *
         * @param flMngNo 다운로드할 파일관리번호
         * @return HTTP 200 + 파일 바이너리 (다운로드)
         */
        @GetMapping("/{flMngNo}/download")
        @Operation(summary = "파일 다운로드", description = "파일관리번호로 파일을 다운로드합니다. " +
                        "응답 헤더에 Content-Disposition: attachment가 설정되어 브라우저에서 자동 다운로드됩니다. " +
                        "원본파일명이 그대로 사용되며 한글 파일명도 UTF-8로 지원합니다.")
        public ResponseEntity<org.springframework.core.io.Resource> downloadFile(
                        @PathVariable("flMngNo") String flMngNo) {

                FileService.FileDownloadResult result = fileService.downloadFile(flMngNo);

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

        /**
         * 이미지 미리보기
         *
         * <p>
         * 이미지 파일을 인라인으로 반환합니다.
         * 브라우저에서 직접 표시 가능하며, 파일구분이 '이미지'인 경우에만 사용을 권장합니다.
         * </p>
         *
         * <p>
         * Tiptap 에디터의 {@code <img src="/api/files/{flMngNo}/preview">} 형태로 사용합니다.
         * JWT Access Token이 httpOnly 쿠키에 저장되므로 브라우저가 자동으로 쿠키를 전송하여
         * 별도 인증 처리 없이 이미지가 정상 표시됩니다.
         * </p>
         *
         * <p>
         * Content-Type은 파일 확장자 기반으로 자동 감지됩니다
         * (image/jpeg, image/png 등 → 브라우저 이미지 렌더링 정상 동작).
         * </p>
         *
         * @param flMngNo 미리보기할 파일관리번호
         * @return HTTP 200 + 이미지 바이너리 (인라인 표시, 정확한 MIME 타입)
         */
        @GetMapping("/{flMngNo}/preview")
        @Operation(summary = "이미지 미리보기", description = "이미지 파일을 브라우저에서 인라인으로 표시합니다. " +
                        "파일구분이 '이미지'인 파일에 사용하세요. " +
                        "Content-Type이 파일 확장자 기반으로 자동 감지되어 브라우저에서 이미지가 올바르게 렌더링됩니다. " +
                        "Tiptap 에디터의 img src로 사용 시 httpOnly 쿠키 인증이 자동 적용됩니다.")
        public ResponseEntity<org.springframework.core.io.Resource> previewFile(
                        @PathVariable("flMngNo") String flMngNo) {

                FileService.FileDownloadResult result = fileService.downloadFile(flMngNo);

                // Content-Disposition: inline 헤더 설정 (브라우저 직접 표시)
                ContentDisposition contentDisposition = ContentDisposition.inline()
                                .filename(result.originalFilename(), StandardCharsets.UTF_8)
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentDisposition(contentDisposition);

                // 파일 확장자 기반 MIME 타입 사용 (image/jpeg, image/png 등)
                // → APPLICATION_OCTET_STREAM 반환 시 브라우저가 이미지를 렌더링하지 않음
                MediaType mediaType = MediaType.parseMediaType(result.contentType());

                return ResponseEntity.ok()
                                .headers(headers)
                                .contentType(mediaType)
                                .body(result.resource());
        }
}
