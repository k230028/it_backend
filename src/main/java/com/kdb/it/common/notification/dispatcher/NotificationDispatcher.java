package com.kdb.it.common.notification.dispatcher;

import com.kdb.it.common.notification.entity.Cinfmm;

/**
 * 알림 외부 발송 전략 SPI
 *
 * <p>
 * 본 페이즈에서는 인앱(INAPP) 적재만 즉시 처리한다.
 * 이메일·SMS·알림톡 등 외부 채널 어댑터는 본 인터페이스를 구현하여
 * Phase 2에서 추가한다.
 * </p>
 */
public interface NotificationDispatcher {

    /**
     * 알림을 외부 시스템으로 발송한다(또는 발송 페이로드를 기록한다).
     *
     * <p>
     * 구현체는 채널 발송만 담당하고 엔티티 상태는 변경하지 않는다.
     * 예상 가능한 발송 실패는 예외 대신 실패 결과로 반환한다.
     * </p>
     *
     * @param notification 적재 직후의 알림 엔티티 (영속 상태)
     * @param sdPayload    발송 페이로드 (JSON 문자열, null 가능)
     */
    NotificationDispatchResult dispatch(Cinfmm notification, String sdPayload);
}
