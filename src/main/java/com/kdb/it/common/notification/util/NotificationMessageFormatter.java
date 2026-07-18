package com.kdb.it.common.notification.util;

/**
 * 알림 제목과 본문을 DB 컬럼 길이에 맞게 정규화하는 포맷터.
 *
 * <p>알림 발행 서비스들이 동일한 말줄임 규칙을 사용하도록 모은 작은 유틸리티입니다.</p>
 */
public final class NotificationMessageFormatter {

    private NotificationMessageFormatter() {
    }

    /**
     * 문자열을 최대 길이에 맞게 말줄임 처리합니다.
     *
     * @param value     대상 문자열
     * @param maxLength 최대 허용 길이
     * @return null·공백은 빈 문자열, 초과 문자열은 말줄임표 포함 최대 길이 문자열
     */
    public static String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 1) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}
