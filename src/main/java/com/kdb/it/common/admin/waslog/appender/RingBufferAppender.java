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

    /**
     * 적재 대상 버퍼. {@link #start()}에서 한 번만 해석한다.
     *
     * <p>매 이벤트마다 다시 찾으면 로깅 경로에서 logback Context 잠금을 잡게 된다. 초기값은 공용 저장소에 붙지 않은 빈 버퍼다 — 생성 시점에 {@code
     * LoggerFactory}를 건드리면 SLF4J 초기화에 재진입한다.
     */
    private WasLogBuffer buffer = new WasLogBuffer(WasLogBuffer.DEFAULT_CAPACITY);

    /** logback XML의 {@code <capacity>} 주입용 setter. */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void start() {
        // 자신의 Context를 넘긴다 — 이 시점은 SLF4J 초기화 도중이라 LoggerFactory로는 진짜
        // LoggerContext를 얻을 수 없고, 그러면 조회 측과 다른 저장소에 쌓게 된다.
        buffer = WasLogBuffer.attachedTo(getContext());
        buffer.resize(capacity);
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        buffer.add(
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
