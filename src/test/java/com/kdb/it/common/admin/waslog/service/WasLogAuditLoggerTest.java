package com.kdb.it.common.admin.waslog.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 감사 로그가 실제 로그인 계정명을 남기고, 인증 정보가 없어도 예외 없이 동작하는지 검증한다.
 *
 * <p>{@code WasLogDownloadTest}를 비롯한 컨트롤러 슬라이스 테스트는 {@code WasLogAuditLogger}를 목으로 대체하므로 실제 {@code
 * actor()} 추출 로직은 이 테스트에서만 검증된다.
 */
class WasLogAuditLoggerTest {

    private final WasLogAuditLogger auditLogger = new WasLogAuditLogger();
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(WasLogAuditLogger.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 관리자가 있으면 실제 계정명을 감사 로그에 남긴다")
    void logDownload_인증된사용자_실명기록() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("admin01", null, List.of()));

        auditLogger.logDownload("SVR1", 10);

        assertThat(listAppender.list).hasSize(1);
        assertThat(listAppender.list.getFirst().getFormattedMessage())
                .contains("actor=admin01")
                .contains("instance=SVR1")
                .contains("lines=10");
    }

    @Test
    @DisplayName("인증 정보가 없어도 예외 없이 anonymous로 기록한다")
    void logSnapshotAccess_인증정보없음_예외없음() {
        SecurityContextHolder.clearContext();

        auditLogger.logSnapshotAccess("SVR1");

        assertThat(listAppender.list).hasSize(1);
        assertThat(listAppender.list.getFirst().getFormattedMessage()).contains("actor=anonymous");
    }

    @Test
    @DisplayName("레벨 변경 감사는 요청 필드를 모두 남긴다")
    void logLevelChange_요청필드기록() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("admin02", null, List.of()));

        auditLogger.logLevelChange(new WasLogDto.LevelRequest("SVR1", "com.kdb.it", "DEBUG", 30));

        assertThat(listAppender.list.getFirst().getFormattedMessage())
                .contains("actor=admin02")
                .contains("logger=com.kdb.it")
                .contains("level=DEBUG")
                .contains("ttl=30분");
    }
}
