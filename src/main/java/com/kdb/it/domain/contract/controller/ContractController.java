package com.kdb.it.domain.contract.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.contract.dto.ContractDto;
import com.kdb.it.domain.contract.service.ContractService;
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

/** 입찰/계약 REST 컨트롤러. CRUD, 상태 전이, 계약 정보 입력 엔드포인트를 제공합니다. */
@RestController
@RequestMapping("/api/project/contracts")
@RequiredArgsConstructor
@Tag(name = "Contract", description = "입찰/계약 API")
public class ContractController {

    private final ContractService contractService;

    /** 입찰계약 목록 조회. 상태·예산구분·참조번호로 필터링 가능합니다. */
    @Operation(summary = "입찰계약 목록")
    @GetMapping
    public ResponseEntity<List<ContractDto.ListItem>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "prnTc", required = false) String prnTc,
            @RequestParam(name = "cncdRfrNo", required = false) String cncdRfrNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(contractService.list(status, prnTc, cncdRfrNo, user));
    }

    /** 입찰계약 상세 조회. docNo(문서관리번호)로 단건 조회합니다. */
    @Operation(summary = "입찰계약 상세")
    @GetMapping("/{docNo}")
    public ResponseEntity<ContractDto.Detail> get(@PathVariable(name = "docNo") String docNo) {
        return ResponseEntity.ok(contractService.get(docNo));
    }

    /** 입찰계약 신규 의뢰. 생성된 문서관리번호를 반환합니다. */
    @Operation(summary = "입찰계약 신규 의뢰")
    @PostMapping
    public ResponseEntity<String> create(@RequestBody @Valid ContractDto.CreateRequest req,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractService.create(req, user));
    }

    /** 입찰계약 마스터 수정. 작성중 상태에서만 수정 가능합니다. */
    @Operation(summary = "입찰계약 마스터 수정(작성중)")
    @PutMapping("/{docNo}")
    public ResponseEntity<Void> update(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid ContractDto.UpdateRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        contractService.update(docNo, req, user);
        return ResponseEntity.ok().build();
    }

    /** 입찰계약 삭제. 작성중 상태에서만 논리 삭제 가능합니다. */
    @Operation(summary = "입찰계약 삭제(작성중)")
    @DeleteMapping("/{docNo}")
    public ResponseEntity<Void> delete(@PathVariable(name = "docNo") String docNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        contractService.delete(docNo, user);
        return ResponseEntity.noContent().build();
    }

    /** 입찰계약 상태 전이. 제출·완료 등 상태 코드를 전달합니다. */
    @Operation(summary = "입찰계약 상태 전이(제출/완료)")
    @PostMapping("/{docNo}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid ContractDto.StatusRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        contractService.changeStatus(docNo, req, user);
        return ResponseEntity.ok().build();
    }

    /** 계약 정보 입력. 진행중 상태에서 계약 세부 정보를 저장합니다. */
    @Operation(summary = "계약 정보 입력(진행중)")
    @PutMapping("/{docNo}/contract")
    public ResponseEntity<Void> saveContract(@PathVariable(name = "docNo") String docNo,
            @RequestBody @Valid ContractDto.WorkRequest req, @AuthenticationPrincipal CustomUserDetails user) {
        contractService.saveContract(docNo, req, user);
        return ResponseEntity.ok().build();
    }
}
