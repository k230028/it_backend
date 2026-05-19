package com.kdb.it.common.notification.event;

import lombok.Builder;

/**
 * 알림 발송 도메인 이벤트
 *
 * <p>
 * 결재·게시판·시스템 등 어디서든 알림이 필요할 때 발행하는 공용 이벤트.
 * {@link org.springframework.context.ApplicationEventPublisher}로 발행하고
 * {@link NotificationEventListener}가 {@code @TransactionalEventListener(AFTER_COMMIT)}로
 * 구독하여 비동기로 적재한다. 발행자 트랜잭션은 차단/롤백되지 않는다.
 * </p>
 *
 * @param recipientEno 수신자 사번 (필수)
 * @param infTpC       알림종류구분코드 — Ccodem cId=CINF_TP
 *                     (APPROVAL_REQUEST / APPROVAL_RESULT / MENTION_POST / MENTION_COMMENT / SYSTEM)
 * @param infTtl       알림 제목 (최대 100자)
 * @param infCone      알림 내용 미리보기 (최대 300자, plain text 권장)
 * @param infLnkUrl    클릭 시 이동할 앱 내부 라우트 (최대 300자)
 * @param eaiPayload   EAI 외부 시스템 발송 페이로드(JSON 문자열). null이면 INAPP만 처리
 */
@Builder
public record NotificationEvent(
    String recipientEno,
    String infTpC,
    String infTtl,
    String infCone,
    String infLnkUrl,
    String eaiPayload
) {
    /** 알림 종류 상수 — 호출자 측 오타 방지용 */
    public static final String TYPE_APPROVAL_REQUEST = "APPROVAL_REQUEST";
    public static final String TYPE_APPROVAL_RESULT  = "APPROVAL_RESULT";
    public static final String TYPE_MENTION_POST     = "MENTION_POST";
    public static final String TYPE_MENTION_COMMENT  = "MENTION_COMMENT";
    public static final String TYPE_SYSTEM           = "SYSTEM";
}
