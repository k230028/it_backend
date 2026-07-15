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

/** 대금지급 API. 인증 필요(클래스 ADMIN 전용 아님), 쓰기 주체/상태전이는 서비스에서 검증. */
@RestController
@RequestMapping("/api/project/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "대금지급 API")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 상태·구분·연계번호로 대금지급 문서를 조회합니다.
     *
     * @param status 상태 조건
     * @param prnTc 구분 조건
     * @param cncdRfrNo 연계번호
     * @param user 인증 사용자
     * @return 조회 가능한 문서 목록
     */
    @Operation(summary = "대금지급 목록")
    @GetMapping
    public ResponseEntity<List<PaymentDto.ListItem>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "prnTc", required = false) String prnTc,
            @RequestParam(name = "cncdRfrNo", required = false) String cncdRfrNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(paymentService.list(status, prnTc, cncdRfrNo, user));
    }

    /**
     * 문서번호로 대금지급 상세를 조회합니다.
     *
     * @param docNo 문서번호
     * @return 대금지급 상세
     * @throws IllegalArgumentException 문서가 없는 경우
     */
    @Operation(summary = "대금지급 상세")
    @GetMapping("/{docNo}")
    public ResponseEntity<PaymentDto.Detail> get(@PathVariable(name = "docNo") String docNo) {
        return ResponseEntity.ok(paymentService.get(docNo));
    }

    /**
     * 대금지급 의뢰 문서를 생성합니다.
     *
     * @param req 생성 요청
     * @param user 인증 사용자
     * @return 생성된 문서번호
     * @throws org.springframework.security.access.AccessDeniedException 생성 권한이 없는 경우
     */
    @Operation(summary = "대금지급 신규 의뢰")
    @PostMapping
    public ResponseEntity<String> create(@RequestBody @Valid PaymentDto.CreateRequest req,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(req, user));
    }

    /**
     * 작성 중인 대금지급 문서를 수정합니다.
     *
     * @param docNo 문서번호
     * @param req 수정 요청
     * @param user 인증 사용자
     * @return 빈 성공 응답
     * @throws IllegalStateException 수정 가능한 상태가 아닌 경우
     */
    @Operation(summary = "대금지급 마스터 수정(작성중)")
    @PutMapping("/{docNo}")
    public ResponseEntity<Void> update(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid PaymentDto.UpdateRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        paymentService.update(docNo, req, user); return ResponseEntity.ok().build();
    }

    /**
     * 작성 중인 대금지급 문서를 논리 삭제합니다.
     *
     * @param docNo 문서번호
     * @param user 인증 사용자
     * @return 빈 성공 응답
     * @throws org.springframework.security.access.AccessDeniedException 삭제 권한이 없는 경우
     */
    @Operation(summary = "대금지급 삭제(작성중)")
    @DeleteMapping("/{docNo}")
    public ResponseEntity<Void> delete(@PathVariable(name = "docNo") String docNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        paymentService.delete(docNo, user); return ResponseEntity.noContent().build();
    }

    /**
     * 대금지급 문서 상태를 전이합니다.
     *
     * @param docNo 문서번호
     * @param req 상태 요청
     * @param user 인증 사용자
     * @return 빈 성공 응답
     * @throws IllegalStateException 허용되지 않은 상태 전이인 경우
     */
    @Operation(summary = "대금지급 상태 전이(제출/완료)")
    @PostMapping("/{docNo}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid PaymentDto.StatusRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        paymentService.changeStatus(docNo, req, user); return ResponseEntity.ok().build();
    }

    /**
     * 진행 중 문서의 회차별 지급 명세를 저장합니다.
     *
     * @param docNo 문서번호
     * @param req 지급 명세
     * @param user 인증 사용자
     * @return 빈 성공 응답
     * @throws IllegalStateException 명세를 저장할 수 없는 상태인 경우
     */
    @Operation(summary = "회차별 지급 명세 일괄 저장(진행중)")
    @PutMapping("/{docNo}/payments")
    public ResponseEntity<Void> savePayments(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid PaymentDto.LinesRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        paymentService.savePayments(docNo, req, user); return ResponseEntity.ok().build();
    }
}
