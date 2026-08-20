package com.kdb.it.common.admin.waslog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
    @DisplayName("화이트리스트 밖 로거는 거부한다")
    void apply_허용되지않은로거() {
        assertThatThrownBy(() -> service.apply("com.evil.Thing", "DEBUG", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("com.evil.Thing");
    }

    @Test
    @DisplayName("TTL이 범위를 벗어나면 거부한다")
    void apply_TTL범위밖() {
        assertThatThrownBy(() -> service.apply("com.kdb.it.domain", "DEBUG", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply("com.kdb.it.domain", "DEBUG", 121))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("허용되지 않은 레벨은 거부한다")
    void apply_잘못된레벨() {
        assertThatThrownBy(() -> service.apply("com.kdb.it.domain", "FATAL", 30))
                .isInstanceOf(IllegalArgumentException.class);
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
}
