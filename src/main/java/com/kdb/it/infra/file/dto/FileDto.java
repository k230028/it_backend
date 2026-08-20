package com.kdb.it.infra.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공통첨부파일기본 DTO 모음
 *
 * <p>TPRMPP_CFILEM 테이블의 CRUD 및 다운로드 API 요청/응답에 사용되는 정적 중첩 클래스 방식 DTO입니다.
 */
public class FileDto {

    /** 파일 업로드 요청 DTO */
    @Schema(name = "FileDto.UploadRequest", description = "파일 업로드 요청 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UploadRequest {

        @Schema(
                description = "파일유형내용 ('이미지' 또는 '첨부파일')",
                example = "첨부파일",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String flTpCone;

        @Schema(description = "주식별자내용 (연결할 도메인 레코드 기본키)", example = "PRJ-2026-0001")
        private String pkCone;

        @Schema(
                description = "주식별자컬럼명 (연결할 도메인 종류)",
                example = "요구사항정의서",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String pkColNm;

        @Schema(description = "첨부파일의 원본 폴더 상대경로", example = "2026/IT부(D01)/01. 사업/근거.pdf")
        private String relativePath;
    }

    /** 파일 메타데이터 수정 요청 DTO */
    @Schema(name = "FileDto.UpdateRequest", description = "파일 메타데이터 수정 요청 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        @Schema(description = "변경할 주식별자내용", example = "PRJ-2026-0002")
        private String pkCone;

        @Schema(
                description = "변경할 주식별자컬럼명",
                example = "정보화사업",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String pkColNm;
    }

    /** 파일 단건·목록 조회 응답 DTO */
    @Schema(name = "FileDto.Response", description = "파일 조회 응답 DTO")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {

        @Schema(description = "파일매핑ID", example = "FL-00000001")
        private String flMpnId;

        @Schema(description = "파일명", example = "요구사항정의서_v1.0.pdf")
        private String flNm;

        @Schema(
                description = "파일물리명",
                example = "SVR1_20260315143022_550e8400e29b41d4a716446655440000.pdf")
        private String flPysNm;

        @Schema(description = "파일저장경로", example = "/data/files/요구사항정의서/2026/03")
        private String flKpnPth;

        @Schema(description = "파일유형내용 ('이미지' 또는 '첨부파일')", example = "첨부파일")
        private String flTpCone;

        @Schema(description = "첨부파일크기(바이트). 레거시 파일은 null", example = "102400")
        private Long apgFlSz;

        @Schema(
                description = "첨부파일의 원본 폴더 상대경로. 기존 파일은 null",
                example = "2026/IT부(D01)/01. 사업/근거.pdf",
                nullable = true)
        private String relativePath;

        @Schema(description = "주식별자내용", example = "PRJ-2026-0001")
        private String pkCone;

        @Schema(description = "주식별자컬럼명", example = "요구사항정의서")
        private String pkColNm;

        @Schema(description = "최초등록일시")
        private LocalDateTime fstEnrDtm;

        @Schema(description = "최초등록자 사번", example = "EMP0001234")
        private String fstEnrUsid;

        /** 이미지 미리보기 URL — flTpCone='이미지'인 경우 Tiptap img src로 사용. */
        @Schema(
                description = "이미지 미리보기 URL (flTpCone='이미지'인 경우 Tiptap img src로 사용)",
                example = "/api/files/FL-00000001/preview")
        private String previewUrl;

        /** 파일 다운로드 URL */
        @Schema(description = "파일 다운로드 URL", example = "/api/files/FL-00000001/download")
        private String downloadUrl;
    }

    /** 원본 기준 일괄 삭제 요청 DTO */
    @Schema(name = "FileDto.BulkDeleteRequest", description = "원본 기준 파일 일괄 삭제 요청 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkDeleteRequest {

        @Schema(
                description = "주식별자컬럼명",
                example = "요구사항정의서",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String pkColNm;

        @Schema(
                description = "주식별자내용",
                example = "PRJ-2026-0001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String pkCone;
    }

    /** 파일 목록 조회 조건 DTO */
    @Schema(name = "FileDto.SearchCondition", description = "파일 목록 조회 조건 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchCondition {

        @Schema(description = "주식별자컬럼명 (필수)", example = "요구사항정의서")
        private String pkColNm;

        @Schema(description = "주식별자내용 (선택 - 미입력 시 pkColNm 전체 조회)", example = "PRJ-2026-0001")
        private String pkCone;

        @Schema(description = "파일유형내용 (선택 - '이미지' 또는 '첨부파일')", example = "첨부파일")
        private String flTpCone;
    }

    /** 파일 일괄 업로드 결과 DTO */
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
