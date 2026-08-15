package com.kdb.it.common.i18n.model;

import java.util.Set;

/** 번역할 수 있는 업무 데이터 구분입니다. */
public enum TranslationTarget {
    MENU("메뉴", Set.of(TranslationColumns.MNU_NM)),
    COMMON_CODE(
            "공통코드",
            Set.of(
                    TranslationColumns.CO_C_NM,
                    TranslationColumns.CDVA_NM,
                    TranslationColumns.CO_CDVA_ABV_NM,
                    TranslationColumns.CO_CDVA_SPS,
                    TranslationColumns.CO_C_INTN_CONE));

    private final String dbName;
    private final Set<String> columns;

    TranslationTarget(String dbName, Set<String> columns) {
        this.dbName = dbName;
        this.columns = columns;
    }

    public String dbName() {
        return dbName;
    }

    public Set<String> columns() {
        return columns;
    }

    /** 해당 구분에서 허용된 원본 컬럼인지 검증합니다. */
    public void validateColumn(String columnName) {
        if (!columns.contains(columnName)) {
            throw new IllegalArgumentException("지원하지 않는 번역 대상 컬럼입니다: " + dbName + "/" + columnName);
        }
    }
}
