package com.kdb.it.common.notification.scheduler;

import com.kdb.it.common.notification.repository.CinfmmRepository;
import com.kdb.it.common.notification.service.NotificationDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationRetrySchedulerTest {

    @Mock
    private CinfmmRepository repository;
    @Mock
    private NotificationDispatchService dispatchService;

    private NotificationRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationRetryScheduler(repository, dispatchService);
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 5);
        ReflectionTestUtils.setField(scheduler, "batchSize", 50);
    }

    @Test
    @DisplayName("재시도 배치는 조회된 ID만 dispatch하고 개별 실패 후에도 다음 건을 계속한다")
    void retry_continuesAfterFailure() {
        given(repository.findRetryableIds(anyList(), eq(5), any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of("INF-1", "INF-2"));
        doThrow(new IllegalStateException("lock")).when(dispatchService).dispatch("INF-1");

        scheduler.retry();

        verify(dispatchService).dispatch("INF-1");
        verify(dispatchService).dispatch("INF-2");
    }
}
