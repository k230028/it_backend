package com.kdb.it.common.admin.waslog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.admin.waslog.appender.WasLogBuffer;
import com.kdb.it.common.admin.waslog.client.WasLogPeerClient;
import com.kdb.it.common.admin.waslog.client.WasLogPeerException;
import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import com.kdb.it.common.admin.waslog.dto.WasLogEntry;
import java.time.LocalDateTime;
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
        service = new WasLogService(properties, "SVR1", null, null, null);

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
    @DisplayName("exportLimit은 현재 링버퍼 용량을 반환한다")
    void exportLimit_버퍼용량반환() {
        assertThat(service.exportLimit()).isEqualTo(WasLogBuffer.shared().capacity());
    }

    @Test
    @DisplayName("exportLimit을 상한으로 쓰면 폴링 상한 200을 넘는 항목도 모두 돌려준다")
    void localSnapshot_내보내기상한_버퍼전체() {
        WasLogBuffer.shared().resize(300);
        for (int i = 0; i < 250; i++) {
            WasLogBuffer.shared().add(i + 1, "INFO", "main", "com.kdb.it.A", "메시지" + i, null);
        }

        WasLogDto.Snapshot snapshot =
                service.localSnapshot(
                        new WasLogDto.Query(0L, service.exportLimit(), Set.of(), null, null));

        assertThat(snapshot.entries()).hasSize(250);
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
        WasLogService routing = new WasLogService(properties, "SVR1", failing, null, null);

        WasLogDto.Snapshot snapshot =
                routing.snapshot("SVR2", new WasLogDto.Query(0L, 200, Set.of(), null, null));

        assertThat(snapshot.peerError()).contains("timeout");
        assertThat(snapshot.entries()).isEmpty();
        assertThat(snapshot.instanceId()).isEqualTo("SVR2");
    }

    @Test
    @DisplayName("자기 인스턴스면 로컬 레벨 서비스에 적용한다")
    void applyLevel_로컬적용() {
        LevelOverrideService levelService = mock(LevelOverrideService.class);
        WasLogDto.LevelOverride expected =
                new WasLogDto.LevelOverride(
                        "com.kdb.it", "DEBUG", "INFO", LocalDateTime.of(2026, 8, 20, 11, 0));
        given(levelService.apply("com.kdb.it", "DEBUG", 30)).willReturn(expected);
        WasLogProperties properties = new WasLogProperties(10, Map.of(), "", 1000, 3000);
        WasLogService routing = new WasLogService(properties, "SVR1", null, null, levelService);

        WasLogDto.LevelOverride actual =
                routing.applyLevel(new WasLogDto.LevelRequest("SVR1", "com.kdb.it", "DEBUG", 30));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("설정에 없는 인스턴스면 IllegalArgumentException")
    void applyLevel_알수없는인스턴스() {
        WasLogProperties properties = new WasLogProperties(10, Map.of(), "", 1000, 3000);
        WasLogService routing =
                new WasLogService(properties, "SVR1", null, null, mock(LevelOverrideService.class));
        WasLogDto.LevelRequest request =
                new WasLogDto.LevelRequest("SVR9", "com.kdb.it", "DEBUG", 30);

        assertThatThrownBy(() -> routing.applyLevel(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SVR9");
    }

    @Test
    @DisplayName("피어 레벨 변경 실패는 삼키지 않고 그대로 전파한다")
    void applyLevel_피어실패_전파() {
        WasLogProperties properties =
                new WasLogProperties(10, Map.of("SVR2", "http://svr2:28080"), "s", 1000, 3000);
        WasLogPeerClient failing =
                new WasLogPeerClient() {
                    @Override
                    public WasLogDto.Snapshot fetchSnapshot(
                            String baseUrl, String instanceId, WasLogDto.Query query) {
                        throw new WasLogPeerException("미사용", null);
                    }

                    @Override
                    public WasLogDto.LevelOverride applyLevel(
                            String baseUrl, WasLogDto.LevelRequest request) {
                        throw new WasLogPeerException("SVR2 인스턴스 레벨 변경 실패: timeout", null);
                    }
                };
        WasLogService routing =
                new WasLogService(
                        properties, "SVR1", failing, null, mock(LevelOverrideService.class));
        WasLogDto.LevelRequest request =
                new WasLogDto.LevelRequest("SVR2", "com.kdb.it", "DEBUG", 30);

        // 조회와 달리 여기서 예외를 삼키면 관리자에게 "적용됨"으로 보인다.
        assertThatThrownBy(() -> routing.applyLevel(request))
                .isInstanceOf(WasLogPeerException.class)
                .hasMessageContaining("timeout");
    }

    /** 성공 응답과 호출 인자를 기록하는 피어 대역. */
    private static final class RecordingPeerClient implements WasLogPeerClient {
        private String snapshotBaseUrl;
        private String levelBaseUrl;
        private final WasLogDto.Snapshot snapshot;
        private final WasLogDto.LevelOverride override;

        private RecordingPeerClient(WasLogDto.Snapshot snapshot, WasLogDto.LevelOverride override) {
            this.snapshot = snapshot;
            this.override = override;
        }

        @Override
        public WasLogDto.Snapshot fetchSnapshot(
                String baseUrl, String instanceId, WasLogDto.Query query) {
            this.snapshotBaseUrl = baseUrl;
            return snapshot;
        }

        @Override
        public WasLogDto.LevelOverride applyLevel(String baseUrl, WasLogDto.LevelRequest request) {
            this.levelBaseUrl = baseUrl;
            return override;
        }
    }

    @Test
    @DisplayName("selfInstanceId는 주입된 인스턴스 ID를 그대로 돌려준다")
    void selfInstanceId_주입값반환() {
        assertThat(service.selfInstanceId()).isEqualTo("SVR1");
    }

    @Test
    @DisplayName("instanceId가 null·공백·자기 자신이면 로컬 버퍼를 읽는다")
    void snapshot_로컬경로_세가지입력() {
        WasLogDto.Query query = new WasLogDto.Query(base + 1, 200, Set.of(), null, null);

        for (String instanceId : new String[] {null, "   ", "SVR1"}) {
            WasLogDto.Snapshot snapshot = service.snapshot(instanceId, query);
            assertThat(snapshot.instanceId()).isEqualTo("SVR1");
            assertThat(snapshot.peerError()).isNull();
            assertThat(snapshot.entries()).extracting(WasLogEntry::seq).containsExactly(base + 2);
        }
    }

    @Test
    @DisplayName("설정에 없는 인스턴스를 조회하면 IllegalArgumentException")
    void snapshot_알수없는인스턴스() {
        WasLogDto.Query query = new WasLogDto.Query(0L, 200, Set.of(), null, null);

        assertThatThrownBy(() -> service.snapshot("SVR9", query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SVR9");
    }

    @Test
    @DisplayName("피어 URL이 비어 있으면 호출하지 않고 IllegalArgumentException")
    void snapshot_빈피어URL() {
        WasLogProperties properties =
                new WasLogProperties(10, Map.of("SVR2", "   "), "s", 1000, 3000);
        WasLogService routing = new WasLogService(properties, "SVR1", null, null, null);
        WasLogDto.Query query = new WasLogDto.Query(0L, 200, Set.of(), null, null);

        assertThatThrownBy(() -> routing.snapshot("SVR2", query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SVR2");
    }

    @Test
    @DisplayName("다른 인스턴스 조회는 설정된 피어 URL로 위임하고 응답을 그대로 돌려준다")
    void snapshot_피어위임_성공() {
        WasLogDto.Snapshot peerSnapshot =
                new WasLogDto.Snapshot("SVR2", "epoch-2", List.of(), 7L, false, List.of(), null);
        RecordingPeerClient peer = new RecordingPeerClient(peerSnapshot, null);
        WasLogProperties properties =
                new WasLogProperties(10, Map.of("SVR2", "http://svr2:28080"), "s", 1000, 3000);
        WasLogService routing = new WasLogService(properties, "SVR1", peer, null, null);

        WasLogDto.Snapshot actual =
                routing.snapshot("SVR2", new WasLogDto.Query(0L, 200, Set.of(), null, null));

        assertThat(actual).isEqualTo(peerSnapshot);
        assertThat(peer.snapshotBaseUrl).isEqualTo("http://svr2:28080");
    }

    @Test
    @DisplayName("레벨 변경도 instanceId가 null·공백이면 로컬 서비스로 간다")
    void applyLevel_로컬경로_null과공백() {
        LevelOverrideService levelService = mock(LevelOverrideService.class);
        WasLogDto.LevelOverride expected =
                new WasLogDto.LevelOverride(
                        "com.kdb.it", "DEBUG", "INFO", LocalDateTime.of(2026, 8, 20, 11, 0));
        given(levelService.apply("com.kdb.it", "DEBUG", 30)).willReturn(expected);
        WasLogProperties properties = new WasLogProperties(10, Map.of(), "", 1000, 3000);
        WasLogService routing = new WasLogService(properties, "SVR1", null, null, levelService);

        assertThat(routing.applyLevel(new WasLogDto.LevelRequest(null, "com.kdb.it", "DEBUG", 30)))
                .isEqualTo(expected);
        assertThat(routing.applyLevel(new WasLogDto.LevelRequest("  ", "com.kdb.it", "DEBUG", 30)))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("피어 URL이 비어 있으면 레벨 변경도 호출하지 않고 거부한다")
    void applyLevel_빈피어URL() {
        WasLogProperties properties = new WasLogProperties(10, Map.of("SVR2", ""), "s", 1000, 3000);
        WasLogService routing = new WasLogService(properties, "SVR1", null, null, null);
        WasLogDto.LevelRequest request =
                new WasLogDto.LevelRequest("SVR2", "com.kdb.it", "DEBUG", 30);

        assertThatThrownBy(() -> routing.applyLevel(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SVR2");
    }

    @Test
    @DisplayName("다른 인스턴스의 레벨 변경은 피어 URL로 위임한다")
    void applyLevel_피어위임_성공() {
        WasLogDto.LevelOverride expected =
                new WasLogDto.LevelOverride(
                        "com.kdb.it", "DEBUG", "INFO", LocalDateTime.of(2026, 8, 20, 11, 0));
        RecordingPeerClient peer = new RecordingPeerClient(null, expected);
        WasLogProperties properties =
                new WasLogProperties(10, Map.of("SVR2", "http://svr2:28080"), "s", 1000, 3000);
        WasLogService routing = new WasLogService(properties, "SVR1", peer, null, null);

        WasLogDto.LevelOverride actual =
                routing.applyLevel(new WasLogDto.LevelRequest("SVR2", "com.kdb.it", "DEBUG", 30));

        assertThat(actual).isEqualTo(expected);
        assertThat(peer.levelBaseUrl).isEqualTo("http://svr2:28080");
    }

    @Test
    @DisplayName("설정에 자기 자신이 없으면 목록에 스스로를 더하고 ID로 정렬한다")
    void instances_자기자신보강과정렬() {
        WasLogProperties properties =
                new WasLogProperties(
                        10, Map.of("SVR3", "http://svr3:28080", "SVR2", ""), "s", 1000, 3000);
        WasLogService routing = new WasLogService(properties, "SVR1", null, null, null);

        List<WasLogDto.InstanceInfo> instances = routing.instances();

        assertThat(instances)
                .extracting(WasLogDto.InstanceInfo::id)
                .containsExactly("SVR1", "SVR2", "SVR3");
        assertThat(instances.get(0).self()).isTrue();
        // 피어 URL이 비어 있으면 도달 불가로 표시한다.
        assertThat(instances.get(1).reachable()).isFalse();
        assertThat(instances.get(2).reachable()).isTrue();
    }

    @Test
    @DisplayName("자기 자신은 피어 URL이 없어도 항상 도달 가능으로 본다")
    void instances_자기자신은항상도달가능() {
        WasLogProperties properties = new WasLogProperties(10, Map.of("SVR1", ""), "", 1000, 3000);
        WasLogService routing = new WasLogService(properties, "SVR1", null, null, null);

        List<WasLogDto.InstanceInfo> instances = routing.instances();

        assertThat(instances).hasSize(1);
        assertThat(instances.getFirst().reachable()).isTrue();
    }

    @Test
    @DisplayName("로거·메시지가 없는 항목도 필터에서 예외 없이 걸러진다")
    void 필터_null로거와메시지() {
        WasLogBuffer.shared().resize(10);
        WasLogBuffer.shared().add(9L, "INFO", "main", null, null, null);
        long only = WasLogBuffer.shared().snapshot().oldestSeq();

        // 로거 접두사 필터 — 로거가 null이면 통과하지 않는다.
        assertThat(
                        service.localSnapshot(
                                        new WasLogDto.Query(
                                                only - 1, 200, Set.of(), "com.kdb", null))
                                .entries())
                .isEmpty();
        // 키워드 필터 — 메시지·로거가 모두 null이면 어떤 키워드에도 걸리지 않는다.
        assertThat(
                        service.localSnapshot(
                                        new WasLogDto.Query(only - 1, 200, Set.of(), null, "실패"))
                                .entries())
                .isEmpty();
        // 필터가 없으면 그대로 나온다.
        assertThat(
                        service.localSnapshot(
                                        new WasLogDto.Query(only - 1, 200, Set.of(), "  ", "  "))
                                .entries())
                .hasSize(1);
    }
}
