package com.kdb.it.domain.budget.document.formguide;

import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 사용자가 사업 유형별 입력 길라잡이를 한 번에 조회하는 API입니다. */
@RestController
@RequestMapping("/api/form-guides")
@RequiredArgsConstructor
public class FormGuideController {

    private final FormGuideService formGuideService;

    /**
     * 등록된 본문이 있는 길라잡이를 사업 유형별로 조회합니다.
     *
     * @param scope {@code info} 또는 {@code cost}
     * @return 표시 가능한 길라잡이 목록
     * @throws IllegalArgumentException 지원하지 않는 사업 유형일 때
     */
    @GetMapping
    public ResponseEntity<List<FormGuideDto.PublicResponse>> getPublished(
            @RequestParam("scope") String scope) {
        return ResponseEntity.ok(formGuideService.getPublished(parseScope(scope)));
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
