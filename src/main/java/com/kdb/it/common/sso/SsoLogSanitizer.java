package com.kdb.it.common.sso;

import java.util.regex.Pattern;

final class SsoLogSanitizer {

    private static final Pattern RESULT_CODE_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private SsoLogSanitizer() {}

    static String masked(String value) {
        return value == null || value.isBlank() ? "(없음)" : "***(len=" + value.length() + ")";
    }

    static String exceptionType(Throwable error) {
        return error == null ? "Unknown" : error.getClass().getSimpleName();
    }

    static String resultCode(String value) {
        if (value == null) {
            return "(없음)";
        }
        return RESULT_CODE_PATTERN.matcher(value).matches()
                ? value
                : "<invalid>(len=" + value.codePointCount(0, value.length()) + ")";
    }
}
