package com.kdb.it.common.approval.itbudget.controller;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.approval.itbudget.service.ItBudgetApprovalFacade;
import com.kdb.it.common.system.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 인증된 사용자의 전산예산 조회용 미리보기 API다. 결재 상태를 변경하지 않아 MFA를 요구하지 않는다. */
@RestController
@RequestMapping("/api/applications/it-budget")
@RequiredArgsConstructor
public class ItBudgetApplicationController {
    private final ItBudgetApprovalFacade facade;

    /** 원장·결재자 입력을 검증해 v2 문서를 반환한다. 인증·권한·대상 누락은 공통 오류 계약으로 응답한다. */
    @PostMapping(value = "/previews", consumes = "application/json", produces = "application/json")
    @Operation(
            summary = "전산예산 결재 미리보기",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "서버 생성 v2 미리보기",
                        content =
                                @Content(schema = @Schema(implementation = PreviewResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "잘못된 미리보기 입력 또는 원장 값",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(responseCode = "401", description = "미인증", content = @Content),
                @ApiResponse(responseCode = "403", description = "권한 없음", content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "원장 개정본 미존재 또는 삭제",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public PreviewResponse preview(
            @AuthenticationPrincipal CustomUserDetails actor,
            @Valid @RequestBody PreviewRequest request) {
        if (actor == null) throw new AccessDeniedException("인증 정보가 없습니다.");
        return facade.preview(actor, request);
    }
}
