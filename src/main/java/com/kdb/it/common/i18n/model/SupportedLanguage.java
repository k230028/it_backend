package com.kdb.it.common.i18n.model;

import java.util.Locale;

/** 사이트가 지원하는 언어입니다. */
public enum SupportedLanguage {
    KO("ko"),
    EN("en");

    private final String code;

    SupportedLanguage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 사용자 조회 언어를 정규화하며 미지원 값은 기본 언어인 한국어로 처리합니다. */
    public static SupportedLanguage normalize(String value) {
        if (value == null) {
            return KO;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (SupportedLanguage language : values()) {
            if (language.code.equals(normalized)) {
                return language;
            }
        }
        return KO;
    }

    /** 관리자 입력 언어를 검증합니다. */
    public static SupportedLanguage requireSupported(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (SupportedLanguage language : values()) {
                if (language.code.equals(normalized)) {
                    return language;
                }
            }
        }
        throw new IllegalArgumentException("지원하지 않는 언어입니다: " + value);
    }
}
