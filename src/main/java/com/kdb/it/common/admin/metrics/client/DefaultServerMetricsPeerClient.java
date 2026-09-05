package com.kdb.it.common.admin.metrics.client;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link ServerMetricsPeerClient}의 RestClient 구현. WAS 로그 뷰어와 같은 피어 RestClient·공유 비밀값을 쓴다. */
@Component
public class DefaultServerMetricsPeerClient implements ServerMetricsPeerClient {

    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final int MAX_ECHOED_INSTANCE_ID_LENGTH = 64;

    private final RestClient restClient;
    private final WasLogProperties properties;

    public DefaultServerMetricsPeerClient(
            @Qualifier("wasLogPeerRestClient") RestClient wasLogPeerRestClient,
            WasLogProperties properties) {
        this.restClient = wasLogPeerRestClient;
        this.properties = properties;
    }

    @Override
    public ServerMetricsDto.InstanceMetrics fetch(String baseUrl, String instanceId) {
        ServerMetricsDto.InstanceMetrics body;
        try {
            body =
                    restClient
                            .get()
                            .uri(baseUrl + "/internal/server-metrics/snapshot")
                            .header(TOKEN_HEADER, properties.internalSecret())
                            .retrieve()
                            .body(ServerMetricsDto.InstanceMetrics.class);
        } catch (RestClientException e) {
            throw new ServerMetricsPeerException(instanceId + " 인스턴스 조회 실패: " + e.getMessage(), e);
        }
        // 2xx인데 본문이 비면 body()가 null을 준다. 그대로 흘리면 화면이 "수집 전"으로 읽어 실패가 감춰진다.
        if (body == null) {
            throw new ServerMetricsPeerException(instanceId + " 인스턴스 응답 본문이 비어 있습니다.", null);
        }
        // 설정 실수로 두 인스턴스가 같은 instance-id로 기동되면 피어 요청에 자기 버퍼가 다른 라벨로 돌아온다.
        if (!instanceId.equals(body.instanceId())) {
            throw new ServerMetricsPeerException(
                    instanceId
                            + " 인스턴스에 요청했으나 "
                            + sanitizeEchoedInstanceId(body.instanceId())
                            + " 응답을 받았습니다.",
                    null);
        }
        return body;
    }

    /** 피어 원본 문자열을 메시지에 넣기 전 안전한 문자집합으로 제한하고 길이를 자른다. */
    private static String sanitizeEchoedInstanceId(String rawInstanceId) {
        if (rawInstanceId == null) return "null";
        String truncated =
                rawInstanceId.length() > MAX_ECHOED_INSTANCE_ID_LENGTH
                        ? rawInstanceId.substring(0, MAX_ECHOED_INSTANCE_ID_LENGTH)
                        : rawInstanceId;
        return truncated.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
