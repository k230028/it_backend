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
     * 구현체는 발송이 완료되면 {@link Cinfmm#markDispatched(String, String)}을 호출하여
     * EAI 메타를 기록할 책임이 있다. 발송 실패는 예외로 던지지 않고 로깅 후 무시한다
     * (호출자 알림 트랜잭션과 분리된 부수 효과로 취급).
     * </p>
     *
     * @param notification 적재 직후의 알림 엔티티 (영속 상태)
     * @param eaiPayload   외부 발송 페이로드 (JSON 문자열, null 가능)
     */
    void dispatch(Cinfmm notification, String eaiPayload);
}
