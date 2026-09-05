package com.kdb.it.common.admin.metrics.service;

import com.kdb.it.common.admin.metrics.client.ServerMetricsPeerClient;
import com.kdb.it.common.admin.metrics.client.ServerMetricsPeerException;
import com.kdb.it.common.admin.metrics.config.ServerMetricsProperties;
import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 서버 자원 사용량 조회. 로컬 링버퍼와 설정된 피어 인스턴스의 지표를 한 응답으로 합친다.
 *
 * <p>피어 목록은 {@code app.was-log.peers}를 공유한다. 피어 조회 실패는 빈 이력으로 위장하지 않고 {@code peerError}로 싣는다.
 */
@Service
public class ServerMetricsService {

    private final String selfInstanceId;
    private final ServerMetricsProperties properties;
    private final WasLogProperties peerProperties;
    private final ServerMetricsHistory history;
    private final ServerMetricsCollector collector;
    private final ServerMetricsPeerClient peerClient;
    private final Clock clock;

    @Autowired
    public ServerMetricsService(
            @Value("${app.server.instance-id:SVR1}") String selfInstanceId,
            ServerMetricsProperties properties,
            WasLogProperties peerProperties,
            ServerMetricsHistory history,
            ServerMetricsCollector collector,
            ServerMetricsPeerClient peerClient) {
        this(
                selfInstanceId,
                properties,
                peerProperties,
                history,
                collector,
                peerClient,
                Clock.systemUTC());
    }

    ServerMetricsService(
            String selfInstanceId,
            ServerMetricsProperties properties,
            WasLogProperties peerProperties,
            ServerMetricsHistory history,
            ServerMetricsCollector collector,
            ServerMetricsPeerClient peerClient,
            Clock clock) {
        this.selfInstanceId = selfInstanceId;
        this.properties = properties;
        this.peerProperties = peerProperties;
        this.history = history;
        this.collector = collector;
        this.peerClient = peerClient;
        this.clock = clock;
    }

    /**
     * 로컬 인스턴스의 최신 샘플과 이력. 스케줄러가 아직 돌기 전이면 즉시 한 번 샘플링한다.
     *
     * @return 자기 자신({@code self=true})의 지표
     */
    public ServerMetricsDto.InstanceMetrics local() {
        ServerMetricsDto.Sample latest = history.latest().orElseGet(collector::sampleNow);
        return new ServerMetricsDto.InstanceMetrics(
                selfInstanceId, true, latest, history.points(), null);
    }

    /**
     * 자기 자신과 모든 피어의 지표를 인스턴스ID 오름차순으로 모은 대시보드 응답.
     *
     * @return 응답. 자기 자신은 항상 포함되고 피어 실패는 {@code peerError}로 표면화된다
     */
    public ServerMetricsDto.Response aggregate() {
        List<ServerMetricsDto.InstanceMetrics> instances = new ArrayList<>();
        instances.add(local());
        for (var entry : peerProperties.peers().entrySet()) {
            String instanceId = entry.getKey();
            String baseUrl = entry.getValue();
            if (instanceId.equals(selfInstanceId) || baseUrl == null || baseUrl.isBlank()) {
                continue;
            }
            instances.add(fetchPeer(instanceId, baseUrl));
        }
        instances.sort(Comparator.comparing(ServerMetricsDto.InstanceMetrics::instanceId));
        return new ServerMetricsDto.Response(
                Instant.now(clock),
                (int) (properties.sampleIntervalMs() / 1000),
                properties.historyMinutes(),
                instances);
    }

    private ServerMetricsDto.InstanceMetrics fetchPeer(String instanceId, String baseUrl) {
        try {
            ServerMetricsDto.InstanceMetrics peer = peerClient.fetch(baseUrl, instanceId);
            // 피어는 자기 기준으로 self=true를 돌려주므로 이 응답에서는 false로 바로잡는다.
            return new ServerMetricsDto.InstanceMetrics(
                    peer.instanceId(), false, peer.latest(), peer.history(), null);
        } catch (ServerMetricsPeerException e) {
            return new ServerMetricsDto.InstanceMetrics(
                    instanceId, false, null, List.of(), e.getMessage());
        }
    }
}
