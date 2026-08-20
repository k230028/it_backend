package com.kdb.it.common.admin.waslog.service;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Service;

/**
 * 런타임 로그레벨 변경 서비스.
 *
 * <p>Actuator 엔드포인트를 노출하지 않고 {@link LoggingSystem} 빈만 사용한다. 모든 변경은 TTL을 가지며 {@link
 * LevelOverrideRestoreScheduler}가 만료 시 직전 레벨로 되돌린다. 재기동 시에는 설정 파일 레벨로 자연 복원된다.
 */
@Service
public class LevelOverrideService {

    /** TTL 상한(분). 끄는 것을 잊어 운영 서버가 느려지는 사고를 막는다. */
    public static final int MAX_TTL_MINUTES = 120;

    /** 변경을 허용하는 로거 접두사. 루트 로거 전체 변경은 허용하지 않는다. */
    public static final List<String> ALLOWED_LOGGER_PREFIXES =
            List.of("com.kdb.it", "org.springframework", "org.hibernate");

    private static final Set<String> ALLOWED_LEVELS =
            Set.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE");

    private final LoggingSystem loggingSystem;
    private final LevelOverrideRegistry registry;
    private final Clock clock;

    public LevelOverrideService(
            LoggingSystem loggingSystem, LevelOverrideRegistry registry, Clock clock) {
        this.loggingSystem = loggingSystem;
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * 로거 레벨을 한시적으로 변경한다.
     *
     * @param logger {@link #ALLOWED_LOGGER_PREFIXES} 중 하나로 시작하는 로거명
     * @param level ERROR/WARN/INFO/DEBUG/TRACE
     * @param ttlMinutes 1~{@value #MAX_TTL_MINUTES}
     * @throws IllegalArgumentException 로거·레벨·TTL이 규칙을 벗어난 경우
     */
    public WasLogDto.LevelOverride apply(String logger, String level, int ttlMinutes) {
        if (logger == null || ALLOWED_LOGGER_PREFIXES.stream().noneMatch(logger::startsWith)) {
            throw new IllegalArgumentException("변경이 허용되지 않은 로거: " + logger);
        }
        if (level == null || !ALLOWED_LEVELS.contains(level)) {
            throw new IllegalArgumentException("허용되지 않은 로그 레벨: " + level);
        }
        if (ttlMinutes < 1 || ttlMinutes > MAX_TTL_MINUTES) {
            throw new IllegalArgumentException(
                    "TTL은 1~" + MAX_TTL_MINUTES + "분이어야 합니다: " + ttlMinutes);
        }

        String previous = configuredLevel(logger);
        loggingSystem.setLogLevel(logger, LogLevel.valueOf(level));

        WasLogDto.LevelOverride override =
                new WasLogDto.LevelOverride(
                        logger, level, previous, LocalDateTime.now(clock).plusMinutes(ttlMinutes));
        registry.put(override);
        return override;
    }

    /**
     * 만료된 오버라이드를 직전 레벨로 되돌린다.
     *
     * @return 복원한 건수
     */
    public int restoreExpired() {
        List<WasLogDto.LevelOverride> expired = registry.removeExpired(LocalDateTime.now(clock));
        for (WasLogDto.LevelOverride override : expired) {
            LogLevel restore =
                    override.previousLevel() == null
                            ? null
                            : LogLevel.valueOf(override.previousLevel());
            loggingSystem.setLogLevel(override.logger(), restore);
        }
        return expired.size();
    }

    private String configuredLevel(String logger) {
        LoggerConfiguration configuration = loggingSystem.getLoggerConfiguration(logger);
        if (configuration == null || configuration.getConfiguredLevel() == null) return null;
        return configuration.getConfiguredLevel().name();
    }
}
