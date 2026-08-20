package com.kdb.it.common.admin.waslog.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
    @DisplayName("피어가 요청과 다른 instanceId로 응답하면 WasLogPeerException을 던진다")
    void fetchSnapshot_인스턴스ID불일치() {
        // app.server.instance-id 기본값이 SVR1이라, SVR2로 설정해야 할 피어가 설정 실수로 SVR1인 채
        // 기동되면 "SVR2를 호출했는데 SVR1이 응답"하는 상황이 조용히 통과한다. 응답 본문의 "가
        // 들어간 instanceId로 Content-Disposition 헤더 주입을 시도하는 컴프로마이즈된 피어도 이
        // 등호 비교로 걸러진다.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + "/internal/was-logs/snapshot"))
                .andRespond(
                        withSuccess(
                                """
                                {"instanceId":"SVR1","bufferEpoch":"e1","entries":[],
                                 "lastSeq":5,"dropped":false,"levelOverrides":[],"peerError":null}
                                """,
                                MediaType.APPLICATION_JSON));

        WasLogPeerClient client = new DefaultWasLogPeerClient(builder.build(), properties);

        assertThatThrownBy(() -> client.fetchSnapshot(PEER_URL, "SVR2", query))
                .isInstanceOf(WasLogPeerException.class)
                .hasMessageContaining("SVR2")
                .hasMessageContaining("SVR1");
    }

    @Test
    @DisplayName("피어가 위조한 instanceId는 불일치 메시지에 심기 전 정화·절단된다")
    void fetchSnapshot_인스턴스ID불일치_위조값정화() {
        // 이 메시지는 WasLogController.handlePeerFailure를 거쳐 502 본문(브라우저)과 애플리케이션
        // 로그 파일 양쪽에 도달한다. 컴프로마이즈된 피어가 개행·따옴표·매우 긴 문자열을 instanceId에
        // 심어도 안전한 문자집합(A-Za-z0-9_-)과 64자 상한으로 걸러지는지 검증한다.
        String maliciousInstanceId = "S\"VR<script>\n".repeat(10);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + "/internal/was-logs/snapshot"))
                .andRespond(
                        withSuccess(
                                """
                                {"instanceId":"%s","bufferEpoch":"e1","entries":[],
                                 "lastSeq":5,"dropped":false,"levelOverrides":[],"peerError":null}
                                """
                                        .formatted(
                                                maliciousInstanceId
                                                        .replace("\"", "\\\"")
                                                        .replace("\n", "\\n")),
                                MediaType.APPLICATION_JSON));

        WasLogPeerClient client = new DefaultWasLogPeerClient(builder.build(), properties);

        assertThatThrownBy(() -> client.fetchSnapshot(PEER_URL, "SVR2", query))
                .isInstanceOf(WasLogPeerException.class)
                .hasMessageNotContaining("\n")
                .hasMessageNotContaining("\"")
                .hasMessageNotContaining("<script>")
                .satisfies(
                        e ->
                                assertThat(e.getMessage().length())
                                        .isLessThan(maliciousInstanceId.length()));
    }

    @Test
    @DisplayName("2xx인데 본문이 비면 WasLogPeerException을 던진다")
    void fetchSnapshot_빈본문() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + "/internal/was-logs/snapshot"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        WasLogPeerClient client = new DefaultWasLogPeerClient(builder.build(), properties);

        assertThatThrownBy(() -> client.fetchSnapshot(PEER_URL, "SVR2", query))
                .isInstanceOf(WasLogPeerException.class)
                .hasMessageContaining("본문");
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

    @Test
    @DisplayName("레벨 변경이 2xx인데 본문이 비면 WasLogPeerException을 던진다")
    void applyLevel_빈본문() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + "/internal/was-logs/level"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        WasLogPeerClient client = new DefaultWasLogPeerClient(builder.build(), properties);
        WasLogDto.LevelRequest request =
                new WasLogDto.LevelRequest("SVR2", "com.kdb.it", "DEBUG", 30);

        assertThatThrownBy(() -> client.applyLevel(PEER_URL, request))
                .isInstanceOf(WasLogPeerException.class)
                .hasMessageContaining("본문");
    }
}
