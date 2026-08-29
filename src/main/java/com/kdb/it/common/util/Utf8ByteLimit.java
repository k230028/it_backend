package com.kdb.it.common.util;

import java.nio.charset.StandardCharsets;

/** Oracle BYTE 시맨틱 컬럼에 맞춰 UTF-8 문자열 길이와 안전한 절단을 계산합니다. */
public final class Utf8ByteLimit {

    private Utf8ByteLimit() {
        throw new UnsupportedOperationException("유틸리티 — 인스턴스화 금지");
    }

    /** 문자열의 UTF-8 인코딩 바이트 수를 반환합니다. */
    public static int length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** 코드포인트를 쪼개지 않고 최대 바이트 이하의 앞부분을 반환합니다. */
    public static String truncate(String value, int maxBytes) {
        if (value == null || length(value) <= maxBytes) {
            return value;
        }
        StringBuilder fitted = new StringBuilder();
        int usedBytes = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = length(character);
            if (usedBytes + characterBytes > maxBytes) {
                break;
            }
            fitted.append(character);
            usedBytes += characterBytes;
            offset += Character.charCount(codePoint);
        }
        return fitted.toString();
    }
}
