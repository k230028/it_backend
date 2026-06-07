package com.kdb.it.domain.deliberation.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import com.kdb.it.domain.deliberation.service.DeliberationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 과업심의위원회 API. 인증 필요(클래스 ADMIN 전용 아님), 쓰기 주체/상태전이는 서비스에서 검증. */
@RestController
@RequestMapping("/api/project/deliberations")
@RequiredArgsConstructor
@Tag(name = "Deliberation", description = "과업심의위원회 API")
public class DeliberationController {

    private final DeliberationService deliberationService;

    @Operation(summary = "과업심의 목록")
    @GetMapping
    public ResponseEntity<List<DeliberationDto.ListItem>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "prnTc", required = false) String prnTc,
            @RequestParam(name = "cncdRfrNo", required = false) String cncdRfrNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(deliberationService.list(status, prnTc, cncdRfrNo, user));
    }

    @Operation(summary = "과업심의 상세")
    @GetMapping("/{docNo}")
    public ResponseEntity<DeliberationDto.Detail> get(@PathVariable(name = "docNo") String docNo) {
        return ResponseEntity.ok(deliberationService.get(docNo));
    }

    @Operation(summary = "과업심의 신규 신청")
    @PostMapping
    public ResponseEntity<String> create(@RequestBody @Valid DeliberationDto.CreateRequest req,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliberationService.create(req, user));
    }

    @Operation(summary = "과업심의 마스터 수정(작성중)")
    @PutMapping("/{docNo}")
    public ResponseEntity<Void> update(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid DeliberationDto.UpdateRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        deliberationService.update(docNo, req, user); return ResponseEntity.ok().build();
    }

    @Operation(summary = "과업심의 삭제(작성중)")
    @DeleteMapping("/{docNo}")
    public ResponseEntity<Void> delete(@PathVariable(name = "docNo") String docNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        deliberationService.delete(docNo, user); return ResponseEntity.noContent().build();
    }

    @Operation(summary = "과업심의 상태 전이(제출/완료)")
    @PostMapping("/{docNo}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid DeliberationDto.StatusRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        deliberationService.changeStatus(docNo, req, user); return ResponseEntity.ok().build();
    }

    @Operation(summary = "과업심의 결과 입력(진행중)")
    @PutMapping("/{docNo}/result")
    public ResponseEntity<Void> saveResult(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid DeliberationDto.ResultRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        deliberationService.saveResult(docNo, req, user); return ResponseEntity.ok().build();
    }
}
