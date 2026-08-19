package com.kdb.it.common.admin.waslog.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

/**
 * 로그 이벤트를 {@link WasLogBuffer}에 적재하는 logback Appender.
 *
 * <p>{@code logback-spring.xml}에서 {@code <capacity>}로 버퍼 크기를 주입한다. 메시지·스택트레이스는 각각 4000자·8000자에서 절단해
 * 버퍼 메모리 상한을 고정한다(2000건 × 12000자 ≈ 24MB).
 */
public class RingBufferAppender extends AppenderBase<ILoggingEvent> {

    /** 메시지 보관 상한(문자 수). */
    public static final int MAX_MESSAGE_CHARS = 4000;

    /** 스택트레이스 보관 상한(문자 수). */
    public static final int MAX_THROWABLE_CHARS = 8000;

    private int capacity = WasLogBuffer.DEFAULT_CAPACITY;

    /** logback XML의 {@code <capacity>} 주입용 setter. */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void start() {
        WasLogBuffer.shared().resize(capacity);
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        WasLogBuffer.shared()
                .add(
                        event.getTimeStamp(),
                        event.getLevel().toString(),
                        event.getThreadName(),
                        event.getLoggerName(),
                        truncate(event.getFormattedMessage(), MAX_MESSAGE_CHARS),
                        throwableText(event));
    }

    private String throwableText(ILoggingEvent event) {
        IThrowableProxy proxy = event.getThrowableProxy();
        if (proxy == null) return null;
        return truncate(ThrowableProxyUtil.asString(proxy), MAX_THROWABLE_CHARS);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
