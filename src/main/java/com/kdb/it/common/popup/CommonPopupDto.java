package com.kdb.it.common.popup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 공통 안내 팝업의 사용자·관리자 API 계약입니다. */
public final class CommonPopupDto {

    private CommonPopupDto() {}

    /** 관리자 본문 저장 요청입니다. */
    @Schema(name = "CommonPopupSaveRequest", description = "공통 안내 팝업 저장 요청")
    public record SaveRequest(
            @NotBlank @Schema(description = "공통 안내 팝업 HTML 본문") String contentHtml) {}

    /** 인증 사용자에게 제공하는 게시 중인 팝업입니다. */
    @Schema(name = "CommonPopupResponse", description = "게시 중인 공통 안내 팝업")
    public record Response(
            @Schema(description = "가이드 문서관리번호") String docMngNo,
            @Schema(description = "정화된 공통 안내 팝업 HTML 본문") String contentHtml,
            @Schema(description = "다시 보지 않기 비교에 사용하는 불투명 콘텐츠 버전") String contentVersion) {}

    /** 관리자 화면에 제공하는 현재 팝업 등록 상태입니다. */
    @Schema(name = "CommonPopupAdminResponse", description = "공통 안내 팝업 관리자 응답")
    public record AdminResponse(
            @Schema(description = "가이드 문서관리번호. 미등록이면 null", nullable = true) String docMngNo,
            @Schema(description = "정화된 HTML 본문. 미등록이면 null", nullable = true) String contentHtml,
            @Schema(description = "콘텐츠 버전. 미등록이면 null", nullable = true) String contentVersion) {}
}
