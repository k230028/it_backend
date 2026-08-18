package com.kdb.it.common.i18n.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 다국어 번역 API 공통 DTO입니다. */
public final class TranslationDto {

    private TranslationDto() {}

    /** 언어·원본 컬럼·번역 문구 한 건입니다. */
    public record Value(String language, String columnName, String text) {}

    /** 원본 한 건과 그 번역 현황입니다. */
    public record TranslationEntry(
            String targetKey,
            Map<String, String> source,
            String label,
            List<TranslationColumnValue> columns,
            boolean translated,
            String lastChangedBy,
            LocalDateTime lastChangedAt) {}

    /**
     * 번역 대상 컬럼 한 개의 한국어 원문과 언어별 번역입니다.
     *
     * <p>{@code maxLength}는 원본 컬럼 길이와 {@code TC_DES}(2000) 중 작은 값이며, 관리자 화면 입력 제한에 씁니다.
     */
    public record TranslationColumnValue(
            String columnName, String koText, int maxLength, Map<String, String> translations) {}
}
