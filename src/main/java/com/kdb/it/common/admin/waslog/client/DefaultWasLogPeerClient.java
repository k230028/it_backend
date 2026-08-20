package com.kdb.it.common.admin.waslog.client;

import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link WasLogPeerClient}의 RestClient 구현. */
@Component
public class DefaultWasLogPeerClient implements WasLogPeerClient {

    private static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final WasLogProperties properties;

    public DefaultWasLogPeerClient(
            @Qualifier("wasLogPeerRestClient") RestClient wasLogPeerRestClient,
            WasLogProperties properties) {
        this.restClient = wasLogPeerRestClient;
        this.properties = properties;
    }

    @Override
    public WasLogDto.Snapshot fetchSnapshot(
            String baseUrl, String instanceId, WasLogDto.Query query) {
        WasLogDto.Snapshot body;
        try {
            body =
                    restClient
                            .post()
                            .uri(baseUrl + "/internal/was-logs/snapshot")
                            .header(TOKEN_HEADER, properties.internalSecret())
                            .body(query)
                            .retrieve()
                            .body(WasLogDto.Snapshot.class);
        } catch (RestClientException e) {
            throw new WasLogPeerException(instanceId + " 인스턴스 조회 실패: " + e.getMessage(), e);
        }
        // 2xx인데 본문이 비면 body()가 예외 없이 null을 준다. 그대로 흘리면 화면이 "로그 없음"으로
        // 읽어 실패가 감춰지므로, 호출 실패로 승격해 peerError 경로를 타게 한다.
        if (body == null) {
            throw new WasLogPeerException(instanceId + " 인스턴스 응답 본문이 비어 있습니다.", null);
        }
        return body;
    }

    @Override
    public WasLogDto.LevelOverride applyLevel(String baseUrl, WasLogDto.LevelRequest request) {
        WasLogDto.LevelOverride body;
        try {
            body =
                    restClient
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
        if (body == null) {
            throw new WasLogPeerException(
                    request.instanceId() + " 인스턴스 레벨 변경 응답 본문이 비어 있습니다.", null);
        }
        return body;
    }
}
