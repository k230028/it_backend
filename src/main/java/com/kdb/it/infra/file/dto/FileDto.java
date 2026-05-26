package com.kdb.it.infra.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공통 첨부파일 관리 DTO 모음
 *
 * <p>
 * TPRMPP_CFILEM 테이블의 CRUD 및 다운로드 API 요청/응답에 사용되는
 * 정적 중첩 클래스 방식 DTO입니다.
 * </p>
 */
public class FileDto {

    /**
     * 파일 업로드 요청 DTO
     *
     * <p>
     * multipart/form-data 형식으로 전달받는 파일 메타데이터입니다.
     * 실제 파일 바이너리는 {@code MultipartFile}로 별도 수신합니다.
     * </p>
     */
    @Schema(name = "FileDto.UploadRequest", description = "파일 업로드 요청 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UploadRequest {

        @Schema(description = "파일구분 ('이미지' 또는 '첨부파일')", example = "첨부파일", requiredMode = Schema.RequiredMode.REQUIRED)
        private String flDtt;

        @Schema(description = "원본PK값 (연결할 도메인 레코드 기본키)", example = "PRJ-2026-0001")
        private String orcPkVl;

        @Schema(description = "원본구분 (연결할 도메인 종류)", example = "요구사항정의서", requiredMode = Schema.RequiredMode.REQUIRED)
        private String orcDtt;
    }

    /**
     * 파일 메타데이터 수정 요청 DTO
     *
     * <p>
     * 파일이 연결된 원본 도메인 정보만 변경할 수 있습니다.
     * 파일 자체 교체는 삭제 후 재업로드를 사용합니다.
     * </p>
     */
    @Schema(name = "FileDto.UpdateRequest", description = "파일 메타데이터 수정 요청 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        @Schema(description = "변경할 원본PK값", example = "PRJ-2026-0002")
        private String orcPkVl;

        @Schema(description = "변경할 원본구분", example = "정보화사업", requiredMode = Schema.RequiredMode.REQUIRED)
        private String orcDtt;
    }

    /**
     * 파일 단건·목록 조회 응답 DTO
     */
    @Schema(name = "FileDto.Response", description = "파일 조회 응답 DTO")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {

        @Schema(description = "파일관리번호", example = "FL_00000001")
        private String flMngNo;

        @Schema(description = "원본파일명", example = "요구사항정의서_v1.0.pdf")
        private String orcFlNm;

        @Schema(description = "서버파일명", example = "SVR1_20260315143022_550e8400e29b41d4a716446655440000.pdf")
        private String svrFlNm;

        @Schema(description = "파일저장경로", example = "/data/files/요구사항정의서/2026/03")
        private String flKpnPth;

        @Schema(description = "파일구분 ('이미지' 또는 '첨부파일')", example = "첨부파일")
        private String flDtt;

        @Schema(description = "원본PK값", example = "PRJ-2026-0001")
        private String orcPkVl;

        @Schema(description = "원본구분", example = "요구사항정의서")
        private String orcDtt;

        @Schema(description = "최초등록일시")
        private LocalDateTime fstEnrDtm;

        @Schema(description = "최초등록자 사번", example = "EMP0001234")
        private String fstEnrUsid;

        /**
         * 이미지 미리보기 URL
         *
         * <p>
         * flDtt가 '이미지'인 경우 Tiptap 에디터의 img src로 직접 사용합니다.
         * 예: {@code /api/files/FL_00000001/preview}
         * httpOnly 쿠키 인증이 자동 적용되므로 별도 처리가 필요 없습니다.
         * </p>
         */
        @Schema(description = "이미지 미리보기 URL (flDtt='이미지'인 경우 Tiptap img src로 사용)",
                example = "/api/files/FL_00000001/preview")
        private String previewUrl;

        /**
         * 파일 다운로드 URL
         *
         * <p>
         * 첨부파일 다운로드 링크에 사용합니다.
         * 예: {@code /api/files/FL_00000001/download}
         * </p>
         */
        @Schema(description = "파일 다운로드 URL", example = "/api/files/FL_00000001/download")
        private String downloadUrl;
    }

    /**
     * 원본 기준 일괄 삭제 요청 DTO
     *
     * <p>
     * 특정 도메인 레코드(예: 요구사항정의서 PRJ-2026-0001)에 연결된
     * 모든 파일을 일괄 논리 삭제할 때 사용합니다.
     * 프로젝트·문서 삭제 시 연관 파일 정리에 활용합니다.
     * </p>
     */
    @Schema(name = "FileDto.BulkDeleteRequest", description = "원본 기준 파일 일괄 삭제 요청 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkDeleteRequest {

        @Schema(description = "원본구분", example = "요구사항정의서", requiredMode = Schema.RequiredMode.REQUIRED)
        private String orcDtt;

        @Schema(description = "원본PK값", example = "PRJ-2026-0001", requiredMode = Schema.RequiredMode.REQUIRED)
        private String orcPkVl;
    }

    /**
     * 파일 목록 조회 조건 DTO
     */
    @Schema(name = "FileDto.SearchCondition", description = "파일 목록 조회 조건 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchCondition {

        @Schema(description = "원본구분 (필수)", example = "요구사항정의서")
        private String orcDtt;

        @Schema(description = "원본PK값 (선택 - 미입력 시 orcDtt 전체 조회)", example = "PRJ-2026-0001")
        private String orcPkVl;

        @Schema(description = "파일구분 (선택 - '이미지' 또는 '첨부파일')", example = "첨부파일")
        private String flDtt;
    }

    /**
     * 파일 일괄 업로드 결과 DTO
     */
    @Schema(name = "FileDto.BulkUploadResponse", description = "파일 일괄 업로드 결과 DTO")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkUploadResponse {

        @Schema(description = "업로드 성공 파일 목록")
        private List<Response> successList;

        @Schema(description = "업로드 실패 파일명 목록")
        private List<String> failList;
    }
}
