package com.kdb.it.common.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.Optional;

import com.kdb.it.common.notification.dispatcher.NotificationDispatcher;
import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

/**
 * NotificationService 단위 테스트
 *
 * <p>알림 발송, 수신자별 조회, 읽음 처리와 논리 삭제의 주요 분기를 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private CinfmmRepository cinfmmRepository;

    @Mock
    private NotificationDispatcher dispatcher;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("send: 유효한 수신자이면 채번한 알림을 저장하고 디스패처에 전달한다")
    void send_유효한수신자_저장및발송() {
        NotificationEvent event = NotificationEvent.builder()
                .recipientEno("10001")
                .infmSvcTc(NotificationEvent.TYPE_SYSTEM)
                .ttl("공지")
                .infmMsgCone("내용")
                .infmRcdUrl("/notifications")
                .sdPayload("{\"id\":1}")
                .build();
        given(cinfmmRepository.getNextVal()).willReturn(7L);

        Cinfmm result = notificationService.send(event);

        ArgumentCaptor<Cinfmm> captor = ArgumentCaptor.forClass(Cinfmm.class);
        verify(cinfmmRepository).saveAndFlush(captor.capture());
        assertThat(result).isSameAs(captor.getValue());
        assertThat(result.getInfmMsgNo())
                .isEqualTo("INF-" + LocalDate.now().getYear() + "-00000007");
        assertThat(result.getRmsEno()).isEqualTo("10001");
        assertThat(result.getInqYn()).isEqualTo("N");
        verify(dispatcher).dispatch(result, "{\"id\":1}");
    }

    @Test
    @DisplayName("send: 제목이 100자를 초과하면 100자로 잘라 저장한다")
    void send_제목초과_100자로_clamp() {
        // Arrange
        String longTitle = "가".repeat(150);
        NotificationEvent event = NotificationEvent.builder()
                .recipientEno("E0001")
                .infmSvcTc(NotificationEvent.TYPE_SYSTEM)
                .ttl(longTitle)
                .infmMsgCone("본문")
                .build();
        given(cinfmmRepository.getNextVal()).willReturn(1L);

        // Act
        notificationService.send(event);

        // Assert
        ArgumentCaptor<Cinfmm> captor = ArgumentCaptor.forClass(Cinfmm.class);
        verify(cinfmmRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTtl()).hasSize(100);
    }

    @Test
    @DisplayName("send: 본문이 4000자를 초과하면 4000자로 잘라 저장한다")
    void send_본문초과_4000자로_clamp() {
        // Arrange
        String longBody = "가".repeat(5000);
        NotificationEvent event = NotificationEvent.builder()
                .recipientEno("E0001")
                .infmSvcTc(NotificationEvent.TYPE_SYSTEM)
                .ttl("제목")
                .infmMsgCone(longBody)
                .build();
        given(cinfmmRepository.getNextVal()).willReturn(1L);

        // Act
        notificationService.send(event);

        // Assert
        ArgumentCaptor<Cinfmm> captor = ArgumentCaptor.forClass(Cinfmm.class);
        verify(cinfmmRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getInfmMsgCone()).hasSize(4000);
    }

    @Test
    @DisplayName("send: URL이 300자를 초과하면 300자로 잘라 저장한다")
    void send_URL초과_300자로_clamp() {
        // Arrange
        String longUrl = "/notifications/" + "a".repeat(400);
        NotificationEvent event = NotificationEvent.builder()
                .recipientEno("E0001")
                .infmSvcTc(NotificationEvent.TYPE_SYSTEM)
                .ttl("제목")
                .infmMsgCone("본문")
                .infmRcdUrl(longUrl)
                .build();
        given(cinfmmRepository.getNextVal()).willReturn(1L);

        // Act
        notificationService.send(event);

        // Assert
        ArgumentCaptor<Cinfmm> captor = ArgumentCaptor.forClass(Cinfmm.class);
        verify(cinfmmRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getInfmRcdUrl()).hasSize(300);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("send: 수신자 사번이 없으면 저장하지 않는다")
    void send_수신자없음_저장하지않음(String recipientEno) {
        NotificationEvent event = NotificationEvent.builder()
                .recipientEno(recipientEno)
                .infmSvcTc(NotificationEvent.TYPE_SYSTEM)
                .build();

        Cinfmm result = notificationService.send(event);

        assertThat(result).isNull();
        verify(cinfmmRepository, never()).getNextVal();
        verify(cinfmmRepository, never()).saveAndFlush(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("listForCurrentUser: 조회 조건과 페이지 정보를 리포지토리에 전달한다")
    void listForCurrentUser_조건전달() {
        PageRequest pageable = PageRequest.of(1, 5);
        Page<Cinfmm> page = new PageImpl<>(java.util.List.of(Cinfmm.builder().infmMsgNo("INF-1").build()));
        given(cinfmmRepository.findInbox("10001", true, pageable)).willReturn(page);

        Page<Cinfmm> result = notificationService.listForCurrentUser("10001", true, pageable);

        assertThat(result).isSameAs(page);
        verify(cinfmmRepository).findInbox("10001", true, pageable);
    }

    @Test
    @DisplayName("unreadCount: 현재 사용자의 미읽음 건수를 반환한다")
    void unreadCount_건수반환() {
        given(cinfmmRepository.countUnread("10001")).willReturn(3L);

        long result = notificationService.unreadCount("10001");

        assertThat(result).isEqualTo(3L);
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
        given(cinfmmRepository.findById("INF-1")).willReturn(Optional.of(notification("10001", "N", "Y")));

        assertThatThrownBy(() -> notificationService.markRead("INF-1", "10001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 삭제된 알림입니다");
    }

    @Test
    @DisplayName("markRead: 타인 알림이면 접근을 거부한다")
    void markRead_타인알림_접근거부() {
        given(cinfmmRepository.findById("INF-1")).willReturn(Optional.of(notification("20001", "N", "N")));

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
