package com.kdb.it.common.popup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 사용자가 사업 전결권 안내를 조회하는 API입니다. */
@RestController
@RequestMapping("/api/project-approval-authority-notice")
@RequiredArgsConstructor
@Tag(name = "Project Approval Authority Notice", description = "사업 전결권 안내 사용자 API")
public class ApprovalAuthorityNoticeController {

    private final CommonPopupService service;

    /** 등록된 안내가 없으면 본문 없는 204 응답을 반환합니다. */
    @GetMapping
    @Operation(summary = "사업 전결권 안내 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "전결권 안내 반환"),
        @ApiResponse(responseCode = "204", description = "등록된 안내 없음", content = @Content)
    })
    public ResponseEntity<CommonPopupDto.Response> getNotice() {
        return service.getApprovalAuthorityNotice()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
