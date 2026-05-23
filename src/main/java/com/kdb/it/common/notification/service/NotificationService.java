package com.kdb.it.common.notification.service;

import com.kdb.it.common.notification.dispatcher.NotificationDispatcher;
import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 디스패처 호출 순으로 처리하며, 디스패처는 INAPP의 경우 EAI 메타만 기록한다.</p>
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
     * {@code @TransactionalEventListener(AFTER_COMMIT)} 콜백에서 주로 호출되는데,
     * Spring 7.0의 {@code TransactionalApplicationListenerSynchronization}은
     * AFTER_COMMIT 페이즈를 {@code afterCompletion} synchronization에서 처리한다.
     * 이 컨텍스트에서는 outer 트랜잭션이 이미 종료된 상태이며, 같은 thread에서
     * {@code Propagation.REQUIRED}로 새 트랜잭션을 시작하는 것이 advisor 단계에서만
     * 호출되고 실제 트랜잭션 매니저는 join하지 않아 flush 시점에
     * {@code TransactionRequiredException: No active transaction}이 발생한다.</p>
     *
     * <p>{@code REQUIRES_NEW}로 명시하면 컨텍스트와 무관하게 항상 독립된 새 트랜잭션을
     * 강제 시작하므로 이 race를 회피할 수 있다.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cinfmm send(NotificationEvent event) {
        log.info("[알림 진단] NotificationService.send 진입: recipient={}, type={}",
            event.recipientEno(), event.infTpC());
        if (event.recipientEno() == null || event.recipientEno().isBlank()) {
            log.warn("[알림 진단] recipientEno 비어있음 → 발행 건너뜀: type={}", event.infTpC());
            return null;
        }
        String infMngNo = generateInfMngNo();
        log.info("[알림 진단] 채번: infMngNo={}", infMngNo);

        Cinfmm notification = Cinfmm.builder()
            .infMngNo(infMngNo)
            .infTpC(event.infTpC())
            .infTtl(event.infTtl())
            .infCone(event.infCone())
            .infLnkUrl(event.infLnkUrl())
            .rcvUsid(event.recipientEno())
            .rddYn("N")
            .build();
        // saveAndFlush로 즉시 INSERT 발행 — 실패 시 즉시 예외(catch에서 명확한 ORA 진단)
        // 일반 save()는 트랜잭션 commit 시점에 flush되는데, @TransactionalEventListener(AFTER_COMMIT)
        // 안의 새 트랜잭션 + 클래스 레벨 readOnly=true 영향으로 flush가 skip되는 케이스가 관찰됨.
        cinfmmRepository.saveAndFlush(notification);
        log.info("[알림 진단] CINFMM saveAndFlush 완료(INSERT 실행됨): infMngNo={}", infMngNo);
        dispatcher.dispatch(notification, event.eaiPayload());
        log.info("[알림 진단] dispatch 완료: infMngNo={}", infMngNo);
        return notification;
    }

    /**
     * 본인 알림 목록 페이지 조회.
     *
     * @param currentEno 현재 인증 사용자 사번
     * @param unreadOnly true=미읽음만
     * @param pageable   페이지 정보
     * @return 알림 페이지 (FST_ENR_DTM DESC)
     */
    public Page<Cinfmm> listForCurrentUser(String currentEno, Boolean unreadOnly, Pageable pageable) {
        return cinfmmRepository.findInbox(currentEno, unreadOnly, pageable);
    }

    /** 본인 미읽음 카운트 조회. */
    public long unreadCount(String currentEno) {
        long count = cinfmmRepository.countUnread(currentEno);
        log.info("[알림 진단] unreadCount 조회: currentEno={}, count={}", currentEno, count);
        return count;
    }

    /**
     * 단건 읽음 처리. 소유자 검증 포함.
     *
     * @throws AccessDeniedException    본인 소유 알림이 아닌 경우
     * @throws IllegalArgumentException 알림이 존재하지 않거나 삭제된 경우
     */
    @Transactional(readOnly = false)
    public void markRead(String infMngNo, String currentEno) {
        Cinfmm notification = loadOwned(infMngNo, currentEno);
        notification.markRead();
    }

    /** 본인 미읽음 알림 일괄 읽음. */
    @Transactional(readOnly = false)
    public long markAllRead(String currentEno) {
        return cinfmmRepository.markAllReadByRcvUsid(currentEno);
    }

    /** 단건 Soft Delete. 소유자 검증 포함. */
    @Transactional(readOnly = false)
    public void softDelete(String infMngNo, String currentEno) {
        Cinfmm notification = loadOwned(infMngNo, currentEno);
        notification.delete();
    }

    // ── 내부 헬퍼 ───────────────────────────────────────────────────────────

    /** 알림관리번호 채번 — {@code INF-{YYYY}-{NEXTVAL:08}} */
    private String generateInfMngNo() {
        Long seq = cinfmmRepository.getNextVal();
        return String.format("INF-%d-%08d", LocalDate.now().getYear(), seq);
    }

    /** 알림 조회 + 소유자/삭제여부 검증 */
    private Cinfmm loadOwned(String infMngNo, String currentEno) {
        Cinfmm notification = cinfmmRepository.findById(infMngNo)
            .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다: " + infMngNo));
        if ("Y".equals(notification.getDelYn())) {
            throw new IllegalArgumentException("이미 삭제된 알림입니다: " + infMngNo);
        }
        if (!notification.getRcvUsid().equals(currentEno)) {
            throw new AccessDeniedException("본인 알림이 아닙니다: " + infMngNo);
        }
        return notification;
    }
}
