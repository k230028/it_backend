package com.kdb.it.common.util;

/**
 * 날짜 문자열 포맷 정규화 유틸.
 *
 * <p>프론트는 날짜를 "YYYY-MM-DD"(ISO) 형식으로 보내는 경우가 있으나, 메타 표준상 일부 날짜 컬럼은 {@code VARCHAR2(8)}(yyyyMMdd)로
 * 저장한다. 저장 경계에서 하이픈 등 비숫자를 제거해 8자리로 정규화한다.
 */
public final class DateFormatUtil {

    private DateFormatUtil() {}

    /**
     * 날짜 문자열을 {@code yyyyMMdd}(8자리)로 정규화한다.
     *
     * <p>"2026-06-04" → "20260604", "20260604" → "20260604" (불변), null/빈값은 그대로 반환. 숫자만 남긴 뒤 8자를
     * 초과하면 앞 8자만 사용한다.
     *
     * @param value 날짜 문자열(ISO 또는 yyyyMMdd 또는 null/빈값)
     * @return 8자리 yyyyMMdd 문자열(입력이 null/빈값이면 그대로)
     */
    public static String toYmd8(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.length() > 8 ? digits.substring(0, 8) : digits;
    }
}
