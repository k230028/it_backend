package com.kdb.it.domain.budget.document.formguide;

/** 사업 입력 길라잡이의 사업 유형 범위입니다. */
public enum FormGuideScope {
    INFO("info."),
    COST("cost.");

    private final String guideIdPrefix;

    FormGuideScope(String guideIdPrefix) {
        this.guideIdPrefix = guideIdPrefix;
    }

    /**
     * 해당 범위의 고정 길라잡이 ID 접두사를 반환합니다.
     *
     * @return {@code info.} 또는 {@code cost.}
     */
    public String guideIdPrefix() {
        return guideIdPrefix;
    }
}
