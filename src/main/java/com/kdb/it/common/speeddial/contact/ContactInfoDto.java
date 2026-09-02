package com.kdb.it.common.speeddial.contact;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 스피드다이얼 담당자 정보 문서의 전용 API 계약입니다. */
public final class ContactInfoDto {

    private ContactInfoDto() {}

    /** 담당자 정보 본문 저장 요청입니다. */
    @Schema(name = "ContactInfoSaveRequest", description = "담당자 정보 저장 요청")
    public record SaveRequest(
            @NotBlank @Schema(description = "담당자 정보 HTML 본문") String contentHtml) {}

    /** 담당자 정보 조회·저장 응답입니다. */
    @Schema(name = "ContactInfoResponse", description = "담당자 정보 응답")
    public record Response(
            @Schema(description = "가이드 문서관리번호. 아직 작성하지 않았으면 null", nullable = true) String docMngNo,
            @Schema(description = "정화된 담당자 정보 HTML 본문. 아직 작성하지 않았으면 null", nullable = true)
                    String contentHtml) {}
}
