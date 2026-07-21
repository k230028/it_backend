package com.kdb.it.domain.budget.document.controller;

import com.kdb.it.domain.budget.document.dto.ReviewerDto;
import com.kdb.it.domain.budget.document.service.ReviewerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사전협의 검토자 컨트롤러 */
@Tag(name = "사전협의 검토자", description = "사전협의 검토자 관련 API")
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewerController {

    private final ReviewerService reviewerService;

    /**
     * 인증된 사용자에게 사전협의 공통 검토자 후보를 반환합니다.
     *
     * <p>미인증 요청은 보안 필터에서 거부되며, 서비스 또는 저장소 조회 실패는 전역 예외 처리기로 전파됩니다.
     *
     * @return 팀별 전역 검토자 후보 목록을 담은 성공 응답
     */
    @Operation(summary = "검토자 목록 조회", description = "사전협의 공통 검토자 후보를 팀별로 반환합니다.")
    @GetMapping("/reviewers")
    public ResponseEntity<List<ReviewerDto.Response>> getReviewers() {
        return ResponseEntity.ok(reviewerService.getReviewers());
    }

    /**
     * 구 문서별 검토자 경로 호환용 엔드포인트.
     *
     * <p>미인증 요청은 보안 필터에서 거부되며, 서비스 또는 저장소 조회 실패는 전역 예외 처리기로 전파됩니다.
     *
     * @param docMngNo 사용하지 않는 구 사전협의 관리번호
     * @return 팀별 전역 검토자 후보 목록을 담은 성공 응답
     * @deprecated 프론트 전환 확인 후 다음 릴리스에서 제거
     */
    @Deprecated(forRemoval = true)
    @GetMapping("/{docMngNo}/reviewers")
    public ResponseEntity<List<ReviewerDto.Response>> getReviewersLegacy(
            @PathVariable(name = "docMngNo") String docMngNo) {
        log.warn("폐기 예정 검토자 경로가 호출되었습니다 (docMngNo={})", docMngNo);
        return ResponseEntity.ok(reviewerService.getReviewers());
    }
}
