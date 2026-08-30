package com.kdb.it.domain.userguide.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 사용자가이드 API 요청·응답 DTO 모음 */
public class UserGuideDto {

    private UserGuideDto() {}

    /** 사용자가이드 조회 응답 DTO */
    @Schema(name = "UserGuideDto.Response", description = "사용자가이드 조회 응답 DTO")
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Response {

        @Schema(description = "파일매핑ID", example = "FL-00000001")
        private String flMpnId;

        @Schema(description = "파일명", example = "IT포털 사용자가이드 v1.2.pdf")
        private String flNm;

        @Schema(description = "첨부파일크기(바이트). 레거시 파일은 null", example = "3145728")
        private Long apgFlSz;

        @Schema(description = "현재 가이드 여부 (DEL_YN='N'이면 true)", example = "true")
        private boolean active;

        @Schema(description = "내려받기 URL", example = "/api/files/FL-00000001/download")
        private String downloadUrl;

        @Schema(description = "최초등록일시")
        private LocalDateTime fstEnrDtm;

        @Schema(description = "최초등록자 사번", example = "EMP0001234")
        private String fstEnrUsid;
    }

    /** 사용자가이드 활성 상태 변경 요청 DTO */
    @Schema(name = "UserGuideDto.ActiveRequest", description = "사용자가이드 활성 상태 변경 요청 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveRequest {

        @Schema(
                description = "현재 가이드로 지정할지 여부. true면 DEL_YN='N', false면 'Y'",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "활성 여부(active)는 필수입니다.")
        private Boolean active;
    }
}
