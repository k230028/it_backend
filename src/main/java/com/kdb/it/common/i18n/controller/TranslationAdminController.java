package com.kdb.it.common.i18n.controller;

import com.kdb.it.common.i18n.dto.TranslationDto;
import com.kdb.it.common.i18n.model.TranslationTarget;
import com.kdb.it.common.i18n.service.TranslationCatalogService;
import com.kdb.it.common.i18n.service.TranslationEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 메뉴와 공통코드 번역을 관리하는 공용 관리자 API입니다. */
@RestController
@RequestMapping("/api/admin/translations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "번역 관리", description = "메뉴·공통코드 번역 조회와 저장")
public class TranslationAdminController {

    private final TranslationCatalogService translationCatalogService;
    private final TranslationEntryService translationEntryService;

    /** 대상 키에 등록된 활성 번역을 조회합니다. */
    @GetMapping("/{target}")
    @Operation(summary = "대상별 번역 조회")
    public ResponseEntity<List<TranslationDto.Value>> getTranslations(
            @PathVariable(name = "target") String target,
            @RequestParam(name = "targetKey") String targetKey) {
        return ResponseEntity.ok(translationCatalogService.findAll(parseTarget(target), targetKey));
    }

    /** 대상 구분의 원본 전체와 등록된 번역을 병합해 반환합니다. */
    @GetMapping("/{target}/entries")
    @Operation(summary = "대상별 번역 현황 목록")
    public ResponseEntity<List<TranslationDto.Entry>> getEntries(
            @PathVariable(name = "target") String target) {
        return ResponseEntity.ok(translationEntryService.findEntries(parseTarget(target)));
    }

    /** 제출된 컬럼의 번역을 생성·수정하거나 빈 문구인 항목을 논리 삭제합니다. */
    @PutMapping("/{target}")
    @Operation(summary = "대상별 번역 저장")
    public ResponseEntity<Void> updateTranslations(
            @PathVariable(name = "target") String target,
            @RequestParam(name = "targetKey") String targetKey,
            @RequestBody UpdateRequest request) {
        translationCatalogService.apply(parseTarget(target), targetKey, request.translations());
        return ResponseEntity.noContent().build();
    }

    private static TranslationTarget parseTarget(String target) {
        return switch (target == null ? "" : target.trim().toLowerCase()) {
            case "menu" -> TranslationTarget.MENU;
            case "common-code" -> TranslationTarget.COMMON_CODE;
            default -> throw new IllegalArgumentException("지원하지 않는 번역 대상입니다: " + target);
        };
    }

    /** 저장할 번역 항목 목록입니다. */
    public record UpdateRequest(List<TranslationDto.Value> translations) {}
}
