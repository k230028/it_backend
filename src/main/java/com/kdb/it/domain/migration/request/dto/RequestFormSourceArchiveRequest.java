package com.kdb.it.domain.migration.request.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 편성요청서 반입 원본 ZIP 다운로드 요청입니다. */
@Schema(name = "RequestFormSourceArchiveRequest", description = "편성요청서 반입 원본 ZIP 다운로드 요청")
public record RequestFormSourceArchiveRequest(
        @Schema(description = "편성요청서 관리번호", example = "APF-2026-0001")
                @NotBlank(message = "편성요청서 관리번호는 필수입니다.")
                String apfMngNo,
        @Schema(description = "선택 파일매핑ID 목록. null이면 접근 가능한 전체 원본을 다운로드합니다.", nullable = true)
                @Size(min = 1, message = "선택 파일은 한 건 이상이어야 합니다.")
                List<@NotBlank(message = "파일매핑ID는 공백일 수 없습니다.") String> fileIds) {}
