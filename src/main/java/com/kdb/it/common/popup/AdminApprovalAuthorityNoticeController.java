package com.kdb.it.common.popup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용 사업 전결권 안내 관리 API입니다. */
@RestController
@RequestMapping("/api/admin/project-approval-authority-notice")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Project Approval Authority Notice", description = "사업 전결권 안내 관리자 API")
public class AdminApprovalAuthorityNoticeController {

    private final CommonPopupService service;

    /** 현재 전결권 안내 등록 상태를 조회합니다. */
    @GetMapping
    @Operation(summary = "사업 전결권 안내 관리자 조회")
    public ResponseEntity<CommonPopupDto.AdminResponse> getNotice() {
        return ResponseEntity.ok(service.getAdminApprovalAuthorityNotice());
    }

    /** 전결권 안내 본문을 최초 등록하거나 갱신합니다. */
    @PutMapping
    @Operation(summary = "사업 전결권 안내 저장")
    public ResponseEntity<CommonPopupDto.AdminResponse> save(
            @Valid @RequestBody CommonPopupDto.SaveRequest request) {
        return ResponseEntity.ok(service.saveApprovalAuthorityNotice(request.contentHtml()));
    }
}
