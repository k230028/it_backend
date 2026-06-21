package com.kdb.it.domain.budget.document.controller;

import com.kdb.it.domain.budget.document.dto.ReviewerDto;
import com.kdb.it.domain.budget.document.service.ReviewerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사전협의 검토자 컨트롤러
 */
@Tag(name = "사전협의 검토자", description = "사전협의 검토자 관련 API")
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewerController {

    private final ReviewerService reviewerService;

    /**
     * 사전협의 문서의 검토자 목록을 조회합니다.
     *
     * @param docMngNo 사전협의 관리번호
     * @return 검토자 목록
     */
    @Operation(summary = "검토자 목록 조회", description = "사전협의 문서의 검토자 목록을 팀별로 반환합니다.")
    @GetMapping("/{docMngNo}/reviewers")
    public ResponseEntity<List<ReviewerDto.Response>> getReviewers(
            @PathVariable(name = "docMngNo") String docMngNo) {
        return ResponseEntity.ok(reviewerService.getReviewers(docMngNo));
    }
}
