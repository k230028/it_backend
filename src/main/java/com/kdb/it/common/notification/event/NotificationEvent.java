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
 * @param recipientEno 수신자 사원번호 (필수)
 * @param infmSvcTc    알림서비스구분코드 — 공통코드 {@code C_ID='INFM_SVC'} 2자리 값
 *                     (01=시스템, 02=결재요청, 03=결재결과, 04=게시물멘션, 05=댓글멘션, 06=결재회수)
 * @param ttl          제목 (최대 100자)
 * @param infmMsgCone  알림메시지내용 — 본문 (최대 4000자)
 * @param infmRcdUrl   알림추천URL — 클릭 시 이동할 앱 내부 라우트 (최대 300자)
 * @param sdPayload    발송 페이로드(JSON 문자열). null이면 인앱만 처리
 */
@Builder
public record NotificationEvent(
    String recipientEno,
    String infmSvcTc,
    String ttl,
    String infmMsgCone,
    String infmRcdUrl,
    String sdPayload
) {
    /**
     * 알림 종류 상수 — 호출자 측 오타 방지용.
     *
     * <p>{@code Ccodem.cId='INFM_SVC'} 시드의 CDVA 값과 1:1 매칭. INFM_SVC_TC 컬럼이
     * VARCHAR2(2)이므로 2자리 코드로 유지한다.</p>
     */
    public static final String TYPE_SYSTEM            = "01"; // 시스템 알림
    public static final String TYPE_APPROVAL_REQUEST  = "02"; // 결재요청 알림
    public static final String TYPE_APPROVAL_RESULT   = "03"; // 결재결과 알림
    public static final String TYPE_MENTION_POST      = "04"; // 게시물 멘션 알림
    public static final String TYPE_MENTION_COMMENT   = "05"; // 댓글 멘션 알림
    public static final String TYPE_APPROVAL_RECALLED = "06"; // 결재회수 알림
}
