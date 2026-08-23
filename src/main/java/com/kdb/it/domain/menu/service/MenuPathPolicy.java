package com.kdb.it.domain.menu.service;

import java.net.URI;
import java.net.URISyntaxException;

/** 메뉴 화면 경로와 외부 링크의 허용 형식을 판정한다. */
public final class MenuPathPolicy {

    private MenuPathPolicy() {}

    /** 공용 준비중 화면 경로 접두. `/preparing/{slug}`는 `preparing/[[slug]].vue` 하나가 처리한다. */
    public static final String PREPARING_PATH_PREFIX = "/preparing/";

    /**
     * 준비중 경로를 자동 등록할 때 카탈로그 비고(RMK)에 남기는 표시.
     *
     * <p>사람이 적어 넣은 안내 문구(예: "2027년 1월 오픈 예정")와 구분하는 유일한 단서다. 준비중 화면은 비고를 사용자에게 그대로 보여주므로, 이 값과 같은
     * 비고는 안내 문구가 아니라 시스템 표시로 보고 화면에 내보내지 않는다.
     */
    public static final String PREPARING_ROUTE_RMK = "준비중 메뉴 자동 등록";

    /**
     * 준비중 화면 경로인지 판정한다.
     *
     * @param value 판정할 경로
     * @return `/preparing/`로 시작하면 {@code true}. null과 slug 없는 `/preparing`은 {@code false}
     */
    public static boolean isPreparing(String value) {
        return value != null && value.startsWith(PREPARING_PATH_PREFIX);
    }

    /**
     * 내부 화면 경로인지 판정한다.
     *
     * @param value 판정할 경로
     * @return 단일 슬래시로 시작하고 공백과 역슬래시가 없는 내부 경로이면 {@code true}
     */
    public static boolean isInternal(String value) {
        return value != null
                && value.startsWith("/")
                && !value.startsWith("//")
                && !value.contains("\\")
                && !containsSpace(value);
    }

    /**
     * 안전한 HTTP(S) 외부 URL인지 판정한다.
     *
     * @param value 판정할 URL
     * @return 사용자 정보와 공백이 없고 호스트와 유효한 포트를 가진 HTTP(S) URL이면 {@code true}
     */
    public static boolean isExternalHttpUrl(String value) {
        if (value == null || containsSpace(value)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            int port = uri.getPort();
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null
                    && (port == -1 || (port >= 0 && port <= 65535));
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private static boolean containsSpace(String value) {
        return value.chars()
                .anyMatch(
                        character ->
                                Character.isWhitespace(character)
                                        || Character.isSpaceChar(character)
                                        || character == '\uFEFF');
    }
}
