package com.kdb.it.common.admin.waslog.service;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
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
 *
 * <p>{@link #apply}와 {@link #restoreExpired}는 하나의 락을 공유한다 — 자세한 이유는 {@link #mutationLock} 참고.
 */
@Service
@Slf4j
public class LevelOverrideService {

    /** TTL 상한(분). 끄는 것을 잊어 운영 서버가 느려지는 사고를 막는다. */
    public static final int MAX_TTL_MINUTES = 120;

    /** 변경을 허용하는 로거 접두사. 루트 로거 전체 변경은 허용하지 않는다. */
    public static final List<String> ALLOWED_LOGGER_PREFIXES =
            List.of("com.kdb.it", "org.springframework", "org.hibernate");

    /** 로거명 길이 상한. 화이트리스트 접두사 아래라면 어떤 접미사든 통과하므로 길이만이라도 묶는다. */
    public static final int MAX_LOGGER_NAME_LENGTH = 256;

    /** 동시에 적용 중일 수 있는 오버라이드 수 상한. 레지스트리가 TTL까지 커지는 것을 막는다. */
    public static final int MAX_ACTIVE_OVERRIDES = 50;

    /**
     * 이 프로세스에서 레벨을 바꿀 수 있는 서로 다른 로거 수 상한.
     *
     * <p>logback은 {@code setLogLevel}로 만든 {@code Logger}를 프로세스 수명 동안 해제하지 않으므로, 동시 개수만 제한하면 TTL이 지날
     * 때마다 새 이름으로 계속 늘릴 수 있다. 만료돼도 줄지 않는 카운터에 상한을 둬야 진짜 상한이 선다(BE-62).
     *
     * <p>상한에 걸리면 <b>이미 건드린 로거</b>는 계속 조정할 수 있고 새 이름만 거부된다. 재기동하면 초기화된다.
     */
    public static final int MAX_DISTINCT_LOGGERS = 200;

    private static final Set<String> ALLOWED_LEVELS =
            Set.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE");

    /**
     * 레벨 변경과 만료 복원이 공유하는 락.
     *
     * <p>{@link #restoreExpired}는 레지스트리에서 만료 항목을 원자적으로 제거한 뒤 {@code setLogLevel}을 부른다. 그 사이에 같은 로거로
     * {@link #apply}가 들어오면 두 가지가 어긋난다 — ① 관리자의 새 설정이 복원 값으로 덮이고, ② 새 항목의 {@code previousLevel}에 임시
     * 레벨이 잡혀 다음 만료 때 그 임시 레벨로 영구 고정된다. 재적용이 원래 레벨을 물려받게 한 장치가 그대로 무력화되는 결과다(BE-59).
     *
     * <p>창은 마이크로초 단위이고 스캔 주기는 30초라 순차적인 관리자 조작으로는 도달하지 않지만, 닫는 비용이 거의 없다 — 레벨 변경은 사람이 드물게 하고 스캔은
     * 대부분의 틱에서 아무 일도 하지 않아 실제 경합이 생기지 않는다. 로거별 락으로 쪼갤 이유가 없다.
     */
    private final ReentrantLock mutationLock = new ReentrantLock();

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
     * <p>새 로거 이름은 {@value #MAX_ACTIVE_OVERRIDES}건 동시 적용, 프로세스당 {@value #MAX_DISTINCT_LOGGERS}종까지만
     * 받는다. 이미 적용 중이거나 한 번이라도 건드린 로거의 재조정은 상한과 무관하게 허용한다(BE-62).
     *
     * @param logger {@link #ALLOWED_LOGGER_PREFIXES} 중 하나로 시작하는 로거명. {@value
     *     #MAX_LOGGER_NAME_LENGTH}자 이하
     * @param level ERROR/WARN/INFO/DEBUG/TRACE
     * @param ttlMinutes 1~{@value #MAX_TTL_MINUTES}
     * @throws IllegalArgumentException 로거·레벨·TTL이 규칙을 벗어나거나 로거 수 상한을 넘은 경우
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
        // 상한 판정부터 레지스트리 등록까지가 한 단위다. 만료 복원이 중간에 끼어들면 아래 previousLevel
        // 계산이 복원 직전의 임시 레벨을 읽거나, 복원이 관리자의 새 설정을 덮어쓴다(BE-59).
        mutationLock.lock();
        try {
            requireWithinLoggerBudget(logger);

            // 같은 로거에 두 번 적용하면 두 번째가 읽는 "현재 레벨"은 첫 번째가 써 넣은 임시 레벨이다.
            // 그대로 previousLevel로 저장하면 TTL 만료 후 임시 레벨로 되돌아가 영구 고정된다
            // (예: INFO → DEBUG 적용 → 시끄러워서 INFO 재적용 → 만료 시 DEBUG로 복원되어 그대로 굳음).
            // 이미 오버라이드가 있으면 최초에 잡아둔 원래 레벨을 그대로 물려받는다.
            WasLogDto.LevelOverride existing = registry.find(logger);
            String previous = existing != null ? existing.previousLevel() : configuredLevel(logger);
            loggingSystem.setLogLevel(logger, LogLevel.valueOf(level));

            WasLogDto.LevelOverride override =
                    new WasLogDto.LevelOverride(
                            logger,
                            level,
                            previous,
                            LocalDateTime.now(clock).plusMinutes(ttlMinutes));
            registry.put(override);
            return override;
        } finally {
            mutationLock.unlock();
        }
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
        // 제거와 복원 사이에 apply가 끼어들면 관리자의 새 설정이 복원 값으로 덮인다(BE-59).
        mutationLock.lock();
        try {
            return restoreExpiredLocked();
        } finally {
            mutationLock.unlock();
        }
    }

    /** {@link #mutationLock}을 잡은 상태에서 만료 항목을 복원한다. */
    private int restoreExpiredLocked() {
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
     * 새 로거 이름을 받아도 되는지 판정한다.
     *
     * <p>이미 건드린 로거의 재조정은 새 {@code Logger}를 만들지 않으므로 항상 허용한다. 새 이름만 두 상한에 건다.
     *
     * @throws IllegalArgumentException 동시 적용 수 또는 프로세스 누적 로거 수 상한을 넘은 경우
     */
    private void requireWithinLoggerBudget(String logger) {
        if (registry.isTouched(logger)) {
            return;
        }
        if (registry.activeCount() >= MAX_ACTIVE_OVERRIDES) {
            throw new IllegalArgumentException(
                    "동시에 적용할 수 있는 로그레벨 변경은 "
                            + MAX_ACTIVE_OVERRIDES
                            + "건까지입니다. 만료를 기다리거나 기존 변경을 정리하세요.");
        }
        if (registry.touchedCount() >= MAX_DISTINCT_LOGGERS) {
            throw new IllegalArgumentException(
                    "이 인스턴스에서 레벨을 바꿀 수 있는 로거는 "
                            + MAX_DISTINCT_LOGGERS
                            + "종까지입니다. 이미 변경한 로거는 계속 조정할 수 있으며, 재기동하면 초기화됩니다.");
        }
    }

    /**
     * 화이트리스트 판정.
     *
     * <p>단순 {@code startsWith}는 {@code com.kdb.itX}처럼 패키지 경계를 넘는 이름까지 통과시키므로, 접두사와 정확히 같거나 그 아래
     * 패키지({@code 접두사 + "."})인 경우만 허용한다. 접미사는 열려 있으므로 길이도 함께 제한한다.
     */
    private boolean allowedLogger(String logger) {
        if (logger == null || logger.isBlank()) return false;
        if (logger.length() > MAX_LOGGER_NAME_LENGTH) return false;
        return ALLOWED_LOGGER_PREFIXES.stream()
                .anyMatch(prefix -> logger.equals(prefix) || logger.startsWith(prefix + "."));
    }

    private String configuredLevel(String logger) {
        LoggerConfiguration configuration = loggingSystem.getLoggerConfiguration(logger);
        if (configuration == null || configuration.getConfiguredLevel() == null) return null;
        return configuration.getConfiguredLevel().name();
    }
}
