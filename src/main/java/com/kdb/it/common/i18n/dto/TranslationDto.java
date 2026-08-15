package com.kdb.it.common.i18n.dto;

/** 다국어 번역 API 공통 DTO입니다. */
public final class TranslationDto {

    private TranslationDto() {}

    /** 언어·원본 컬럼·번역 문구 한 건입니다. */
    public record Value(String language, String columnName, String text) {}
}
