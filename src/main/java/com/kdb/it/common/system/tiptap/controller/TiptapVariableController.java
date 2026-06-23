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
 * <p>인증된 모든 사용자가 호출 가능하며, 토큰 해석의 PROJ 권한 제한은 서비스 계층에서 적용한다.
 * 메타데이터의 프로젝트 카탈로그 권한 필터링은 후속 과제로 추적한다.</p>
 *
 * Design Ref: §2.2 백엔드, §4.5 권한 필터링
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
     * 현재 프로젝트 카탈로그는 인증 사용자 공통 목록이며, 사용자별 권한 필터링은 후속 과제로 분리되어 있습니다.</p>
     *
     * @return 카테고리 메타데이터 목록 (200 OK)
     */
    @GetMapping("/metadata")
    @Operation(summary = "변수 카탈로그 조회",
               description = "Tiptap 변수 드롭다운에 표시할 카테고리·연도·사업·항목 목록을 반환합니다.")
    public ResponseEntity<MetadataResponse> getMetadata() {
        return ResponseEntity.ok(service.getMetadata());
    }

    /**
     * 토큰 배열을 해석하여 표시값과 상태를 반환합니다.
     *
     * <p>요청 본문은 {@link ResolveRequest}에 의해 1~200개 토큰으로 제한되며,
     * 검증 실패 시 자동으로 400 Bad Request 응답이 반환됩니다.</p>
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
