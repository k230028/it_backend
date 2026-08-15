package com.kdb.it.common.i18n.service;

/** 번역 마스터에서 사용하는 대상 키를 생성합니다. */
public final class TranslationTargetKey {

    private static final int MAX_LENGTH = 255;

    private TranslationTargetKey() {}

    /** 메뉴 ID를 번역 대상 키로 사용합니다. */
    public static String menu(String menuId) {
        validatePart(menuId, "메뉴 ID");
        return validateLength(menuId);
    }

    /** 공통코드 복합키의 각 값을 길이-prefix 형식으로 연결합니다. */
    public static String code(String codeId, String codeValueId, String startDate) {
        validatePart(codeId, "공통코드 ID");
        validatePart(codeValueId, "코드값 ID");
        validatePart(startDate, "시작일자");
        return validateLength(withLength(codeId) + withLength(codeValueId) + withLength(startDate));
    }

    private static String withLength(String value) {
        return value.length() + ":" + value;
    }

    private static void validatePart(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 공백일 수 없습니다.");
        }
    }

    private static String validateLength(String value) {
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("번역 대상 키는 255자를 넘을 수 없습니다.");
        }
        return value;
    }
}
