package com.kdb.it.common.admin.waslog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.admin.waslog.appender.WasLogBuffer;
import com.kdb.it.common.admin.waslog.client.WasLogPeerClient;
import com.kdb.it.common.admin.waslog.client.WasLogPeerException;
import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WasLogServiceTest {

    private WasLogService service;

    /**
     * setUp이 넣은 첫 항목의 seq.
     *
     * <p>{@code WasLogBuffer.shared()}는 프로세스 공용 싱글턴이고 {@code resize()}는 항목만 비울 뿐 seq 카운터는 되돌리지
     * 않는다(의도된 동작 — seq를 되돌리면 같은 {@code bufferEpoch} 안에서 커서가 뒤로 가 클라이언트가 신규 로그를 건너뛴다). 그래서 테스트는 절대
     * seq 값을 박지 않고 이 기준값에서 상대적으로 단언한다.
     */
    private long base;

    @BeforeEach
    void setUp() {
        WasLogBuffer.shared().resize(10);
        WasLogProperties properties =
                new WasLogProperties(10, Map.of("SVR1", "http://svr1:28080"), "", 1000, 3000);
        service = new WasLogService(properties, "SVR1", null, null);

        WasLogBuffer.shared().add(1L, "INFO", "main", "com.kdb.it.A", "정상 처리", null);
        WasLogBuffer.shared().add(2L, "ERROR", "main", "com.kdb.it.B", "저장 실패", "stack");
        WasLogBuffer.shared().add(3L, "WARN", "http-1", "org.hibernate.C", "느린 쿼리", null);

        base = WasLogBuffer.shared().snapshot().oldestSeq();
    }

    @Test
    @DisplayName("afterSeq 이후 항목만 돌려준다")
    void localSnapshot_커서적용() {
        WasLogDto.Snapshot snapshot =
                service.localSnapshot(new WasLogDto.Query(base + 1, 200, Set.of(), null, null));

        assertThat(snapshot.entries()).extracting(WasLogEntry::seq).containsExactly(base + 2);
        assertThat(snapshot.lastSeq()).isEqualTo(base + 2);
        assertThat(snapshot.dropped()).isFalse();
        assertThat(snapshot.instanceId()).isEqualTo("SVR1");
    }

    @Test
    @DisplayName("레벨 필터는 지정한 레벨만 통과시킨다")
    void localSnapshot_레벨필터() {
        WasLogDto.Snapshot snapshot =
                service.localSnapshot(
                        new WasLogDto.Query(0L, 200, Set.of("ERROR", "WARN"), null, null));

        assertThat(snapshot.entries())
                .extracting(WasLogEntry::level)
                .containsExactly("ERROR", "WARN");
    }

    @Test
    @DisplayName("로거 필터는 접두사 일치로 좁힌다")
    void localSnapshot_로거필터() {
        WasLogDto.Snapshot snapshot =
                service.localSnapshot(new WasLogDto.Query(0L, 200, Set.of(), "com.kdb.it", null));

        assertThat(snapshot.entries())
                .extracting(WasLogEntry::logger)
                .containsExactly("com.kdb.it.A", "com.kdb.it.B");
    }

    @Test
    @DisplayName("키워드는 메시지·로거에 대소문자 무시 부분일치로 적용한다")
    void localSnapshot_키워드필터() {
        WasLogDto.Snapshot snapshot =
                service.localSnapshot(new WasLogDto.Query(0L, 200, Set.of(), null, "실패"));

        assertThat(snapshot.entries()).extracting(WasLogEntry::message).containsExactly("저장 실패");
    }

    @Test
    @DisplayName("커서 이후 항목이 버퍼에서 밀려났으면 dropped를 세운다")
    void localSnapshot_밀림감지() {
        WasLogBuffer.shared().resize(2);
        WasLogBuffer.shared().add(1L, "INFO", "main", "com.kdb.it.A", "하나", null);
        WasLogBuffer.shared().add(2L, "INFO", "main", "com.kdb.it.A", "둘", null);
        WasLogBuffer.shared().add(3L, "INFO", "main", "com.kdb.it.A", "셋", null);

        // 남은 것은 '둘'·'셋'이고 '하나'는 밀려났다. 커서가 '하나'보다 앞이어야 실제로 건너뛴 항목이 생긴다.
        long missedSeq = WasLogBuffer.shared().snapshot().oldestSeq() - 2;

        WasLogDto.Snapshot snapshot =
                service.localSnapshot(new WasLogDto.Query(missedSeq, 200, Set.of(), null, null));

        assertThat(snapshot.dropped()).isTrue();
    }

    @Test
    @DisplayName("limit은 200을 넘지 못하고 최신 항목을 남긴다")
    void localSnapshot_limit상한() {
        WasLogDto.Snapshot snapshot =
                service.localSnapshot(new WasLogDto.Query(0L, 2, Set.of(), null, null));

        assertThat(snapshot.entries())
                .extracting(WasLogEntry::seq)
                .containsExactly(base + 1, base + 2);
        assertThat(snapshot.lastSeq()).isEqualTo(base + 2);
    }

    @Test
    @DisplayName("조회 상한 때문에 오래된 항목을 버리면 dropped를 세운다")
    void localSnapshot_상한초과_dropped() {
        WasLogDto.Snapshot snapshot =
                service.localSnapshot(new WasLogDto.Query(0L, 2, Set.of(), null, null));

        assertThat(snapshot.dropped()).isTrue();
    }

    @Test
    @DisplayName("상한에 걸리지 않으면 dropped를 세우지 않는다")
    void localSnapshot_상한미달_dropped없음() {
        WasLogDto.Snapshot snapshot =
                service.localSnapshot(new WasLogDto.Query(0L, 200, Set.of(), null, null));

        assertThat(snapshot.entries()).hasSize(3);
        assertThat(snapshot.dropped()).isFalse();
    }

    @Test
    @DisplayName("허용되지 않은 레벨이면 IllegalArgumentException을 던진다")
    void localSnapshot_잘못된레벨() {
        WasLogDto.Query query = new WasLogDto.Query(0L, 200, Set.of("FATAL"), null, null);

        assertThatThrownBy(() -> service.localSnapshot(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FATAL");
    }

    @Test
    @DisplayName("인스턴스 목록은 자기 자신을 self로 표시한다")
    void instances_self표시() {
        List<WasLogDto.InstanceInfo> instances = service.instances();

        assertThat(instances).extracting(WasLogDto.InstanceInfo::id).containsExactly("SVR1");
        assertThat(instances.getFirst().self()).isTrue();
    }

    @Test
    @DisplayName("피어 호출이 실패하면 예외 대신 peerError로 표면화한다")
    void snapshot_피어실패_표면화() {
        WasLogProperties properties =
                new WasLogProperties(10, Map.of("SVR2", "http://svr2:28080"), "s", 1000, 3000);
        WasLogPeerClient failing =
                new WasLogPeerClient() {
                    @Override
                    public WasLogDto.Snapshot fetchSnapshot(
                            String baseUrl, String instanceId, WasLogDto.Query query) {
                        throw new WasLogPeerException("SVR2 인스턴스 조회 실패: timeout", null);
                    }

                    @Override
                    public WasLogDto.LevelOverride applyLevel(
                            String baseUrl, WasLogDto.LevelRequest request) {
                        throw new WasLogPeerException("미사용", null);
                    }
                };
        WasLogService routing = new WasLogService(properties, "SVR1", failing, null);

        WasLogDto.Snapshot snapshot =
                routing.snapshot("SVR2", new WasLogDto.Query(0L, 200, Set.of(), null, null));

        assertThat(snapshot.peerError()).contains("timeout");
        assertThat(snapshot.entries()).isEmpty();
        assertThat(snapshot.instanceId()).isEqualTo("SVR2");
    }
}
