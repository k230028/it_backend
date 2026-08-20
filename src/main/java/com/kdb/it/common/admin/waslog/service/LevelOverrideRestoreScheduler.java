package com.kdb.it.common.admin.waslog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 런타임 로그레벨 오버라이드를 되돌리는 스케줄러.
 *
 * <p>스케줄링은 {@code NotificationSchedulingConfig}의 {@code @EnableScheduling}으로 이미 활성이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LevelOverrideRestoreScheduler {

    private final LevelOverrideService service;

    /** 만료분을 스캔해 원복한다. */
    @Scheduled(fixedDelayString = "${app.was-log.restore-scan-ms:30000}")
    public void restore() {
        int restored = service.restoreExpired();
        if (restored > 0) {
            log.info("[WAS로그] 만료된 로그레벨 오버라이드 {}건을 원복했습니다.", restored);
        }
    }
}
