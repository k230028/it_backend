package com.kdb.it.common.popup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용 공통 안내 팝업 관리 API입니다. */
@RestController
@RequestMapping("/api/admin/common-popup")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Common Popup", description = "공통 안내 팝업 관리자 API")
public class AdminCommonPopupController {

    private final CommonPopupService service;

    /** 현재 팝업 등록 상태를 조회합니다. */
    @GetMapping
    @Operation(summary = "공통 안내 팝업 관리자 조회")
    public ResponseEntity<CommonPopupDto.AdminResponse> getAdminPopup() {
        return ResponseEntity.ok(service.getAdminPopup());
    }

    /** 팝업 본문을 최초 등록하거나 갱신합니다. */
    @PutMapping
    @Operation(summary = "공통 안내 팝업 저장")
    public ResponseEntity<CommonPopupDto.AdminResponse> save(
            @Valid @RequestBody CommonPopupDto.SaveRequest request) {
        return ResponseEntity.ok(service.save(request.contentHtml()));
    }

    /** 현재 팝업 게시를 중지합니다. */
    @DeleteMapping
    @Operation(
            summary = "공통 안내 팝업 게시 중지",
            responses =
                    @ApiResponse(
                            responseCode = "204",
                            description = "게시 중지 성공",
                            content = @Content))
    public ResponseEntity<Void> stopPublishing() {
        service.stopPublishing();
        return ResponseEntity.noContent().build();
    }
}
