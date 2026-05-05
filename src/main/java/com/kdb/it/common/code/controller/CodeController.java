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
 *
 * <p>
 * 공통코드 조회, 생성, 수정, 삭제 API를 제공합니다.
 * </p>
 */
@RestController
@RequestMapping("/api/ccodem")
@RequiredArgsConstructor
@Tag(name = "Code", description = "공통코드(Ccodem) API")
public class CodeController {

    private final CodeService codeService;

    /**
     * 공통코드 단건 조회 (코드ID 기준)
     *
     * @param cdId       조회할 코드ID
     * @param targetDate 기준일자 (선택, yyyy-MM-dd, 미지정 시 시스템 현재 일자 기준)
     * @return HTTP 200 + 조회된 공통코드 정보
     */
    @GetMapping("/{cdId}")
    @Operation(summary = "코드ID 기준 공통코드 단건 조회", description = "특정 코드ID를 가진 공통코드를 조회합니다. 기준일자가 시작일자와 종료일자 사이인 유효한 코드만 조회합니다.")
    public ResponseEntity<CodeDto.Response> getCcodemById(
            @PathVariable("cdId") String cdId,
            @Parameter(description = "기준일자 (yyyy-MM-dd)", required = false) @RequestParam(value = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        CodeDto.Response response = codeService.getCcodemById(cdId, targetDate);
        return ResponseEntity.ok(response);
    }

    /**
     * 공통코드 다건 조회 (코드값구분 기준)
     *
     * @param cttTp      조회할 코드값구분
     * @param targetDate 기준일자 (선택, yyyy-MM-dd, 미지정 시 시스템 현재 일자 기준)
     * @return HTTP 200 + 조회된 공통코드 목록
     */
    @GetMapping("/type/{cttTp}")
    @Operation(summary = "코드값구분 기준 공통코드 목록 조회", description = "특정 코드값구분(CTT_TP)에 속하는 공통코드 목록을 조회합니다.")
    public ResponseEntity<List<CodeDto.Response>> getCcodemByCttTp(
            @PathVariable("cttTp") String cttTp,
            @Parameter(description = "기준일자 (yyyy-MM-dd)", required = false) @RequestParam(value = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        List<CodeDto.Response> responses = codeService.getCcodemByCttTp(cttTp, targetDate);
        return ResponseEntity.ok(responses);
    }

    /**
     * 공통코드 신규 생성
     *
     * @param request 생성 요청 데이터
     * @return HTTP 201 Created + 생성된 코드ID
     */
    @PostMapping
    @Operation(summary = "공통코드 신규 생성", description = "새로운 공통코드를 등록합니다.")
    public ResponseEntity<String> createCcodem(@RequestBody CodeDto.CreateRequest request) {
        String createdCdId = codeService.createCcodem(request);
        return ResponseEntity.created(URI.create("/api/ccodem/" + createdCdId)).body(createdCdId);
    }

    /**
     * 공통코드 수정
     *
     * @param cdId    수정할 코드ID
     * @param sttDt   수정할 시작일자
     * @param request 수정 요청 데이터
     * @return HTTP 200 + 수정된 코드ID
     */
    @PutMapping("/{cdId}")
    @Operation(summary = "공통코드 수정", description = "기존 공통코드 정보를 수정합니다.")
    public ResponseEntity<String> updateCcodem(
            @PathVariable("cdId") String cdId,
            @Parameter(description = "시작일자 (yyyy-MM-dd)", required = true) @RequestParam("sttDt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sttDt,
            @RequestBody CodeDto.UpdateRequest request) {

        String updatedCdId = codeService.updateCcodem(cdId, sttDt, request);
        return ResponseEntity.ok(updatedCdId);
    }

    /**
     * 공통코드 삭제 (논리적 삭제)
     *
     * @param cdId  삭제할 코드ID
     * @param sttDt 삭제할 시작일자
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{cdId}")
    @Operation(summary = "공통코드 삭제 (논리적 삭제)", description = "공통코드를 삭제 상태(DEL_YN='Y')로 변경합니다.")
    public ResponseEntity<Void> deleteCcodem(
            @PathVariable("cdId") String cdId,
            @Parameter(description = "시작일자 (yyyy-MM-dd)", required = true) @RequestParam("sttDt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sttDt) {
        codeService.deleteCcodem(cdId, sttDt);
        return ResponseEntity.noContent().build();
    }

    /**
     * 예산 신청 기간 조회
     *
     * <p>
     * 공통코드 BG-RQS-STA(시작일자)와 BG-RQS-END(종료일자)를 조회하여
     * 예산 신청 가능 기간을 반환합니다.
     * </p>
     *
     * @return HTTP 200 + 시작일자/종료일자
     */
    @GetMapping("/budget-period")
    @Operation(summary = "예산 신청 기간 조회", description = "공통코드(BG-RQS-STA/END)에서 예산 신청 가능 기간을 조회합니다.")
    public ResponseEntity<CodeDto.BudgetPeriodResponse> getBudgetPeriod() {
        CodeDto.BudgetPeriodResponse response = codeService.getBudgetPeriod();
        return ResponseEntity.ok(response);
    }
}
