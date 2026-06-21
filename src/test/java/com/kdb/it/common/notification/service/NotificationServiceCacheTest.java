package com.kdb.it.common.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.notification.dispatcher.NotificationDispatcher;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import com.kdb.it.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 알림 미읽음 카운트 캐시 동작 검증 — @Cacheable 적용 시 repository 1회만 호출.
 */
@SpringJUnitConfig(classes = {CacheConfig.class, NotificationService.class})
class NotificationServiceCacheTest {

    @Autowired private NotificationService service;
    @MockitoBean private CinfmmRepository cinfmmRepository;
    @MockitoBean private NotificationDispatcher dispatcher;

    @Test
    @DisplayName("unreadCount 캐시: 동일 사용자 연속 조회 시 repository는 1회만 호출된다")
    void unreadCount_cached() {
        given(cinfmmRepository.countUnread("E001")).willReturn(3L);

        assertThat(service.unreadCount("E001")).isEqualTo(3L);
        assertThat(service.unreadCount("E001")).isEqualTo(3L);

        verify(cinfmmRepository, times(1)).countUnread("E001");
    }
}
