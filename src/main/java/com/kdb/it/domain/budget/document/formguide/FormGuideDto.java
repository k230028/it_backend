package com.kdb.it.domain.budget.document.formguide;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 사업 입력 길라잡이 공개·관리 API의 요청과 응답 DTO입니다. */
public final class FormGuideDto {

    private FormGuideDto() {}

    /** 사용자가 선택한 입력 필드에 표시할 길라잡이 응답입니다. */
    public record PublicResponse(String guideId, String fieldLabel, String contentHtml) {}

    /** 관리 화면의 고정 카탈로그와 현재 등록 상태를 나타내는 응답입니다. */
    public record CatalogResponse(
            String guideId,
            String section,
            String fieldLabel,
            String controlType,
            String docMngNo,
            String contentHtml) {}

    /** 길라잡이 HTML 본문 저장 요청입니다. */
    @Schema(name = "FormGuideSaveRequest", description = "사업 입력 길라잡이 저장 요청")
    public record SaveRequest(@NotBlank String contentHtml) {}
}
