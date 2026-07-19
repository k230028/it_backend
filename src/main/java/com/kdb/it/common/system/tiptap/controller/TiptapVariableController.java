package com.kdb.it.common.system.tiptap.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.MetadataResponse;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ResolveRequest;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ResolveResponse;
import com.kdb.it.common.system.tiptap.service.TiptapVariableService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Tiptap 에디터 변수 입력 기능 컨트롤러.
 *
 * <p>인증된 모든 사용자가 호출할 수 있으며, 사업(PROJ) 토큰 권한 검증은 서비스 계층에서 적용합니다.
 * ADMIN은 모든 사업 토큰을 해석할 수 있고, 부서관리자는 본인 부서 사업만 해석할 수 있습니다.</p>
 *
 * 설계 참조: §2.2 백엔드, §4.5 권한 필터링
 */
@RestController
@RequestMapping("/api/tiptap-variables")
@RequiredArgsConstructor
@Tag(name = "TiptapVariable", description = "Tiptap 변수 입력 API")
public class TiptapVariableController {

    private final TiptapVariableService service;

    /**
     * 변수 카탈로그를 조회합니다.
     *
     * <p>Tiptap 변수 드롭다운에 표시할 카테고리·연도·사업·항목 목록을 반환합니다.
     * 사업(PROJ) 카탈로그는 인증 사용자의 부서(bbrC) 기준으로 필터링되며,
     * ADMIN은 전체 사업을, 그 외 사용자는 본인 부서 사업만 조회합니다.</p>
     *
     * @param user 현재 인증 사용자 (권한·부서 기준 필터)
     * @return 카테고리 메타데이터 목록 (200 OK)
     */
    @GetMapping("/metadata")
    @Operation(summary = "변수 카탈로그 조회",
               description = "Tiptap 변수 드롭다운에 표시할 카테고리·연도·사업(부서 필터)·항목 목록을 반환합니다.")
    public ResponseEntity<MetadataResponse> getMetadata(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(service.getMetadata(user));
    }

    /**
     * 토큰 배열을 해석하여 표시값과 상태를 반환합니다.
     *
     * <p>요청 본문은 {@link ResolveRequest}에 의해 1~200개 토큰으로 제한되며,
     * 검증 실패 시 자동으로 400 Bad Request 응답이 반환됩니다.
     * 사업(PROJ) 토큰은 ADMIN 전체 허용, 부서관리자 동일 부서 사업만 허용, 그 외 사용자는 FORBIDDEN입니다.</p>
     *
     * @param request 해석할 토큰 배열 (1~200개)
     * @return 토큰별 해석 결과 (삽입 순서 유지, 200 OK)
     */
    @PostMapping("/resolve")
    @Operation(summary = "변수 토큰 해석",
               description = "토큰 배열을 받아 토큰별 표시값과 상태를 반환합니다. 최대 200개.")
    public ResponseEntity<ResolveResponse> resolve(
            @Valid @RequestBody ResolveRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(service.resolve(request.tokens(), user));
    }
}
