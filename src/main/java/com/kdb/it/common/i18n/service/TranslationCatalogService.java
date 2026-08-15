package com.kdb.it.common.i18n.service;

import com.kdb.it.common.i18n.dto.TranslationDto;
import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.common.i18n.entity.ClangmId;
import com.kdb.it.common.i18n.model.SupportedLanguage;
import com.kdb.it.common.i18n.model.TranslationTarget;
import com.kdb.it.common.i18n.repository.ClangmRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메뉴와 공통코드 번역을 일괄 조회하고 변경하는 카탈로그 서비스입니다. */
@Service
public class TranslationCatalogService {

    private static final int ORACLE_IN_BATCH_SIZE = 900;
    private static final int MAX_TARGET_KEY_LENGTH = 255;
    private static final int MAX_TEXT_LENGTH = 2000;

    private final ClangmRepository repository;

    public TranslationCatalogService(ClangmRepository repository) {
        this.repository = repository;
    }

    /** 활성 번역을 대상 키와 원본 컬럼 기준의 중첩 맵으로 반환합니다. */
    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> findActive(
            TranslationTarget target, SupportedLanguage language, Collection<String> targetKeys) {
        if (language == SupportedLanguage.KO || targetKeys == null || targetKeys.isEmpty()) {
            return Map.of();
        }

        List<String> uniqueKeys = new ArrayList<>(new LinkedHashSet<>(targetKeys));
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (int from = 0; from < uniqueKeys.size(); from += ORACLE_IN_BATCH_SIZE) {
            List<String> batch =
                    List.copyOf(
                            uniqueKeys.subList(
                                    from,
                                    Math.min(from + ORACLE_IN_BATCH_SIZE, uniqueKeys.size())));
            for (Clangm row :
                    repository.findActiveByTargetAndLanguageAndKeys(
                            target.dbName(), language.code(), batch)) {
                if (row.getTcDes() == null || row.getTcDes().isBlank()) {
                    continue;
                }
                result.computeIfAbsent(row.getTcIdCone(), ignored -> new LinkedHashMap<>())
                        .put(row.getTcColNm(), row.getTcDes());
            }
        }
        return result;
    }

    /** 대상 키의 활성 번역을 관리자 DTO 형식으로 반환합니다. */
    @Transactional(readOnly = true)
    public List<TranslationDto.Value> findAll(TranslationTarget target, String targetKey) {
        validateTargetKey(targetKey);
        return repository.findByDttNmAndTcIdCone(target.dbName(), targetKey).stream()
                .filter(row -> "N".equals(row.getDelYn()))
                .filter(row -> row.getTcDes() != null && !row.getTcDes().isBlank())
                .map(
                        row ->
                                new TranslationDto.Value(
                                        row.getDttLanC(), row.getTcColNm(), row.getTcDes()))
                .toList();
    }

    /** 제출된 번역만 생성·변경·명시 삭제합니다. null과 빈 목록은 기존 번역을 유지합니다. */
    @Transactional
    public void apply(
            TranslationTarget target, String targetKey, List<TranslationDto.Value> values) {
        validateTargetKey(targetKey);
        if (values == null || values.isEmpty()) {
            return;
        }

        validateValues(target, values);
        for (TranslationDto.Value value : values) {
            SupportedLanguage language = SupportedLanguage.requireSupported(value.language());
            ClangmId id = new ClangmId(targetKey, language.code(), value.columnName());
            var existing = repository.findById(id);
            if (value.text() == null || value.text().isBlank()) {
                existing.ifPresent(Clangm::delete);
                continue;
            }
            if (existing.isPresent()) {
                existing.get().update(value.text());
                repository.save(existing.get());
            } else {
                repository.save(
                        Clangm.builder()
                                .tcIdCone(targetKey)
                                .dttLanC(language.code())
                                .tcColNm(value.columnName())
                                .tcDes(value.text())
                                .dttNm(target.dbName())
                                .delYn("N")
                                .build());
            }
        }
    }

    /** 대상 키의 모든 번역을 논리 삭제합니다. */
    @Transactional
    public void softDeleteTarget(TranslationTarget target, String targetKey) {
        validateTargetKey(targetKey);
        repository.findByDttNmAndTcIdCone(target.dbName(), targetKey).forEach(Clangm::delete);
    }

    /** 원본 복합키 변경 시 활성 번역을 새 대상 키로 이전합니다. */
    @Transactional
    public void moveTarget(TranslationTarget target, String oldTargetKey, String newTargetKey) {
        validateTargetKey(oldTargetKey);
        validateTargetKey(newTargetKey);
        if (oldTargetKey.equals(newTargetKey)) {
            return;
        }
        if (repository.existsByDttNmAndTcIdCone(target.dbName(), newTargetKey)) {
            throw new IllegalArgumentException("새 대상 키에 번역이 이미 존재합니다: " + newTargetKey);
        }

        for (Clangm row : repository.findByDttNmAndTcIdCone(target.dbName(), oldTargetKey)) {
            if ("N".equals(row.getDelYn())) {
                repository.save(
                        Clangm.builder()
                                .tcIdCone(newTargetKey)
                                .dttLanC(row.getDttLanC())
                                .tcColNm(row.getTcColNm())
                                .tcDes(row.getTcDes())
                                .dttNm(row.getDttNm())
                                .delYn("N")
                                .build());
            }
            row.delete();
        }
    }

    private static void validateValues(
            TranslationTarget target, List<TranslationDto.Value> values) {
        Set<String> uniqueValues = new LinkedHashSet<>();
        for (TranslationDto.Value value : values) {
            if (value == null) {
                throw new IllegalArgumentException("번역 항목은 null일 수 없습니다.");
            }
            SupportedLanguage language = SupportedLanguage.requireSupported(value.language());
            if (language == SupportedLanguage.KO) {
                throw new IllegalArgumentException("한국어 원문은 번역 테이블에 저장할 수 없습니다.");
            }
            target.validateColumn(value.columnName());
            if (value.text() != null && value.text().length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("번역 문구는 2000자를 넘을 수 없습니다.");
            }
            String uniqueKey = language.code() + "\u0000" + value.columnName();
            if (!uniqueValues.add(uniqueKey)) {
                throw new IllegalArgumentException("같은 언어와 컬럼의 번역이 중복되었습니다.");
            }
        }
    }

    private static void validateTargetKey(String targetKey) {
        if (targetKey == null || targetKey.isBlank()) {
            throw new IllegalArgumentException("번역 대상 키는 공백일 수 없습니다.");
        }
        if (targetKey.length() > MAX_TARGET_KEY_LENGTH) {
            throw new IllegalArgumentException("번역 대상 키는 255자를 넘을 수 없습니다.");
        }
    }
}
