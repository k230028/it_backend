package com.kdb.it.common.admin.waslog.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WasLogPeerClientTest {

    private static final String PEER_URL = "http://svr2:28080";

    private final WasLogProperties properties =
            new WasLogProperties(2000, Map.of(), "s3cret", 1000, 3000);

    private final WasLogDto.Query query = new WasLogDto.Query(0L, 200, Set.of(), null, null);

    @Test
    @DisplayName("피어 응답을 그대로 반환하고 내부 토큰 헤더를 보낸다")
    void fetchSnapshot_정상() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + "/internal/was-logs/snapshot"))
                .andExpect(method(POST))
                .andExpect(header("X-Internal-Token", "s3cret"))
                .andRespond(
                        withSuccess(
                                """
                                {"instanceId":"SVR2","bufferEpoch":"e2","entries":[],
                                 "lastSeq":5,"dropped":false,"levelOverrides":[],"peerError":null}
                                """,
                                MediaType.APPLICATION_JSON));

        WasLogPeerClient client = new DefaultWasLogPeerClient(builder.build(), properties);
        WasLogDto.Snapshot snapshot = client.fetchSnapshot(PEER_URL, "SVR2", query);

        server.verify();
        assertThat(snapshot.instanceId()).isEqualTo("SVR2");
        assertThat(snapshot.lastSeq()).isEqualTo(5L);
    }

    @Test
    @DisplayName("피어가 5xx면 WasLogPeerException을 던지고 인스턴스ID를 메시지에 담는다")
    void fetchSnapshot_서버오류() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + "/internal/was-logs/snapshot"))
                .andRespond(withServerError());

        WasLogPeerClient client = new DefaultWasLogPeerClient(builder.build(), properties);

        assertThatThrownBy(() -> client.fetchSnapshot(PEER_URL, "SVR2", query))
                .isInstanceOf(WasLogPeerException.class)
                .hasMessageContaining("SVR2");
    }

    @Test
    @DisplayName("레벨 변경도 같은 토큰 헤더로 위임한다")
    void applyLevel_정상() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + "/internal/was-logs/level"))
                .andExpect(method(POST))
                .andExpect(header("X-Internal-Token", "s3cret"))
                .andRespond(
                        withSuccess(
                                """
                                {"logger":"com.kdb.it","level":"DEBUG","previousLevel":"INFO",
                                 "expiresAt":"2026-08-20T11:00:00"}
                                """,
                                MediaType.APPLICATION_JSON));

        WasLogPeerClient client = new DefaultWasLogPeerClient(builder.build(), properties);
        WasLogDto.LevelOverride override =
                client.applyLevel(
                        PEER_URL, new WasLogDto.LevelRequest("SVR2", "com.kdb.it", "DEBUG", 30));

        server.verify();
        assertThat(override.logger()).isEqualTo("com.kdb.it");
        assertThat(override.previousLevel()).isEqualTo("INFO");
    }
}
