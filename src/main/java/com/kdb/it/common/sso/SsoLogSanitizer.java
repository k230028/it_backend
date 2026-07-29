package com.kdb.it.common.sso;

final class SsoLogSanitizer {

    private SsoLogSanitizer() {}

    static String masked(String value) {
        return value == null || value.isBlank() ? "(없음)" : "***(len=" + value.length() + ")";
    }

    static String exceptionType(Throwable error) {
        return error == null ? "Unknown" : error.getClass().getSimpleName();
    }
}
