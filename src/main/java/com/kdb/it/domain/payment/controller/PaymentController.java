package com.kdb.it.domain.payment.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.payment.dto.PaymentDto;
import com.kdb.it.domain.payment.service.PaymentService;
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

@RestController
@RequestMapping("/api/project/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "대금지급 API")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "대금지급 목록")
    @GetMapping
    public ResponseEntity<List<PaymentDto.ListItem>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "prnTc", required = false) String prnTc,
            @RequestParam(name = "cncdRfrNo", required = false) String cncdRfrNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(paymentService.list(status, prnTc, cncdRfrNo, user));
    }

    @Operation(summary = "대금지급 상세")
    @GetMapping("/{docNo}")
    public ResponseEntity<PaymentDto.Detail> get(@PathVariable(name = "docNo") String docNo) {
        return ResponseEntity.ok(paymentService.get(docNo));
    }

    @Operation(summary = "대금지급 신규 의뢰")
    @PostMapping
    public ResponseEntity<String> create(@RequestBody @Valid PaymentDto.CreateRequest req,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(req, user));
    }

    @Operation(summary = "대금지급 마스터 수정(작성중)")
    @PutMapping("/{docNo}")
    public ResponseEntity<Void> update(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid PaymentDto.UpdateRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        paymentService.update(docNo, req, user); return ResponseEntity.ok().build();
    }

    @Operation(summary = "대금지급 삭제(작성중)")
    @DeleteMapping("/{docNo}")
    public ResponseEntity<Void> delete(@PathVariable(name = "docNo") String docNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        paymentService.delete(docNo, user); return ResponseEntity.noContent().build();
    }

    @Operation(summary = "대금지급 상태 전이(제출/완료)")
    @PostMapping("/{docNo}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid PaymentDto.StatusRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        paymentService.changeStatus(docNo, req, user); return ResponseEntity.ok().build();
    }

    @Operation(summary = "회차별 지급 명세 일괄 저장(진행중)")
    @PutMapping("/{docNo}/payments")
    public ResponseEntity<Void> savePayments(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid PaymentDto.LinesRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        paymentService.savePayments(docNo, req, user); return ResponseEntity.ok().build();
    }
}
