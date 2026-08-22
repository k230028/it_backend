package com.kdb.it.common.admin.waslog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;

class LevelOverrideServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");

    private LoggingSystem loggingSystem;
    private LevelOverrideRegistry registry;
    private LevelOverrideService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        loggingSystem = mock(LoggingSystem.class);
        registry = new LevelOverrideRegistry();
        clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        service = new LevelOverrideService(loggingSystem, registry, clock);
    }

    @Test
    @DisplayName("레벨을 적용하고 직전 레벨과 만료 시각을 등록한다")
    void apply_정상() {
        given(loggingSystem.getLoggerConfiguration("com.kdb.it.domain"))
                .willReturn(
                        new LoggerConfiguration("com.kdb.it.domain", LogLevel.INFO, LogLevel.INFO));

        WasLogDto.LevelOverride override = service.apply("com.kdb.it.domain", "DEBUG", 30);

        verify(loggingSystem).setLogLevel("com.kdb.it.domain", LogLevel.DEBUG);
        assertThat(override.level()).isEqualTo("DEBUG");
        assertThat(override.previousLevel()).isEqualTo("INFO");
        assertThat(override.expiresAt())
                .isEqualTo(java.time.LocalDateTime.now(clock).plusMinutes(30));
        assertThat(registry.list()).hasSize(1);
    }

    @Test
    @DisplayName("같은 로거에 다시 적용해도 최초 레벨을 previousLevel로 유지한다")
    void apply_재적용_최초레벨보존() {
        given(loggingSystem.getLoggerConfiguration("com.kdb.it.domain"))
                .willReturn(
                        new LoggerConfiguration("com.kdb.it.domain", LogLevel.INFO, LogLevel.INFO));
        service.apply("com.kdb.it.domain", "DEBUG", 30);

        // 두 번째 호출 시점의 "현재 설정 레벨"은 이미 첫 번째가 써 넣은 DEBUG다.
        given(loggingSystem.getLoggerConfiguration("com.kdb.it.domain"))
                .willReturn(
                        new LoggerConfiguration(
                                "com.kdb.it.domain", LogLevel.DEBUG, LogLevel.DEBUG));
        WasLogDto.LevelOverride second = service.apply("com.kdb.it.domain", "TRACE", 30);

        assertThat(second.previousLevel()).isEqualTo("INFO");
    }

    @Test
    @DisplayName("화이트리스트 밖 로거는 거부하고 레벨을 건드리지 않는다")
    void apply_허용되지않은로거() {
        assertThatThrownBy(() -> service.apply("com.evil.Thing", "DEBUG", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("com.evil.Thing");
        verify(loggingSystem, never()).setLogLevel(any(), any());
    }

    @Test
    @DisplayName("접두사만 같고 패키지 경계를 넘는 이름은 거부한다")
    void apply_접두사경계() {
        assertThatThrownBy(() -> service.apply("com.kdb.itX", "DEBUG", 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply("", "DEBUG", 30))
                .isInstanceOf(IllegalArgumentException.class);
        verify(loggingSystem, never()).setLogLevel(any(), any());
    }

    @Test
    @DisplayName("TTL이 범위를 벗어나면 거부하고 레벨을 건드리지 않는다")
    void apply_TTL범위밖() {
        assertThatThrownBy(() -> service.apply("com.kdb.it.domain", "DEBUG", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply("com.kdb.it.domain", "DEBUG", 121))
                .isInstanceOf(IllegalArgumentException.class);
        // 검증이 setLogLevel보다 먼저여야 한다 — 레벨만 바뀌고 만료 등록에 실패하면 영구 오버라이드가 된다.
        verify(loggingSystem, never()).setLogLevel(any(), any());
    }

    @Test
    @DisplayName("허용되지 않은 레벨은 거부하고 레벨을 건드리지 않는다")
    void apply_잘못된레벨() {
        assertThatThrownBy(() -> service.apply("com.kdb.it.domain", "FATAL", 30))
                .isInstanceOf(IllegalArgumentException.class);
        verify(loggingSystem, never()).setLogLevel(any(), any());
    }

    @Test
    @DisplayName("원래 설정이 없던 로거는 null로 되돌려 상위 상속으로 복원한다")
    void restoreExpired_설정없음_null복원() {
        given(loggingSystem.getLoggerConfiguration("com.kdb.it.c")).willReturn(null);
        service.apply("com.kdb.it.c", "DEBUG", 1);

        LevelOverrideService later =
                new LevelOverrideService(
                        loggingSystem,
                        registry,
                        Clock.fixed(NOW.plusSeconds(120), ZoneId.of("Asia/Seoul")));
        later.restoreExpired();

        verify(loggingSystem).setLogLevel("com.kdb.it.c", null);
    }

    @Test
    @DisplayName("만료된 오버라이드만 직전 레벨로 되돌린다")
    void restoreExpired_만료분만복원() {
        given(loggingSystem.getLoggerConfiguration("com.kdb.it.a"))
                .willReturn(new LoggerConfiguration("com.kdb.it.a", LogLevel.INFO, LogLevel.INFO));
        given(loggingSystem.getLoggerConfiguration("com.kdb.it.b"))
                .willReturn(new LoggerConfiguration("com.kdb.it.b", LogLevel.WARN, LogLevel.WARN));
        service.apply("com.kdb.it.a", "DEBUG", 1);
        service.apply("com.kdb.it.b", "DEBUG", 60);

        LevelOverrideService later =
                new LevelOverrideService(
                        loggingSystem,
                        registry,
                        Clock.fixed(NOW.plusSeconds(120), ZoneId.of("Asia/Seoul")));
        int restored = later.restoreExpired();

        assertThat(restored).isEqualTo(1);
        verify(loggingSystem).setLogLevel("com.kdb.it.a", LogLevel.INFO);
        assertThat(registry.list())
                .extracting(WasLogDto.LevelOverride::logger)
                .containsExactly("com.kdb.it.b");
    }

    @Test
    @DisplayName("동시 적용 상한을 넘는 새 로거는 거부한다")
    void apply_동시상한초과_거부() {
        for (int i = 0; i < LevelOverrideService.MAX_ACTIVE_OVERRIDES; i++) {
            service.apply("com.kdb.it.bulk" + i, "DEBUG", 30);
        }

        assertThatThrownBy(() -> service.apply("com.kdb.it.overflow", "DEBUG", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(LevelOverrideService.MAX_ACTIVE_OVERRIDES));
        verify(loggingSystem, never()).setLogLevel("com.kdb.it.overflow", LogLevel.DEBUG);
    }

    @Test
    @DisplayName("상한에 걸려도 이미 건드린 로거는 계속 조정할 수 있다")
    void apply_상한초과여도_기존로거는허용() {
        for (int i = 0; i < LevelOverrideService.MAX_ACTIVE_OVERRIDES; i++) {
            service.apply("com.kdb.it.bulk" + i, "DEBUG", 30);
        }

        WasLogDto.LevelOverride again = service.apply("com.kdb.it.bulk0", "TRACE", 10);

        assertThat(again.level()).isEqualTo("TRACE");
        verify(loggingSystem).setLogLevel("com.kdb.it.bulk0", LogLevel.TRACE);
    }

    @Test
    @DisplayName("만료로 자리가 나도 프로세스 누적 로거 수 상한은 남는다")
    void apply_누적상한은_만료로회복되지않는다() {
        // 만료로 활성 슬롯은 매번 비지만, logback Logger는 프로세스 수명 동안 남는다.
        java.time.LocalDateTime afterTtl = java.time.LocalDateTime.now(clock).plusMinutes(5);
        for (int i = 0; i < LevelOverrideService.MAX_DISTINCT_LOGGERS; i++) {
            service.apply("com.kdb.it.rotate" + i, "DEBUG", 1);
            registry.removeExpired(afterTtl);
        }

        assertThat(registry.activeCount()).isZero();
        assertThatThrownBy(() -> service.apply("com.kdb.it.rotateNew", "DEBUG", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(LevelOverrideService.MAX_DISTINCT_LOGGERS));
    }

    @Test
    @DisplayName("로거명 길이 상한을 넘으면 거부한다")
    void apply_로거명길이초과_거부() {
        String tooLong = "com.kdb.it." + "a".repeat(LevelOverrideService.MAX_LOGGER_NAME_LENGTH);

        assertThatThrownBy(() -> service.apply(tooLong, "DEBUG", 30))
                .isInstanceOf(IllegalArgumentException.class);
        verify(loggingSystem, never()).setLogLevel(tooLong, LogLevel.DEBUG);
    }

    /** 시각을 마음대로 옮길 수 있는 Clock. 한 서비스 인스턴스에서 적용과 만료를 모두 재현하기 위해 쓴다. */
    private static final class MovableClock extends Clock {
        private final AtomicReference<Instant> now;

        private MovableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Test
    @DisplayName("복원이 진행 중이면 같은 로거의 재적용이 끼어들지 못한다 — 새 설정이 복원 값으로 덮이지 않는다")
    void restoreExpired_와_apply_는_직렬화된다() throws Exception {
        // Arrange: INFO 로거에 DEBUG를 걸고 TTL이 지난 시각으로 옮긴다.
        // 락은 인스턴스 필드이므로 두 조작이 반드시 같은 인스턴스를 거쳐야 한다 —
        // 운영에서는 싱글턴 빈 하나뿐이라 이 조건이 자연히 성립한다.
        MovableClock movable = new MovableClock(NOW);
        LevelOverrideService racing = new LevelOverrideService(loggingSystem, registry, movable);
        given(loggingSystem.getLoggerConfiguration("com.kdb.it.race"))
                .willReturn(
                        new LoggerConfiguration("com.kdb.it.race", LogLevel.INFO, LogLevel.INFO));
        racing.apply("com.kdb.it.race", "DEBUG", 1);
        movable.now.set(NOW.plusSeconds(120));

        // 복원이 setLogLevel을 부르는 동안 다른 스레드가 같은 로거로 apply를 시도하게 만든다.
        List<String> order = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch restoreEntered = new CountDownLatch(1);
        CountDownLatch applyFinished = new CountDownLatch(1);
        willAnswer(
                        invocation -> {
                            restoreEntered.countDown();
                            // apply 스레드에 확실히 기회를 준다. 락이 없으면 여기서 끼어든다.
                            applyFinished.await(300, TimeUnit.MILLISECONDS);
                            // 기록은 이 호출이 "끝난" 시점에 남긴다 — 진입 시점에 남기면 락이 있든
                            // 없든 순서가 같아 보여 경합을 구분하지 못한다.
                            order.add("restore:" + invocation.getArgument(1));
                            return null;
                        })
                .given(loggingSystem)
                .setLogLevel("com.kdb.it.race", LogLevel.INFO);
        willAnswer(
                        invocation -> {
                            order.add("apply:" + invocation.getArgument(1));
                            return null;
                        })
                .given(loggingSystem)
                .setLogLevel("com.kdb.it.race", LogLevel.WARN);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> restoring = pool.submit(racing::restoreExpired);
            restoreEntered.await(1, TimeUnit.SECONDS);
            Future<?> applying =
                    pool.submit(
                            () -> {
                                racing.apply("com.kdb.it.race", "WARN", 30);
                                applyFinished.countDown();
                            });
            restoring.get(5, TimeUnit.SECONDS);
            applying.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // 마지막으로 실제 로거에 쓴 값이 관리자의 WARN이어야 한다. 락이 없으면 복원의 INFO가
        // apply의 WARN 뒤에 도착해 새 설정을 덮어쓴다(order가 뒤집힌다).
        assertThat(order).containsExactly("restore:INFO", "apply:WARN");
        assertThat(registry.find("com.kdb.it.race")).isNotNull();
        assertThat(registry.find("com.kdb.it.race").level()).isEqualTo("WARN");
        // 복원이 먼저 끝났으므로 재적용의 previousLevel은 임시 레벨이 아니라 설정 레벨(INFO)이다.
        assertThat(registry.find("com.kdb.it.race").previousLevel()).isEqualTo("INFO");
    }
}
