package com.kdb.it.common.code.controller;

import com.kdb.it.common.code.dto.CodeDto;
import com.kdb.it.common.code.service.CodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * 공통코드(Ccodem) REST 컨트롤러
 */
@RestController
@RequestMapping("/api/ccodem")
@RequiredArgsConstructor
@Tag(name = "Code", description = "공통코드(Ccodem) API")
public class CodeController {

    private final CodeService codeService;

    /**
     * 코드ID 기준 카테고리 다건 조회
     *
     * @param cId        코드ID (예: PRJ_TP, CUR)
     * @param targetDate 기준일자 (선택)
     */
    @GetMapping("/{cId}")
    @Operation(summary = "코드ID 기준 공통코드 목록 조회")
    public ResponseEntity<List<CodeDto.Response>> getCcodemsByCId(
            @PathVariable("cId") String cId,
            @Parameter(description = "기준일자 (yyyy-MM-dd)")
            @RequestParam(value = "targetDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        return ResponseEntity.ok(codeService.getCcodemsByCId(cId, targetDate));
    }

    /**
     * 코드ID + 코드값 기준 단건 조회
     *
     * @param cId        코드ID
     * @param cdva       코드값
     * @param targetDate 기준일자 (선택)
     */
    @GetMapping("/{cId}/{cdva}")
    @Operation(summary = "코드ID+코드값 기준 공통코드 단건 조회")
    public ResponseEntity<CodeDto.Response> getCcodem(
            @PathVariable("cId") String cId,
            @PathVariable("cdva") String cdva,
            @Parameter(description = "기준일자 (yyyy-MM-dd)")
            @RequestParam(value = "targetDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        return ResponseEntity.ok(codeService.getCcodem(cId, cdva, targetDate));
    }

    /**
     * 공통코드 신규 생성
     */
    @PostMapping
    @Operation(summary = "공통코드 신규 생성")
    public ResponseEntity<String> createCcodem(@RequestBody CodeDto.CreateRequest request) {
        String created = codeService.createCcodem(request);
        return ResponseEntity.created(URI.create("/api/ccodem/" + created)).body(created);
    }

    /**
     * 공통코드 수정
     *
     * @param cId   코드ID
     * @param cdva  코드값
     * @param sttDt 시작일자 (복합PK)
     */
    @PutMapping("/{cId}/{cdva}")
    @Operation(summary = "공통코드 수정")
    public ResponseEntity<String> updateCcodem(
            @PathVariable("cId") String cId,
            @PathVariable("cdva") String cdva,
            @Parameter(description = "시작일자 (yyyy-MM-dd)", required = true)
            @RequestParam("sttDt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sttDt,
            @RequestBody CodeDto.UpdateRequest request) {

        return ResponseEntity.ok(codeService.updateCcodem(cId, cdva, sttDt, request));
    }

    /**
     * 공통코드 논리적 삭제
     *
     * @param cId   코드ID
     * @param cdva  코드값
     * @param sttDt 시작일자 (복합PK)
     */
    @DeleteMapping("/{cId}/{cdva}")
    @Operation(summary = "공통코드 삭제 (논리적 삭제)")
    public ResponseEntity<Void> deleteCcodem(
            @PathVariable("cId") String cId,
            @PathVariable("cdva") String cdva,
            @Parameter(description = "시작일자 (yyyy-MM-dd)", required = true)
            @RequestParam("sttDt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sttDt) {

        codeService.deleteCcodem(cId, cdva, sttDt);
        return ResponseEntity.noContent().build();
    }

    /**
     * 코드타입 기준 공통코드 목록 조회
     * 소요자원 구분 CascadeSelect에서 IOE 비목 코드 조회에 사용합니다.
     *
     * @param cTp        코드타입 (예: IOE_LEAFE, IOE_XPN, IOE_SEVS, IOE_IDR, IOE_CPIT)
     * @param targetDate 기준일자 (선택)
     */
    @GetMapping("/type/{cTp}")
    @Operation(summary = "코드타입 기준 공통코드 목록 조회")
    public ResponseEntity<List<CodeDto.Response>> getCcodemsByCTp(
            @PathVariable("cTp") String cTp,
            @Parameter(description = "기준일자 (yyyy-MM-dd)")
            @RequestParam(value = "targetDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        return ResponseEntity.ok(codeService.getCcodemsByCTp(cTp, targetDate));
    }

    /**
     * 예산 신청 기간 조회
     */
    @GetMapping("/budget-period")
    @Operation(summary = "예산 신청 기간 조회")
    public ResponseEntity<CodeDto.BudgetPeriodResponse> getBudgetPeriod() {
        return ResponseEntity.ok(codeService.getBudgetPeriod());
    }
}
