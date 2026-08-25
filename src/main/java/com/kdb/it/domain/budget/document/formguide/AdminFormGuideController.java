package com.kdb.it.domain.budget.document.formguide;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용 사업 입력 길라잡이 카탈로그·본문 관리 API입니다. */
@RestController
@RequestMapping("/api/admin/form-guides")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFormGuideController {

    private final FormGuideService formGuideService;

    /**
     * 사업 유형의 전체 고정 카탈로그와 현재 등록 상태를 조회합니다.
     *
     * @param scope {@code info} 또는 {@code cost}
     * @return 전체 카탈로그와 등록된 문서 정보
     * @throws IllegalArgumentException 지원하지 않는 사업 유형일 때
     */
    @GetMapping("/catalog")
    public ResponseEntity<List<FormGuideDto.CatalogResponse>> getCatalog(
            @RequestParam("scope") String scope) {
        return ResponseEntity.ok(formGuideService.getCatalog(parseScope(scope)));
    }

    /**
     * 카탈로그 항목의 HTML 본문을 신규 등록하거나 수정합니다.
     *
     * @param guideId 고정 카탈로그 ID
     * @param request HTML 본문
     * @return 현재 입력 길라잡이 문서관리번호
     */
    @PutMapping("/{guideId}")
    public ResponseEntity<String> save(
            @PathVariable("guideId") String guideId,
            @Valid @RequestBody FormGuideDto.SaveRequest request) {
        return ResponseEntity.ok(formGuideService.save(guideId, request));
    }

    /**
     * 카탈로그 항목에 등록된 활성 길라잡이를 논리 삭제합니다.
     *
     * @param guideId 고정 카탈로그 ID
     * @return 본문 없는 204 응답
     */
    @DeleteMapping("/{guideId}")
    public ResponseEntity<Void> delete(@PathVariable("guideId") String guideId) {
        formGuideService.delete(guideId);
        return ResponseEntity.noContent().build();
    }

    private static FormGuideScope parseScope(String scope) {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("지원하지 않는 사업 유형입니다");
        }
        try {
            return FormGuideScope.valueOf(scope.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 사업 유형입니다");
        }
    }
}
