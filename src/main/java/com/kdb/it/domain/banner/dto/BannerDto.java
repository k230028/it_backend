package com.kdb.it.domain.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** /info 홈 배너 API 요청·응답 DTO 모음 */
public class BannerDto {

    private BannerDto() {}

    /** 배너 조회 응답 DTO */
    @Schema(name = "BannerDto.Response", description = "배너 조회 응답 DTO")
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Response {

        @Schema(description = "파일매핑ID", example = "FL-00000001")
        private String flMpnId;

        @Schema(description = "파일명", example = "hero-2026.png")
        private String flNm;

        @Schema(description = "첨부파일크기(바이트). 레거시 파일은 null", example = "204800")
        private Long apgFlSz;

        @Schema(description = "활성 여부 (DEL_YN='N'이면 true)", example = "true")
        private boolean active;

        @Schema(description = "이미지 미리보기 URL", example = "/api/files/FL-00000001/preview")
        private String previewUrl;

        @Schema(description = "최초등록일시")
        private LocalDateTime fstEnrDtm;

        @Schema(description = "최초등록자 사번", example = "EMP0001234")
        private String fstEnrUsid;
    }

    /** 배너 활성 상태 변경 요청 DTO */
    @Schema(name = "BannerDto.ActiveRequest", description = "배너 활성 상태 변경 요청 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveRequest {

        @Schema(
                description = "활성 여부. true면 DEL_YN='N', false면 DEL_YN='Y'",
                example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "활성 여부(active)는 필수입니다.")
        private Boolean active;
    }
}
