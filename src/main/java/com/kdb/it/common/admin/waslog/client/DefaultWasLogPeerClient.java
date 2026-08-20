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
        // 피어가 요청받은 것과 다른 instanceId로 응답할 수 있다 — app.server.instance-id 기본값이
        // SVR1이라, 설정 실수로 SVR2가 SVR1로 기동되면 SVR2 요청에 SVR1 버퍼가 SVR1 라벨로 조용히
        // 돌아온다. 컴프로마이즈된 피어가 "를 심어 다운로드 응답 헤더에 값을 주입하는 경로도 여기서
        // 막힌다 — 등호 비교라 주입 문자가 있으면 그대로 불일치로 걸린다.
        if (!instanceId.equals(body.instanceId())) {
            throw new WasLogPeerException(
                    instanceId + " 인스턴스에 요청했으나 " + body.instanceId() + " 응답을 받았습니다.", null);
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
