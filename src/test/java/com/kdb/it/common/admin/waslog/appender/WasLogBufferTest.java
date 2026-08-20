package com.kdb.it.common.admin.waslog.appender;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WasLogBufferTest {

    private WasLogBuffer newBuffer(int capacity) {
        WasLogBuffer buffer = new WasLogBuffer(capacity);
        return buffer;
    }

    @Test
    @DisplayName("적재한 순서대로 seq가 1부터 단조 증가한다")
    void add_seq단조증가() {
        WasLogBuffer buffer = newBuffer(10);

        buffer.add(1L, "INFO", "main", "com.kdb.it.A", "첫번째", null);
        buffer.add(2L, "WARN", "main", "com.kdb.it.B", "두번째", null);

        WasLogBuffer.BufferSnapshot snapshot = buffer.snapshot();
        assertThat(snapshot.entries()).extracting(WasLogEntry::seq).containsExactly(1L, 2L);
        assertThat(snapshot.oldestSeq()).isEqualTo(1L);
        assertThat(snapshot.lastSeq()).isEqualTo(2L);
    }

    @Test
    @DisplayName("용량을 넘으면 가장 오래된 항목부터 버린다")
    void add_용량초과_오래된항목폐기() {
        WasLogBuffer buffer = newBuffer(2);

        buffer.add(1L, "INFO", "main", "com.kdb.it.A", "하나", null);
        buffer.add(2L, "INFO", "main", "com.kdb.it.A", "둘", null);
        buffer.add(3L, "INFO", "main", "com.kdb.it.A", "셋", null);

        WasLogBuffer.BufferSnapshot snapshot = buffer.snapshot();
        assertThat(snapshot.entries()).extracting(WasLogEntry::message).containsExactly("둘", "셋");
        assertThat(snapshot.oldestSeq()).isEqualTo(2L);
        assertThat(snapshot.lastSeq()).isEqualTo(3L);
    }

    @Test
    @DisplayName("비어 있으면 oldestSeq와 lastSeq가 0이다")
    void snapshot_빈버퍼() {
        WasLogBuffer.BufferSnapshot snapshot = newBuffer(5).snapshot();

        assertThat(snapshot.entries()).isEmpty();
        assertThat(snapshot.oldestSeq()).isZero();
        assertThat(snapshot.lastSeq()).isZero();
        assertThat(snapshot.epoch()).isNotBlank();
    }

    @Test
    @DisplayName("resize는 버퍼를 비우고 용량을 바꾼다")
    void resize_버퍼초기화() {
        WasLogBuffer buffer = newBuffer(2);
        buffer.add(1L, "INFO", "main", "com.kdb.it.A", "하나", null);

        buffer.resize(5);

        WasLogBuffer.BufferSnapshot snapshot = buffer.snapshot();
        assertThat(snapshot.entries()).isEmpty();
        assertThat(buffer.capacity()).isEqualTo(5);
    }
}
