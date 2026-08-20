package com.kdb.it.common.admin.waslog.appender;

import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WAS 로그 링버퍼.
 *
 * <p>logback Appender는 Spring 컨텍스트보다 먼저 기동하므로 빈으로 만들 수 없다. 프로세스 공용 인스턴스를 {@link #shared()}로 노출하고,
 * 서비스 계층은 {@link #snapshot()}만 읽는다.
 *
 * <p>모든 공개 메서드는 {@code synchronized}다. 적재는 로깅 경로에서 호출되므로 O(1) 작업만 한다.
 */
public final class WasLogBuffer {

    /** 기본 용량. 설정으로 덮어쓸 수 있다. */
    public static final int DEFAULT_CAPACITY = 2000;

    private static final WasLogBuffer SHARED = new WasLogBuffer(DEFAULT_CAPACITY);

    /**
     * 버퍼 스냅샷.
     *
     * @param epoch 버퍼 세대 식별자. 인스턴스가 재기동하면 바뀌므로 클라이언트는 커서를 버려야 한다
     * @param oldestSeq 남아 있는 가장 오래된 항목의 seq. 비었으면 0
     * @param lastSeq 마지막으로 적재된 항목의 seq. 비었으면 0
     * @param entries seq 오름차순 복사본
     */
    public record BufferSnapshot(
            String epoch, long oldestSeq, long lastSeq, List<WasLogEntry> entries) {}

    private final String epoch = UUID.randomUUID().toString();

    private WasLogEntry[] slots;
    private int writeIndex;
    private int size;
    private long seq;

    WasLogBuffer(int capacity) {
        this.slots = new WasLogEntry[Math.max(1, capacity)];
    }

    /** 프로세스 공용 인스턴스. */
    public static WasLogBuffer shared() {
        return SHARED;
    }

    /** 현재 용량. */
    public synchronized int capacity() {
        return slots.length;
    }

    /**
     * 용량을 바꾸고 버퍼를 비운다. Appender 기동 시 1회만 호출한다.
     *
     * @param capacity 1 미만이면 1로 보정
     */
    public synchronized void resize(int capacity) {
        this.slots = new WasLogEntry[Math.max(1, capacity)];
        this.writeIndex = 0;
        this.size = 0;
    }

    /** 로그 한 줄을 적재한다. 용량 초과 시 가장 오래된 항목을 덮어쓴다. */
    public synchronized void add(
            long timestamp,
            String level,
            String thread,
            String logger,
            String message,
            String throwable) {
        seq++;
        slots[writeIndex] =
                new WasLogEntry(seq, timestamp, level, thread, logger, message, throwable);
        writeIndex = (writeIndex + 1) % slots.length;
        if (size < slots.length) size++;
    }

    /** 현재 보관 중인 항목을 seq 오름차순 복사본으로 반환한다. */
    public synchronized BufferSnapshot snapshot() {
        List<WasLogEntry> entries = new ArrayList<>(size);
        int start = (writeIndex - size + slots.length) % slots.length;
        for (int i = 0; i < size; i++) {
            entries.add(slots[(start + i) % slots.length]);
        }
        long oldest = entries.isEmpty() ? 0L : entries.get(0).seq();
        long last = entries.isEmpty() ? 0L : entries.get(entries.size() - 1).seq();
        return new BufferSnapshot(epoch, oldest, last, entries);
    }
}
