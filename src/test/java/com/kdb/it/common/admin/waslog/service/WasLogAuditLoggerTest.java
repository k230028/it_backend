package com.kdb.it.common.admin.waslog.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 감사 로그가 실제 로그인 계정명을 남기고, 인증 정보가 없어도 예외 없이 동작하는지 검증한다. 또한 조회 감사의 스로틀과 개행 이스케이프가 실제로 동작하는지 검증한다.
 *
 * <p>{@code WasLogDownloadTest}를 비롯한 컨트롤러 슬라이스 테스트는 {@code WasLogAuditLogger}를 목으로 대체하므로 실제 {@code
 * actor()} 추출·스로틀·이스케이프 로직은 이 테스트에서만 검증된다.
 */
class WasLogAuditLoggerTest {

    // 스로틀(10분 창) 판정이 결정적으로 동작하도록 고정 시각을 쓴다.
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneId.systemDefault());
    private final WasLogAuditLogger auditLogger = new WasLogAuditLogger(clock);
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(WasLogAuditLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 관리자가 있으면 실제 계정명을 감사 로그에 남긴다")
    void logDownload_인증된사용자_실명기록() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("admin01", null, List.of()));

        auditLogger.logDownload("SVR1", 10);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("actor=admin01")
                .contains("instance=SVR1")
                .contains("lines=10");
    }

    @Test
    @DisplayName("인증 정보가 없어도 예외 없이 anonymous로 기록한다")
    void logSnapshotAccess_인증정보없음_예외없음() {
        SecurityContextHolder.clearContext();

        auditLogger.logSnapshotAccess("SVR1");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("actor=anonymous");
    }

    @Test
    @DisplayName("레벨 변경 감사는 요청 필드를 모두 남긴다")
    void logLevelChange_요청필드기록() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("admin02", null, List.of()));

        auditLogger.logLevelChange(new WasLogDto.LevelRequest("SVR1", "com.kdb.it", "DEBUG", 30));

        assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("actor=admin02")
                .contains("logger=com.kdb.it")
                .contains("level=DEBUG")
                .contains("ttl=30분");
    }

    @Test
    @DisplayName("같은 행위자·인스턴스의 연속 조회는 한 번만 기록한다")
    void logSnapshotAccess_스로틀() {
        auditLogger.logSnapshotAccess("SVR1");
        auditLogger.logSnapshotAccess("SVR1");
        auditLogger.logSnapshotAccess("SVR1");

        assertThat(appender.list).hasSize(1);
    }

    @Test
    @DisplayName("다른 인스턴스는 따로 기록한다")
    void logSnapshotAccess_인스턴스별() {
        auditLogger.logSnapshotAccess("SVR1");
        auditLogger.logSnapshotAccess("SVR2");

        assertThat(appender.list).hasSize(2);
    }

    @Test
    @DisplayName("서로 다른 인스턴스ID를 계속 보내도 추적 맵이 무한히 자라지 않는다")
    void logSnapshotAccess_추적맵상한() {
        for (int i = 0; i < 1500; i++) {
            auditLogger.logSnapshotAccess("SVR" + i);
        }

        // 상한에 닿으면 비우므로 기록은 남되 맵 크기는 상한 아래로 유지된다.
        assertThat(appender.list).hasSize(1500);
        assertThat(auditLogger.trackedKeyCount()).isLessThan(1000);
    }

    @Test
    @DisplayName("개행이 든 값은 가짜 감사 줄을 만들지 못하게 이스케이프한다")
    void 감사값_개행이스케이프() {
        auditLogger.logDownload("SVR1\n[WAS로그감사] 조회 actor=victim", 0);

        assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain("\n");
    }
}
