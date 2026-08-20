package com.kdb.it.common.admin.waslog.appender;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RingBufferAppenderTest {

    private LoggingEvent event(Level level, String message, Throwable throwable) {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger("com.kdb.it.Sample");
        LoggingEvent e =
                new LoggingEvent("com.kdb.it.Sample", logger, level, message, throwable, null);
        e.setThreadName("test-thread");
        return e;
    }

    @Test
    @DisplayName("이벤트의 레벨·로거·스레드·메시지를 버퍼에 담는다")
    void append_필드매핑() {
        RingBufferAppender appender = new RingBufferAppender();
        appender.setCapacity(10);
        appender.start();

        appender.doAppend(event(Level.WARN, "경고 메시지", null));

        WasLogEntry entry = WasLogBuffer.shared().snapshot().entries().getLast();
        assertThat(entry.level()).isEqualTo("WARN");
        assertThat(entry.logger()).isEqualTo("com.kdb.it.Sample");
        assertThat(entry.thread()).isEqualTo("test-thread");
        assertThat(entry.message()).isEqualTo("경고 메시지");
        assertThat(entry.throwable()).isNull();
    }

    @Test
    @DisplayName("예외가 있으면 스택트레이스 문자열을 담는다")
    void append_예외포함() {
        RingBufferAppender appender = new RingBufferAppender();
        appender.setCapacity(10);
        appender.start();

        appender.doAppend(event(Level.ERROR, "실패", new IllegalStateException("터짐")));

        WasLogEntry entry = WasLogBuffer.shared().snapshot().entries().getLast();
        assertThat(entry.throwable()).contains("IllegalStateException").contains("터짐");
    }

    @Test
    @DisplayName("긴 메시지는 4000자에서 절단한다")
    void append_메시지절단() {
        RingBufferAppender appender = new RingBufferAppender();
        appender.setCapacity(10);
        appender.start();

        appender.doAppend(event(Level.INFO, "가".repeat(5000), null));

        WasLogEntry entry = WasLogBuffer.shared().snapshot().entries().getLast();
        assertThat(entry.message()).hasSize(RingBufferAppender.MAX_MESSAGE_CHARS);
    }
}
