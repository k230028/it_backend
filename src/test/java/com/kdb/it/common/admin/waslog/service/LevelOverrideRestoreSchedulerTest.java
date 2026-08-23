package com.kdb.it.common.admin.waslog.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 만료 로그레벨 오버라이드 원복 스케줄러의 위임·로깅 분기 검증. */
@ExtendWith(MockitoExtension.class)
class LevelOverrideRestoreSchedulerTest {

    @Mock private LevelOverrideService service;

    private LevelOverrideRestoreScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LevelOverrideRestoreScheduler(service);
    }

    @Test
    @DisplayName("원복 대상이 있으면 서비스에 위임하고 안내 로그 분기를 탄다")
    void restore_delegatesWhenRestored() {
        given(service.restoreExpired()).willReturn(3);

        scheduler.restore();

        verify(service).restoreExpired();
    }

    @Test
    @DisplayName("원복 대상이 없으면 안내 로그 분기를 타지 않는다")
    void restore_skipsLogWhenNothingRestored() {
        given(service.restoreExpired()).willReturn(0);

        scheduler.restore();

        verify(service).restoreExpired();
    }

    @Test
    @DisplayName("서비스 예외는 삼키지 않고 그대로 전파한다")
    void restore_propagatesFailure() {
        given(service.restoreExpired()).willThrow(new IllegalStateException("lock"));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> scheduler.restore());
    }
}
