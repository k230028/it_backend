package com.kdb.it.common.notification.service;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 비즈니스 로직 서비스.
 *
 * <p>조회·읽음·삭제는 모두 호출자 본인({@code currentEno}) 데이터에 한정한다.
 * 본인 외 알림에 대한 호출은 {@link AccessDeniedException}으로 차단된다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final CinfmmRepository cinfmmRepository;

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
     * 캐시하지 않아(unless) 신규 알림 발생 시 즉시 반영되도록 한다. 쓰기 경로(enqueue/markRead/
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
