package com.kdb.it.domain.menu.service;

import java.net.URI;
import java.net.URISyntaxException;

/** 메뉴 화면 경로와 외부 링크의 허용 형식을 판정한다. */
public final class MenuPathPolicy {

    private MenuPathPolicy() {}

    /**
     * 내부 화면 경로인지 판정한다.
     *
     * @param value 판정할 경로
     * @return 단일 슬래시로 시작하고 공백이 없는 내부 경로이면 {@code true}
     */
    public static boolean isInternal(String value) {
        return value != null
                && value.startsWith("/")
                && !value.startsWith("//")
                && value.chars().noneMatch(Character::isWhitespace);
    }

    /**
     * 안전한 HTTP(S) 외부 URL인지 판정한다.
     *
     * @param value 판정할 URL
     * @return 사용자 정보와 공백이 없고 호스트를 가진 HTTP(S) URL이면 {@code true}
     */
    public static boolean isExternalHttpUrl(String value) {
        if (value == null || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException ignored) {
            return false;
        }
    }
}
