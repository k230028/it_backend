package com.kdb.it.infra.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 게시판 첨부파일 ZIP 다운로드 요청입니다. */
@Schema(name = "BoardAttachmentArchiveRequest", description = "게시판 첨부파일 ZIP 다운로드 요청")
public record BoardAttachmentArchiveRequest(
        @Schema(description = "게시물 관리번호", example = "NAC-2026-0003")
                @NotBlank(message = "게시물 관리번호는 필수입니다.")
                String nacMngNo,
        @Schema(description = "선택 파일매핑ID 목록. null이면 전체 첨부파일을 다운로드합니다.", nullable = true)
                @Size(min = 1, message = "선택 파일은 한 건 이상이어야 합니다.")
                List<@NotBlank(message = "파일매핑ID는 공백일 수 없습니다.") String> fileIds) {}
