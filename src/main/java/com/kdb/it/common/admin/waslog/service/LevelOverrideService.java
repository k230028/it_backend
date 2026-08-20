package com.kdb.it.common.admin.waslog.service;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        if (!allowedLogger(logger)) {
            throw new IllegalArgumentException("변경이 허용되지 않은 로거: " + logger);
        }
        if (level == null || !ALLOWED_LEVELS.contains(level)) {
            throw new IllegalArgumentException("허용되지 않은 로그 레벨: " + level);
        }
        if (ttlMinutes < 1 || ttlMinutes > MAX_TTL_MINUTES) {
            throw new IllegalArgumentException(
                    "TTL은 1~" + MAX_TTL_MINUTES + "분이어야 합니다: " + ttlMinutes);
        }

        // 같은 로거에 두 번 적용하면 두 번째가 읽는 "현재 레벨"은 첫 번째가 써 넣은 임시 레벨이다.
        // 그대로 previousLevel로 저장하면 TTL 만료 후 임시 레벨로 되돌아가 영구 고정된다
        // (예: INFO → DEBUG 적용 → 시끄러워서 INFO 재적용 → 만료 시 DEBUG로 복원되어 그대로 굳음).
        // 이미 오버라이드가 있으면 최초에 잡아둔 원래 레벨을 그대로 물려받는다.
        WasLogDto.LevelOverride existing = registry.find(logger);
        String previous = existing != null ? existing.previousLevel() : configuredLevel(logger);
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
     * <p>{@code previousLevel}이 null이면 null을 그대로 넘겨 설정을 지우고 상위 로거 상속으로 되돌린다.
     *
     * <p>이 메서드는 레벨을 바꾸는 주체가 이 기능뿐이라고 가정한다. Actuator {@code loggers} 엔드포인트를 열거나 logback 설정 자동 재로딩을
     * 켜면 그 가정이 깨져 남의 변경을 덮어쓸 수 있다.
     *
     * @return 복원한 건수
     */
    public int restoreExpired() {
        List<WasLogDto.LevelOverride> expired = registry.removeExpired(LocalDateTime.now(clock));
        int restored = 0;
        for (WasLogDto.LevelOverride override : expired) {
            LogLevel restore =
                    override.previousLevel() == null
                            ? null
                            : LogLevel.valueOf(override.previousLevel());
            try {
                loggingSystem.setLogLevel(override.logger(), restore);
                restored++;
            } catch (RuntimeException e) {
                // 한 건이 실패해도 나머지는 되돌린다 — 이미 레지스트리에서 빠졌으므로 여기서 멈추면 영구 고정된다.
                log.warn("[WAS로그] 로그레벨 복원 실패 logger={} level={}", override.logger(), restore, e);
            }
        }
        return restored;
    }

    /**
     * 화이트리스트 판정.
     *
     * <p>단순 {@code startsWith}는 {@code com.kdb.itX}처럼 패키지 경계를 넘는 이름까지 통과시키므로, 접두사와 정확히 같거나 그 아래
     * 패키지({@code 접두사 + "."})인 경우만 허용한다.
     */
    private boolean allowedLogger(String logger) {
        if (logger == null || logger.isBlank()) return false;
        return ALLOWED_LOGGER_PREFIXES.stream()
                .anyMatch(prefix -> logger.equals(prefix) || logger.startsWith(prefix + "."));
    }

    private String configuredLevel(String logger) {
        LoggerConfiguration configuration = loggingSystem.getLoggerConfiguration(logger);
        if (configuration == null || configuration.getConfiguredLevel() == null) return null;
        return configuration.getConfiguredLevel().name();
    }
}
