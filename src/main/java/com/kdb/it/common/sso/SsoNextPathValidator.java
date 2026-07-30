package com.kdb.it.common.sso;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class SsoNextPathValidator {

    private static final String LOGIN_SEGMENT = "login";
    private static final String UNRESERVED =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
    private static final Pattern FORBIDDEN_INPUT =
            Pattern.compile(
                    "[\\x00-\\x20\\x7F]|%(?![0-9A-Fa-f]{2})|(?i:%(?:0[0-9a-f]|1[0-9a-f]|7f))");
    private static final Pattern FORBIDDEN_PATH_ENCODING = Pattern.compile("(?i:%(?:20|2f|5c))");
    private static final Pattern SUFFIX_START = Pattern.compile("[?#]");
    private static final Pattern LEADING_SLASHES = Pattern.compile("^/+");

    private SsoNextPathValidator() {}

    static Optional<String> safePath(String value) {
        if (value == null
                || value.isBlank()
                || !value.startsWith("/")
                || value.startsWith("//")
                || value.contains("\\")) {
            return Optional.empty();
        }

        var suffixMatcher = SUFFIX_START.matcher(value);
        int suffixStart = suffixMatcher.find() ? suffixMatcher.start() : value.length();
        String rawPath = value.substring(0, suffixStart);
        if (FORBIDDEN_INPUT.matcher(value).find()
                || FORBIDDEN_PATH_ENCODING.matcher(rawPath).find()) {
            return Optional.empty();
        }

        String canonicalPath = canonicalizePath(rawPath);
        if (hasLoginFirstSegment(canonicalPath)) {
            return Optional.empty();
        }
        return Optional.of(canonicalPath + value.substring(suffixStart));
    }

    static String safePathOrRoot(String value) {
        return safePath(value).orElse("/");
    }

    private static String canonicalizePath(String rawPath) {
        String[] rawSegments = rawPath.split("/", -1);
        List<String> segments = new ArrayList<>();
        boolean trailingSlash = rawPath.endsWith("/");

        for (int index = 1; index < rawSegments.length; index++) {
            String segment = canonicalizeSegment(rawSegments[index]);
            if (segment.equals(".")) {
                trailingSlash = trailingSlash || index == rawSegments.length - 1;
                continue;
            }
            if (segment.equals("..")) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
                trailingSlash = trailingSlash || index == rawSegments.length - 1;
                continue;
            }
            segments.add(segment);
        }

        String canonical =
                LEADING_SLASHES.matcher("/" + String.join("/", segments)).replaceFirst("/");
        if (trailingSlash && !canonical.endsWith("/")) {
            return canonical + "/";
        }
        return canonical;
    }

    private static String canonicalizeSegment(String segment) {
        StringBuilder canonical = new StringBuilder(segment.length());
        for (int index = 0; index < segment.length(); index++) {
            char character = segment.charAt(index);
            if (character != '%') {
                canonical.append(character);
                continue;
            }

            int octet =
                    (Character.digit(segment.charAt(index + 1), 16) << 4)
                            + Character.digit(segment.charAt(index + 2), 16);
            if (isUnreserved(octet)) {
                canonical.append((char) octet);
            } else {
                canonical.append('%');
                canonical.append(Character.toUpperCase(segment.charAt(index + 1)));
                canonical.append(Character.toUpperCase(segment.charAt(index + 2)));
            }
            index += 2;
        }
        return canonical.toString();
    }

    private static boolean isUnreserved(int octet) {
        return UNRESERVED.indexOf(octet) >= 0;
    }

    private static boolean hasLoginFirstSegment(String path) {
        int nextSlash = path.indexOf('/', 1);
        String firstSegment = nextSlash < 0 ? path.substring(1) : path.substring(1, nextSlash);
        return LOGIN_SEGMENT.equalsIgnoreCase(firstSegment);
    }
}
