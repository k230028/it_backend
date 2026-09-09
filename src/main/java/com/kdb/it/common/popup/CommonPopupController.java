package com.kdb.it.common.popup;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 인증 사용자가 게시 중인 공통 안내 팝업을 조회하는 API입니다. */
@RestController
@RequestMapping("/api/common-popup")
@RequiredArgsConstructor
@Tag(name = "Common Popup", description = "공통 안내 팝업 사용자 API")
public class CommonPopupController {

    private final CommonPopupService service;

    /** 게시 중인 안내가 없으면 본문 없는 204 응답을 반환합니다. */
    @GetMapping
    @Operation(summary = "게시 중인 공통 안내 팝업 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "게시 중인 안내 반환"),
        @ApiResponse(responseCode = "204", description = "게시 중인 안내 없음", content = @Content)
    })
    public ResponseEntity<CommonPopupDto.Response> getActivePopup() {
        return service.getActivePopup()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 지정된 화면 유형의 게시 중인 안내가 없으면 본문 없는 204 응답을 반환합니다. */
    @GetMapping("/{type}")
    @Operation(summary = "게시 중인 화면별 안내 팝업 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "게시 중인 안내 반환"),
        @ApiResponse(responseCode = "204", description = "게시 중인 안내 없음", content = @Content)
    })
    public ResponseEntity<CommonPopupDto.Response> getActivePopup(@PathVariable String type) {
        return service.getActivePopup(requireType(type))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private CommonPopupType requireType(String type) {
        try {
            return CommonPopupType.fromKey(type);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
