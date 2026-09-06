package com.kdb.it.common.admin.metrics.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import com.kdb.it.common.admin.waslog.config.WasLogProperties;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 피어 인스턴스 자원 사용량 조회 클라이언트 검증.
 *
 * <p>WAS 로그 뷰어와 같은 피어 RestClient·공유 비밀값을 쓰므로 {@code WasLogPeerClientTest}와 같은 방식으로 검증한다.
 */
class ServerMetricsPeerClientTest {

    private static final String PEER_URL = "http://svr2:28080";
    private static final String SNAPSHOT_PATH = "/internal/server-metrics/snapshot";

    private final WasLogProperties properties =
            new WasLogProperties(2000, Map.of(), "s3cret", 1000, 3000);

    private ServerMetricsPeerClient client(RestClient.Builder builder) {
        return new DefaultServerMetricsPeerClient(builder.build(), properties);
    }

    @Test
    @DisplayName("피어 응답을 그대로 반환하고 내부 토큰 헤더를 보낸다")
    void fetch_정상() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + SNAPSHOT_PATH))
                .andExpect(method(GET))
                .andExpect(header("X-Internal-Token", "s3cret"))
                .andRespond(
                        withSuccess(
                                """
                                {"instanceId":"SVR2","self":false,"latest":null,
                                 "history":[],"peerError":null}
                                """,
                                MediaType.APPLICATION_JSON));

        ServerMetricsDto.InstanceMetrics metrics = client(builder).fetch(PEER_URL, "SVR2");

        server.verify();
        assertThat(metrics.instanceId()).isEqualTo("SVR2");
        assertThat(metrics.self()).isFalse();
        assertThat(metrics.history()).isEmpty();
    }

    @Test
    @DisplayName("피어가 5xx면 ServerMetricsPeerException을 던지고 인스턴스ID를 메시지에 담는다")
    void fetch_서버오류() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + SNAPSHOT_PATH)).andRespond(withServerError());

        assertThatThrownBy(() -> client(builder).fetch(PEER_URL, "SVR2"))
                .isInstanceOf(ServerMetricsPeerException.class)
                .hasMessageContaining("SVR2");
    }

    @Test
    @DisplayName("2xx인데 본문이 비면 ServerMetricsPeerException을 던진다")
    void fetch_빈본문() {
        // 본문 없음을 그대로 흘리면 화면이 "아직 수집 전"으로 읽어 조회 실패가 감춰진다.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + SNAPSHOT_PATH))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatThrownBy(() -> client(builder).fetch(PEER_URL, "SVR2"))
                .isInstanceOf(ServerMetricsPeerException.class)
                .hasMessageContaining("본문");
    }

    @Test
    @DisplayName("피어가 요청과 다른 instanceId로 응답하면 ServerMetricsPeerException을 던진다")
    void fetch_인스턴스ID불일치() {
        // 설정 실수로 두 인스턴스가 같은 instance-id로 기동되면 피어 요청에 자기 지표가 돌아온다.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + SNAPSHOT_PATH))
                .andRespond(
                        withSuccess(
                                """
                                {"instanceId":"SVR1","self":false,"latest":null,
                                 "history":[],"peerError":null}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(builder).fetch(PEER_URL, "SVR2"))
                .isInstanceOf(ServerMetricsPeerException.class)
                .hasMessageContaining("SVR2")
                .hasMessageContaining("SVR1");
    }

    @Test
    @DisplayName("피어가 위조한 instanceId는 불일치 메시지에 심기 전 정화·절단된다")
    void fetch_인스턴스ID불일치_위조값정화() {
        // 이 메시지는 응답의 peerError로 관리자 화면에 그대로 노출되므로, 컴프로마이즈된 피어가
        // 개행·따옴표·매우 긴 문자열을 instanceId에 심어도 안전한 문자집합과 64자 상한으로 걸러져야 한다.
        String maliciousInstanceId = "S\"VR<script>\n".repeat(10);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + SNAPSHOT_PATH))
                .andRespond(
                        withSuccess(
                                """
                                {"instanceId":"%s","self":false,"latest":null,
                                 "history":[],"peerError":null}
                                """
                                        .formatted(
                                                maliciousInstanceId
                                                        .replace("\"", "\\\"")
                                                        .replace("\n", "\\n")),
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(builder).fetch(PEER_URL, "SVR2"))
                .isInstanceOf(ServerMetricsPeerException.class)
                .hasMessageNotContaining("\n")
                .hasMessageNotContaining("\"")
                .hasMessageNotContaining("<script>")
                .satisfies(
                        e ->
                                assertThat(e.getMessage().length())
                                        .isLessThan(maliciousInstanceId.length()));
    }

    @Test
    @DisplayName("피어가 instanceId를 null로 응답해도 불일치로 처리하고 메시지에 null을 남긴다")
    void fetch_인스턴스ID없음() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PEER_URL + SNAPSHOT_PATH))
                .andRespond(
                        withSuccess(
                                """
                                {"instanceId":null,"self":false,"latest":null,
                                 "history":[],"peerError":null}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(builder).fetch(PEER_URL, "SVR2"))
                .isInstanceOf(ServerMetricsPeerException.class)
                .hasMessageContaining("SVR2")
                .hasMessageContaining("null");
    }
}
