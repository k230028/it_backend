package com.kdb.it.common.notification.dispatcher;

/** 알림 채널 발송 결과입니다. */
public record NotificationDispatchResult(boolean success, String errorMessage) {

    public static NotificationDispatchResult sent() {
        return new NotificationDispatchResult(true, null);
    }

    public static NotificationDispatchResult failure(String message) {
        return new NotificationDispatchResult(false, message);
    }
}
