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
     * 알림 1건 적재 + 디스패처 호출.
     *
     * @param event 발송 이벤트
     * @return 적재된 알림 엔티티 (테스트·로깅용)
     */
    @Transactional
    public Cinfmm send(NotificationEvent event) {
        if (event.recipientEno() == null || event.recipientEno().isBlank()) {
            log.warn("Notification skipped: recipientEno is blank. type={}", event.infTpC());
            return null;
        }
        String infMngNo = generateInfMngNo();

        Cinfmm notification = Cinfmm.builder()
            .infMngNo(infMngNo)
            .infTpC(event.infTpC())
            .infTtl(event.infTtl())
            .infCone(event.infCone())
            .infLnkUrl(event.infLnkUrl())
            .rcvUsid(event.recipientEno())
            .rddYn("N")
            .build();
        cinfmmRepository.save(notification);
        dispatcher.dispatch(notification, event.eaiPayload());
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
        return cinfmmRepository.countUnread(currentEno);
    }

    /**
     * 단건 읽음 처리. 소유자 검증 포함.
     *
     * @throws AccessDeniedException    본인 소유 알림이 아닌 경우
     * @throws IllegalArgumentException 알림이 존재하지 않거나 삭제된 경우
     */
    @Transactional
    public void markRead(String infMngNo, String currentEno) {
        Cinfmm notification = loadOwned(infMngNo, currentEno);
        notification.markRead();
    }

    /** 본인 미읽음 알림 일괄 읽음. */
    @Transactional
    public long markAllRead(String currentEno) {
        return cinfmmRepository.markAllReadByRcvUsid(currentEno);
    }

    /** 단건 Soft Delete. 소유자 검증 포함. */
    @Transactional
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
