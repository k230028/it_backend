package com.kdb.it.common.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.notification.dispatcher.NotificationDispatcher;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import com.kdb.it.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** 알림 미읽음 카운트 캐시 동작 검증 — @Cacheable 적용 시 repository 1회만 호출. */
@SpringJUnitConfig(classes = {CacheConfig.class, NotificationService.class})
class NotificationServiceCacheTest {

    @Autowired private NotificationService service;
    @Autowired private CacheManager cacheManager;
    @MockitoBean private CinfmmRepository cinfmmRepository;
    @MockitoBean private NotificationDispatcher dispatcher;

    // 캐시는 Spring 컨텍스트에 공유되므로 테스트 간 격리를 위해 매 테스트 전에 초기화한다.
    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("unreadCount 캐시: 동일 사용자 연속 조회 시 repository는 1회만 호출된다")
    void unreadCount_cached() {
        given(cinfmmRepository.countUnread("E001")).willReturn(3L);

        assertThat(service.unreadCount("E001")).isEqualTo(3L);
        assertThat(service.unreadCount("E001")).isEqualTo(3L);

        verify(cinfmmRepository, times(1)).countUnread("E001");
    }

    @Test
    @DisplayName("unreadCount 캐시 evict: 쓰기(markAllRead) 후 동일 사용자 재조회 시 repository가 다시 호출된다")
    void unreadCount_evictedAfterWrite() {
        given(cinfmmRepository.countUnread("E001")).willReturn(3L);

        // 1차 조회 → 캐시 적재
        assertThat(service.unreadCount("E001")).isEqualTo(3L);

        // 동일 사용자 키에 대한 evict 발생 (markAllRead의 @CacheEvict)
        service.markAllRead("E001");

        // 2차 조회 → 캐시 miss → repository 재호출
        assertThat(service.unreadCount("E001")).isEqualTo(3L);

        // evict가 사이에 끼었으므로 count 메서드가 총 2회 호출되어야 한다
        verify(cinfmmRepository, times(2)).countUnread("E001");
    }
}
