package com.kdb.it.domain.budget.it.controller;

import com.kdb.it.domain.budget.it.dto.ItBudgetDto;
import com.kdb.it.domain.budget.it.service.ItBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정보기술부문 예산 REST 컨트롤러
 *
 * <p>기본 URL: {@code /api/budget/it}</p>
 *
 * <p>
 * 제공 API:
 * </p>
 * <ul>
 * <li>GET /summary: 비목별 IT/정보보호 구분 편성요청액·편성액 조회</li>
 * <li>GET /comparison: 전년도 대비 비목별 증감 비교</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/budget/it")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ItBudget", description = "정보기술부문 예산 API")
public class ItBudgetController {

    private final ItBudgetService itBudgetService;

    /**
     * 정보기술부문 예산 요약 조회
     *
     * <p>비목별 IT/정보보호 편성요청액·편성액을 천원 단위로 반환합니다.</p>
     *
     * @param bgYy 예산년도 (예: "2026")
     * @return 요약 응답
     */
    @GetMapping("/summary")
    @Operation(summary = "정보기술부문 예산 요약 조회", description = "비목별 IT/정보보호 구분 편성요청액·편성액 (천원)")
    public ResponseEntity<ItBudgetDto.SummaryResponse> getSummary(
            @Parameter(description = "예산년도", example = "2026")
            @RequestParam("bgYy") String bgYy) {
        return ResponseEntity.ok(itBudgetService.getSummary(bgYy));
    }

    /**
     * 정보기술부문 예산 전년 대비 비교
     *
     * <p>금년도 vs 전년도 비목별 편성요청액 증감을 반환합니다.</p>
     *
     * @param bgYy 금년도 예산년도 (예: "2026")
     * @return 비교 응답
     */
    @GetMapping("/comparison")
    @Operation(summary = "정보기술부문 예산 전년 대비 비교", description = "비목별 금년/전년 편성요청액 증감·증감률 (천원)")
    public ResponseEntity<ItBudgetDto.ComparisonResponse> getComparison(
            @Parameter(description = "금년도 예산년도", example = "2026")
            @RequestParam("bgYy") String bgYy) {
        return ResponseEntity.ok(itBudgetService.getComparison(bgYy));
    }
}
