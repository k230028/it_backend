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

    private final LoggerContext context = new LoggerContext();

    /** 적재 대상 Context를 명시한 appender. 실제 logback 설정도 start() 전에 Context를 주입한다. */
    private RingBufferAppender startedAppender() {
        RingBufferAppender appender = new RingBufferAppender();
        appender.setContext(context);
        appender.setCapacity(10);
        appender.start();
        return appender;
    }

    private LoggingEvent event(Level level, String message, Throwable throwable) {
        Logger logger = context.getLogger("com.kdb.it.Sample");
        LoggingEvent e =
                new LoggingEvent("com.kdb.it.Sample", logger, level, message, throwable, null);
        e.setThreadName("test-thread");
        return e;
    }

    /** 조회 측이 보는 버퍼 — Context의 공용 저장소를 통해 appender와 만나야 한다. */
    private WasLogEntry lastEntry() {
        return WasLogBuffer.attachedTo(context).snapshot().entries().getLast();
    }

    @Test
    @DisplayName("이벤트의 레벨·로거·스레드·메시지를 버퍼에 담는다")
    void append_필드매핑() {
        startedAppender().doAppend(event(Level.WARN, "경고 메시지", null));

        WasLogEntry entry = lastEntry();
        assertThat(entry.level()).isEqualTo("WARN");
        assertThat(entry.logger()).isEqualTo("com.kdb.it.Sample");
        assertThat(entry.thread()).isEqualTo("test-thread");
        assertThat(entry.message()).isEqualTo("경고 메시지");
        assertThat(entry.throwable()).isNull();
    }

    @Test
    @DisplayName("예외가 있으면 스택트레이스 문자열을 담는다")
    void append_예외포함() {
        startedAppender().doAppend(event(Level.ERROR, "실패", new IllegalStateException("터짐")));

        assertThat(lastEntry().throwable()).contains("IllegalStateException").contains("터짐");
    }

    @Test
    @DisplayName("긴 메시지는 4000자에서 절단한다")
    void append_메시지절단() {
        startedAppender().doAppend(event(Level.INFO, "가".repeat(5000), null));

        assertThat(lastEntry().message()).hasSize(RingBufferAppender.MAX_MESSAGE_CHARS);
    }

    @Test
    @DisplayName("적재분은 같은 Context에 붙은 다른 버퍼 인스턴스에서도 보인다")
    void append_다른버퍼인스턴스에서조회() {
        // 이 단언이 이 기능의 생명줄이다. appender와 조회 서비스가 서로 다른 클래스로더에 로드되면
        // static 필드로는 만나지 못하고 화면이 오류 없이 "로그 0건"만 보여준다. 공용 저장소가
        // logback Context에 있어야만 별개 인스턴스가 같은 적재분을 본다.
        startedAppender().doAppend(event(Level.INFO, "공용 저장소", null));

        WasLogBuffer reader = WasLogBuffer.attachedTo(context);
        WasLogBuffer another = WasLogBuffer.attachedTo(context);

        assertThat(reader.snapshot().entries()).hasSize(1);
        assertThat(another.snapshot().entries())
                .extracting(WasLogEntry::message)
                .containsExactly("공용 저장소");
        assertThat(another.capacity()).isEqualTo(10);
    }
}
