package com.kdb.it.common.approval.itbudget.controller;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.approval.itbudget.service.ItBudgetApprovalFacade;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.security.MfaRequired;
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

/** 전산예산 미리보기와 상신 API다. 상신만 결재용 MFA를 요구한다. */
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
                        description = "서버 생성 v3 미리보기",
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

    /** 미리보기 결속을 검증하여 모든 문서를 함께 상신한다. 인증·MFA·검증 실패 시 저장하지 않는다. */
    @MfaRequired(purpose = MfaPurpose.APPROVAL)
    @PostMapping(
            value = "/submissions",
            consumes = "application/json",
            produces = "application/json")
    @Operation(
            summary = "전산예산 원자적 결재 상신",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "입력 순서의 신청관리번호",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                SubmissionResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "미리보기 입력 변조 또는 결속 오류",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "미인증 또는 결재용 MFA 필요",
                        content = @Content),
                @ApiResponse(responseCode = "403", description = "권한 없음", content = @Content),
                @ApiResponse(
                        responseCode = "409",
                        description = "원장 변경·미리보기 만료·표시 정보 변경·잠금 경합",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public SubmissionResponse submit(
            @AuthenticationPrincipal CustomUserDetails actor,
            @Valid @RequestBody SubmissionRequest request) {
        if (actor == null) throw new AccessDeniedException("인증 정보가 없습니다.");
        return facade.submit(actor, request);
    }
}
