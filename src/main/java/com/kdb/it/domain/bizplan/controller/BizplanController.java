package com.kdb.it.domain.bizplan.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.bizplan.dto.BizplanDto;
import com.kdb.it.domain.bizplan.service.BizplanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사업계획 API. 인증 필요(클래스 ADMIN 전용 아님), 주관부서/관리자 권한은 서비스에서 검증. */
@RestController
@RequestMapping("/api/project/bizplans")
@RequiredArgsConstructor
@Tag(name = "Bizplan", description = "사업계획 API")
public class BizplanController {

    private final BizplanService bizplanService;

    @Operation(summary = "사업계획 목록 (정보기술부문 계획 포함 사업 기준)")
    @GetMapping
    public ResponseEntity<List<BizplanDto.ListItem>> list(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(bizplanService.list(user));
    }

    @Operation(summary = "사업계획 진입(create-or-get) — 미생성이면 생성 후 상태 21 부여")
    @PostMapping("/{abusMngNo}")
    public ResponseEntity<BizplanDto.Detail> enter(
            @PathVariable(name = "abusMngNo") String abusMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(bizplanService.getOrCreate(abusMngNo, user));
    }

    @Operation(summary = "사업계획 상세 재조회")
    @GetMapping("/{abusMngNo}")
    public ResponseEntity<BizplanDto.Detail> get(
            @PathVariable(name = "abusMngNo") String abusMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(bizplanService.get(abusMngNo, user));
    }

    @Operation(summary = "사업계획 전체 저장 (보고서+일정/품목/계약, 완료 후에도 허용)")
    @PutMapping("/{abusMngNo}")
    public ResponseEntity<Void> save(
            @PathVariable(name = "abusMngNo") String abusMngNo,
            @RequestBody @Valid BizplanDto.SaveRequest req,
            @AuthenticationPrincipal CustomUserDetails user) {
        bizplanService.save(abusMngNo, req, user);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "사업계획 완료 (21→29)")
    @PostMapping("/{abusMngNo}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable(name = "abusMngNo") String abusMngNo,
            @RequestBody @Valid BizplanDto.StatusRequest req,
            @AuthenticationPrincipal CustomUserDetails user) {
        bizplanService.complete(abusMngNo, req, user);
        return ResponseEntity.ok().build();
    }
}
