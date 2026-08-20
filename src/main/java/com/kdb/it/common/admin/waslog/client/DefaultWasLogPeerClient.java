package com.kdb.it.common.admin.waslog.client;

import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link WasLogPeerClient}의 RestClient 구현. */
@Component
public class DefaultWasLogPeerClient implements WasLogPeerClient {

    private static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final WasLogProperties properties;

    public DefaultWasLogPeerClient(RestClient wasLogPeerRestClient, WasLogProperties properties) {
        this.restClient = wasLogPeerRestClient;
        this.properties = properties;
    }

    @Override
    public WasLogDto.Snapshot fetchSnapshot(
            String baseUrl, String instanceId, WasLogDto.Query query) {
        try {
            return restClient
                    .post()
                    .uri(baseUrl + "/internal/was-logs/snapshot")
                    .header(TOKEN_HEADER, properties.internalSecret())
                    .body(query)
                    .retrieve()
                    .body(WasLogDto.Snapshot.class);
        } catch (RestClientException e) {
            throw new WasLogPeerException(instanceId + " 인스턴스 조회 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public WasLogDto.LevelOverride applyLevel(String baseUrl, WasLogDto.LevelRequest request) {
        try {
            return restClient
                    .post()
                    .uri(baseUrl + "/internal/was-logs/level")
                    .header(TOKEN_HEADER, properties.internalSecret())
                    .body(request)
                    .retrieve()
                    .body(WasLogDto.LevelOverride.class);
        } catch (RestClientException e) {
            throw new WasLogPeerException(
                    request.instanceId() + " 인스턴스 레벨 변경 실패: " + e.getMessage(), e);
        }
    }
}
