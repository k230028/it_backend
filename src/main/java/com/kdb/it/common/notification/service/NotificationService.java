package com.kdb.it.common.notification.service;

import com.kdb.it.common.notification.dispatcher.NotificationDispatcher;
import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 알림 비즈니스 로직 서비스.
 *
 * <p>발송({@link #send(NotificationEvent)})은 채번 → 엔티티 빌드 → 영속화 →
 * 디스패처 호출 순으로 처리하며, 디스패처는 인앱의 경우 발송 메타만 기록한다.</p>
 *
 * <p>조회·읽음·삭제는 모두 호출자 본인({@code currentEno}) 데이터에 한정한다.
 * 본인 외 알림에 대한 호출은 {@link AccessDeniedException}으로 차단된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final CinfmmRepository cinfmmRepository;
    private final NotificationDispatcher dispatcher;

    /**
     * 알림 1건 적재.
     *
     * <p><b>Propagation.REQUIRES_NEW 필수.</b> 본 메서드는
     * {@code @TransactionalEventListener(AFTER_COMMIT)} 콜백에서 주로 호출되며,
     * Spring 7.0 환경에서 AFTER_COMMIT 페이즈는 outer 트랜잭션 종료 후 호출되어
     * {@code Propagation.REQUIRED}만으로는 {@code TransactionRequiredException}이 발생할 수 있다.
     * {@code REQUIRES_NEW}로 명시하면 항상 독립된 새 트랜잭션을 강제 시작하므로 회피 가능.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "notificationUnreadCount", key = "#event.recipientEno()",
            condition = "#event.recipientEno() != null")
    public Cinfmm send(NotificationEvent event) {
        log.debug("[알림] send 진입: recipient={}, svcTc={}", event.recipientEno(), event.infmSvcTc());
        if (event.recipientEno() == null || event.recipientEno().isBlank()) {
            log.warn("[알림] recipientEno 비어있음 → 발행 건너뜀: svcTc={}", event.infmSvcTc());
            return null;
        }
        String infmMsgNo = generateInfmMsgNo();

        // DB 컬럼 길이(제목 100자 / 본문 4000자 / URL 300자)를 초과하면 INSERT가 ORA-12899로 실패하므로
        // 저장 직전에 안전하게 잘라낸다. truncation 발생 시 발행자 측 데이터 점검을 위해 warn 로그를 남긴다.
        Cinfmm notification = Cinfmm.builder()
            .infmMsgNo(infmMsgNo)
            .infmSvcTc(event.infmSvcTc())
            .ttl(clamp("제목", infmMsgNo, event.ttl(), 100))
            .infmMsgCone(clamp("본문", infmMsgNo, event.infmMsgCone(), 4000))
            .infmRcdUrl(clamp("URL", infmMsgNo, event.infmRcdUrl(), 300))
            .rmsEno(event.recipientEno())
            .inqYn("N")
            .sdTc(event.sdTc())
            .build();
        // saveAndFlush로 즉시 INSERT 발행 — 실패 시 즉시 예외(catch에서 명확한 ORA 진단).
        // 일반 save()는 트랜잭션 commit 시점에 flush되는데, @TransactionalEventListener(AFTER_COMMIT)
        // 안의 새 트랜잭션 + 클래스 레벨 readOnly=true 영향으로 flush가 skip되는 케이스가 관찰됨.
        cinfmmRepository.saveAndFlush(notification);
        log.info("[알림] CINFMM saveAndFlush 완료: infmMsgNo={}", infmMsgNo);
        dispatcher.dispatch(notification, event.sdPayload());
        return notification;
    }

    /**
     * 알림 문자열 필드를 DB 컬럼 최대 길이로 안전하게 잘라냅니다.
     *
     * @param fieldLabel 로그용 필드 라벨(제목/본문/URL)
     * @param infmMsgNo  진단용 알림 채번
     * @param value      원본 값(null이면 그대로 null 반환)
     * @param maxLen     허용 최대 길이
     * @return maxLen 이하로 잘린 값(또는 원본/널)
     */
    private String clamp(String fieldLabel, String infmMsgNo, String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        log.warn("[알림] {} 길이 초과 — {}자→{}자로 절단: infmMsgNo={}",
                fieldLabel, value.length(), maxLen, infmMsgNo);
        return value.substring(0, maxLen);
    }

    /**
     * 본인 알림 목록 페이지 조회.
     */
    public Page<Cinfmm> listForCurrentUser(String currentEno, Boolean unreadOnly, Pageable pageable) {
        return cinfmmRepository.findInbox(currentEno, unreadOnly, pageable);
    }

    /**
     * 본인 미읽음 알림 건수 조회.
     *
     * <p>AppHeader 배지에서 고빈도 호출되므로 사용자(currentEno)별로 캐시한다. 카운트가 0이면
     * 캐시하지 않아(unless) 신규 알림 발생 시 즉시 반영되도록 한다. 쓰기 경로(send/markRead/
     * markAllRead/softDelete)에서 해당 사용자 키를 evict 한다. 캐시는 Caffeine 60초 TTL을
     * 가지므로(P5/T13, {@link com.kdb.it.config.CacheConfig} 참조), evict 누락 시에도 stale은
     * 최대 60초로 제한된다(evict-on-write와 TTL 병행).</p>
     */
    @Cacheable(value = "notificationUnreadCount", key = "#p0", unless = "#result == 0")
    public long unreadCount(String currentEno) {
        return cinfmmRepository.countUnread(currentEno);
    }

    /**
     * 단건 읽음 처리. 소유자 검증 포함.
     */
    @Transactional(readOnly = false)
    @CacheEvict(value = "notificationUnreadCount", key = "#p1")
    public void markRead(String infmMsgNo, String currentEno) {
        Cinfmm notification = loadOwned(infmMsgNo, currentEno);
        notification.markRead();
    }

    /**
     * 본인 미읽음 알림 일괄 읽음 처리.
     */
    @Transactional(readOnly = false)
    @CacheEvict(value = "notificationUnreadCount", key = "#p0")
    public long markAllRead(String currentEno) {
        return cinfmmRepository.markAllReadByRmsEno(currentEno);
    }

    /**
     * 단건 알림 Soft Delete. 소유자 검증 포함.
     */
    @Transactional(readOnly = false)
    @CacheEvict(value = "notificationUnreadCount", key = "#p1")
    public void softDelete(String infmMsgNo, String currentEno) {
        Cinfmm notification = loadOwned(infmMsgNo, currentEno);
        notification.delete();
    }

    // ── 내부 헬퍼 ───────────────────────────────────────────────────────────

    /** 알림메시지번호 채번 — {@code INF-{YYYY}-{NEXTVAL:08}} */
    private String generateInfmMsgNo() {
        Long seq = cinfmmRepository.getNextVal();
        return String.format("INF-%d-%08d", LocalDate.now().getYear(), seq);
    }

    /**
     * 알림 조회 및 소유자·삭제여부 검증 헬퍼.
     */
    private Cinfmm loadOwned(String infmMsgNo, String currentEno) {
        Cinfmm notification = cinfmmRepository.findById(infmMsgNo)
            .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다: " + infmMsgNo));
        if ("Y".equals(notification.getDelYn())) {
            throw new IllegalArgumentException("이미 삭제된 알림입니다: " + infmMsgNo);
        }
        if (!notification.getRmsEno().equals(currentEno)) {
            throw new AccessDeniedException("본인 알림이 아닙니다: " + infmMsgNo);
        }
        return notification;
    }
}
