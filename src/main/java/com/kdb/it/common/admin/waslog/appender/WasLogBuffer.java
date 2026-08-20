package com.kdb.it.common.admin.waslog.appender;

import ch.qos.logback.core.Context;
import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;

/**
 * WAS 로그 링버퍼.
 *
 * <p>logback Appender는 Spring 컨텍스트보다 먼저 기동하므로 빈으로 만들 수 없다. 그래서 적재({@link RingBufferAppender})와
 * 조회(서비스 계층)가 프로세스 공용 저장소를 통해 만난다.
 *
 * <h2>공용 저장소를 static 필드에 두지 않는 이유</h2>
 *
 * <p>이 클래스가 <b>두 클래스로더에 각각 로드되는 환경</b>이 실재한다. logback은 appender 클래스를 logback 자신을 로드한 클래스로더로
 * 찾는데(=애플리케이션 클래스로더), {@code spring-boot-devtools}가 붙은 IDE 기동에서는 프로젝트 클래스가 {@code
 * RestartClassLoader}로 다시 로드된다. 그러면 appender는 A 클래스로더의 {@code static} 버퍼에 쌓고 서비스는 B 클래스로더의 <b>영원히
 * 비어 있는</b> 버퍼를 읽어, 화면이 오류 하나 없이 "로그 0건"만 보여준다. 외부 WAS가 logback을 공용 lib에 두는 배치에서도 같은 갈림이 생긴다.
 *
 * <p>그래서 공용 저장소를 logback {@link Context}의 object map에 두고, <b>JDK 타입만</b>으로 구성한다({@link ArrayDeque},
 * {@link AtomicLong}, {@code Object[]} …). 저장소에 이 패키지의 타입을 하나라도 넣으면 반대편 클래스로더에서 {@code
 * ClassCastException}이 나므로, 항목도 {@link #ENTRY_LEN}칸짜리 {@code Object[]}로 담고 조회 시점에 각자의 {@link
 * WasLogEntry}로 되살린다. logback {@code Context}·slf4j {@code LoggerFactory}는 양쪽 클래스로더가 부모 위임으로 같은
 * 클래스를 보므로 이 경계가 성립한다.
 *
 * <p>모든 공개 메서드는 저장소 큐를 모니터로 하는 동기화 구간이다. 적재는 로깅 경로에서 호출되므로 O(1) 작업만 한다.
 */
public final class WasLogBuffer {

    /** 기본 용량. 설정으로 덮어쓸 수 있다. */
    public static final int DEFAULT_CAPACITY = 2000;

    /** logback {@link Context} object map에서 공용 저장소를 찾는 키. */
    static final String STORE_KEY = "com.kdb.it.wasLog.store";

    /** 저장소 배열 칸 — 0: 항목 큐(모니터 겸용), 1: seq 채번, 2: 용량, 3: 버퍼 세대 */
    private static final int STORE_ENTRIES = 0;

    private static final int STORE_SEQ = 1;
    private static final int STORE_CAPACITY = 2;
    private static final int STORE_EPOCH = 3;
    private static final int STORE_LEN = 4;

    /** 항목 배열 칸 — seq, timestamp, level, thread, logger, message, throwable */
    private static final int ENTRY_LEN = 7;

    /**
     * logback {@link Context}를 얻을 수 없을 때 쓰는 프로세스 지역 저장소.
     *
     * <p>logback이 아닌 SLF4J 바인딩이 물려 있는 환경에서 조회가 예외로 죽지 않게 하는 최후 수단이다. 이 경로에서는 적재·조회가 같은 클래스로더일 때만 서로
     * 보인다.
     */
    private static final Object[] FALLBACK_STORE = newStore(DEFAULT_CAPACITY);

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

    private final Object[] store;

    /** 공용 저장소에 붙지 않는 독립 버퍼. 테스트가 다른 테스트의 적재에 오염되지 않도록 쓴다. */
    WasLogBuffer(int capacity) {
        this.store = newStore(capacity);
    }

    private WasLogBuffer(Object[] store) {
        this.store = store;
    }

    /**
     * 프로세스 공용 버퍼.
     *
     * <p>SLF4J 바인딩이 logback이면 그 {@link Context}의 공용 저장소에, 아니면 프로세스 지역 저장소에 붙는다.
     */
    public static WasLogBuffer shared() {
        Object factory = LoggerFactory.getILoggerFactory();
        return factory instanceof Context context
                ? attachedTo(context)
                : new WasLogBuffer(FALLBACK_STORE);
    }

    /**
     * 지정한 logback {@link Context}의 공용 저장소에 붙은 버퍼.
     *
     * <p>Appender는 자신의 {@code getContext()}를 넘긴다 — 기동 시점이 SLF4J 초기화 도중이라 {@link #shared()}가 쓰는
     * {@code LoggerFactory.getILoggerFactory()}로는 아직 진짜 {@code LoggerContext}를 얻을 수 없다.
     *
     * @param context null이면 프로세스 지역 저장소에 붙는다
     */
    public static WasLogBuffer attachedTo(Context context) {
        if (context == null) return new WasLogBuffer(FALLBACK_STORE);
        // putObject는 원자적이지 않다. 두 클래스로더의 코드가 동시에 들어와도 저장소가 하나만 만들어지도록 잠근다.
        synchronized (context.getConfigurationLock()) {
            Object existing = context.getObject(STORE_KEY);
            if (existing instanceof Object[] store && store.length == STORE_LEN) {
                return new WasLogBuffer(store);
            }
            Object[] store = newStore(DEFAULT_CAPACITY);
            context.putObject(STORE_KEY, store);
            return new WasLogBuffer(store);
        }
    }

    private static Object[] newStore(int capacity) {
        Object[] store = new Object[STORE_LEN];
        store[STORE_ENTRIES] = new ArrayDeque<Object[]>();
        store[STORE_SEQ] = new AtomicLong();
        store[STORE_CAPACITY] = new AtomicInteger(Math.max(1, capacity));
        store[STORE_EPOCH] = UUID.randomUUID().toString();
        return store;
    }

    @SuppressWarnings("unchecked")
    private ArrayDeque<Object[]> entries() {
        return (ArrayDeque<Object[]>) store[STORE_ENTRIES];
    }

    private AtomicLong seq() {
        return (AtomicLong) store[STORE_SEQ];
    }

    private AtomicInteger capacityRef() {
        return (AtomicInteger) store[STORE_CAPACITY];
    }

    /** 현재 용량. */
    public int capacity() {
        return capacityRef().get();
    }

    /**
     * 용량을 바꾸고 버퍼를 비운다. Appender 기동 시 1회만 호출한다.
     *
     * @param capacity 1 미만이면 1로 보정
     */
    public void resize(int capacity) {
        ArrayDeque<Object[]> entries = entries();
        synchronized (entries) {
            capacityRef().set(Math.max(1, capacity));
            entries.clear();
        }
    }

    /** 로그 한 줄을 적재한다. 용량 초과 시 가장 오래된 항목을 덮어쓴다. */
    public void add(
            long timestamp,
            String level,
            String thread,
            String logger,
            String message,
            String throwable) {
        ArrayDeque<Object[]> entries = entries();
        synchronized (entries) {
            Object[] row = new Object[ENTRY_LEN];
            row[0] = seq().incrementAndGet();
            row[1] = timestamp;
            row[2] = level;
            row[3] = thread;
            row[4] = logger;
            row[5] = message;
            row[6] = throwable;
            while (entries.size() >= capacityRef().get()) {
                entries.pollFirst();
            }
            entries.addLast(row);
        }
    }

    /** 현재 보관 중인 항목을 seq 오름차순 복사본으로 반환한다. */
    public BufferSnapshot snapshot() {
        ArrayDeque<Object[]> entries = entries();
        Object[][] rows;
        synchronized (entries) {
            rows = entries.toArray(new Object[0][]);
        }
        List<WasLogEntry> result = new ArrayList<>(rows.length);
        for (Object[] row : rows) {
            result.add(
                    new WasLogEntry(
                            (Long) row[0],
                            (Long) row[1],
                            (String) row[2],
                            (String) row[3],
                            (String) row[4],
                            (String) row[5],
                            (String) row[6]));
        }
        long oldest = result.isEmpty() ? 0L : result.get(0).seq();
        long last = result.isEmpty() ? 0L : result.get(result.size() - 1).seq();
        return new BufferSnapshot((String) store[STORE_EPOCH], oldest, last, result);
    }
}
