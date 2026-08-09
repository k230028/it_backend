package com.kdb.it.common.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.notification.dto.NotificationDto;
import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import com.kdb.it.common.notification.repository.NotificationInboxRow;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

/**
 * NotificationService 단위 테스트
 *
 * <p>알림 발송, 수신자별 조회, 읽음 처리와 논리 삭제의 주요 분기를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private CinfmmRepository cinfmmRepository;

    @InjectMocks private NotificationService notificationService;

    @Test
    @DisplayName("listForCurrentUser: 조회 조건과 페이지 정보를 리포지토리에 전달한다")
    void listForCurrentUser_조건전달() {
        PageRequest pageable = PageRequest.of(1, 5);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 9, 9, 10);
        Page<NotificationInboxRow> page =
                new PageImpl<>(
                        java.util.List.of(
                                new NotificationInboxRow(
                                        "INF-1", "01", "제목", "내용", "/route", "N", null,
                                        createdAt)));
        given(cinfmmRepository.findInboxRows("10001", true, pageable)).willReturn(page);

        Page<NotificationDto.Item> result =
                notificationService.listForCurrentUser("10001", true, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getInfmMsgNo()).isEqualTo("INF-1");
        assertThat(result.getContent().getFirst().getFstEnrDtm()).isEqualTo(createdAt);
        verify(cinfmmRepository).findInboxRows("10001", true, pageable);
    }

    @Test
    @DisplayName("unreadCount: 현재 사용자의 미읽음 건수를 반환한다")
    void unreadCount_건수반환() {
        given(cinfmmRepository.countUnread("10001")).willReturn(3L);

        long result = notificationService.unreadCount("10001");

        assertThat(result).isEqualTo(3L);
    }

    @Test
    @DisplayName("캐시 키는 파라미터명 보존 여부와 무관하게 인덱스 기반 SpEL을 사용한다")
    void cacheAnnotations_useIndexedParameterKeys() throws NoSuchMethodException {
        Method unreadCount = NotificationService.class.getMethod("unreadCount", String.class);
        Method markRead =
                NotificationService.class.getMethod("markRead", String.class, String.class);
        Method markAllRead = NotificationService.class.getMethod("markAllRead", String.class);
        Method softDelete =
                NotificationService.class.getMethod("softDelete", String.class, String.class);

        assertThat(unreadCount.getAnnotation(Cacheable.class).key()).isEqualTo("#p0");
        assertThat(markRead.getAnnotation(CacheEvict.class).key()).isEqualTo("#p1");
        assertThat(markAllRead.getAnnotation(CacheEvict.class).key()).isEqualTo("#p0");
        assertThat(softDelete.getAnnotation(CacheEvict.class).key()).isEqualTo("#p1");
    }

    @Test
    @DisplayName("markRead: 본인 알림을 읽음 상태로 변경한다")
    void markRead_본인알림_읽음처리() {
        Cinfmm notification = notification("10001", "N", "N");
        given(cinfmmRepository.findById("INF-1")).willReturn(Optional.of(notification));

        notificationService.markRead("INF-1", "10001");

        assertThat(notification.getInqYn()).isEqualTo("Y");
        assertThat(notification.getInqDtm()).isNotNull();
    }

    @Test
    @DisplayName("markRead: 존재하지 않는 알림이면 예외가 발생한다")
    void markRead_미존재알림_예외발생() {
        given(cinfmmRepository.findById("INF-404")).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead("INF-404", "10001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알림을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("markRead: 삭제된 알림이면 예외가 발생한다")
    void markRead_삭제알림_예외발생() {
        given(cinfmmRepository.findById("INF-1"))
                .willReturn(Optional.of(notification("10001", "N", "Y")));

        assertThatThrownBy(() -> notificationService.markRead("INF-1", "10001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 삭제된 알림입니다");
    }

    @Test
    @DisplayName("markRead: 타인 알림이면 접근을 거부한다")
    void markRead_타인알림_접근거부() {
        given(cinfmmRepository.findById("INF-1"))
                .willReturn(Optional.of(notification("20001", "N", "N")));

        assertThatThrownBy(() -> notificationService.markRead("INF-1", "10001"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("본인 알림이 아닙니다");
    }

    @Test
    @DisplayName("markAllRead: 일괄 읽음 처리 건수를 반환한다")
    void markAllRead_처리건수반환() {
        given(cinfmmRepository.markAllReadByRmsEno("10001")).willReturn(2L);

        long result = notificationService.markAllRead("10001");

        assertThat(result).isEqualTo(2L);
    }

    @Test
    @DisplayName("softDelete: 본인 알림을 논리 삭제한다")
    void softDelete_본인알림_논리삭제() {
        Cinfmm notification = notification("10001", "N", "N");
        given(cinfmmRepository.findById("INF-1")).willReturn(Optional.of(notification));

        notificationService.softDelete("INF-1", "10001");

        assertThat(notification.getDelYn()).isEqualTo("Y");
    }

    private Cinfmm notification(String recipientEno, String readYn, String deletedYn) {
        return Cinfmm.builder()
                .infmMsgNo("INF-1")
                .rmsEno(recipientEno)
                .inqYn(readYn)
                .delYn(deletedYn)
                .build();
    }
}
