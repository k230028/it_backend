package com.kdb.it.common.sso;

import java.util.Optional;

final class SsoNextPathValidator {

    private static final String LOGIN_PATH = "/login";

    private SsoNextPathValidator() {}

    static Optional<String> safePath(String value) {
        if (value == null
                || value.isBlank()
                || !value.startsWith("/")
                || value.startsWith("//")
                || value.startsWith(LOGIN_PATH)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    static String safePathOrRoot(String value) {
        return safePath(value).orElse("/");
    }
}
