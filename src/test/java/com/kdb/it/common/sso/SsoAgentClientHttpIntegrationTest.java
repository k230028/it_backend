package com.kdb.it.common.sso;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 실제 {@link RestClient} 메시지 컨버터를 거치는 SSO 클라이언트 회귀 테스트.
 *
 * <p>RestClient를 mock하는 단위 테스트는 JSON 역직렬화 경로를 건너뛰므로, "내부망에서만" 재현되던 {@code
 * HttpMessageConversionException: Type definition error [JsonNode]}를 잡지 못했습니다(외부망은 SSO 서버
 * 미도달→타임아웃→폴백이라 컨버터 미실행). 본 테스트는 로컬 {@link HttpServer}로 실제 ISign+ JSON 응답을 흉내내고 {@link
 * SsoInfraConfig}의 실제 RestClient로 호출해, Spring Boot 4(Jackson 2/3 공존) 환경의 기본 컨버터가 응답을 정상 역직렬화하는지
 * 검증합니다. 과거 Jackson 2 {@code JsonNode} 구현에서는 이 테스트가 실패합니다.
 */
class SsoAgentClientHttpIntegrationTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 지정 경로에 JSON 응답을 반환하는 핸들러를 등록합니다. */
    private void respondJson(String path, String json) {
        server.createContext(
                path,
                exchange -> {
                    byte[] payload = json.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders()
                            .add("Content-Type", "application/json;charset=UTF-8");
                    exchange.sendResponseHeaders(200, payload.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(payload);
                    }
                });
    }

    private SsoProperties props() {
        return new SsoProperties(
                false, "K140024", baseUrl, baseUrl, "AGENT-1", "id, name", 3000, 3000);
    }

    private SsoAgentClient client() {
        RestClient restClient = new SsoInfraConfig().ssoRestClient(props());
        return new SsoAgentClient(restClient, props());
    }

    @Test
    @DisplayName("isServerAlive: 실제 컨버터로 checkserver JSON을 역직렬화해 true를 반환한다")
    void isServerAlive_realConverter_true() {
        respondJson("/openapi/checkserver", "{\"resultCode\":\"000000\"}");

        assertThat(client().isServerAlive()).isTrue();
    }

    @Test
    @DisplayName("authorize: base64 secureToken(+,/,=)이 실제 전송→서버 폼디코딩 후 원본대로 복원된다")
    void authorize_base64Token_roundTripsThroughRealServer() {
        // 실제 ISign+ 토큰과 유사하게 base64 특수문자를 포함
        String originalToken = "gF9s+rs6/rAb+cd==";
        AtomicReference<String> decodedToken = new AtomicReference<>();

        server.createContext(
                "/token/authorization",
                exchange -> {
                    // 서버(ISign+)가 쿼리를 폼 디코딩하는 것을 모사: %2B→'+', '+'→' '(공백)
                    String rawQuery = exchange.getRequestURI().getRawQuery();
                    for (String pair : rawQuery.split("&")) {
                        int eq = pair.indexOf('=');
                        String key = pair.substring(0, eq);
                        if ("secureToken".equals(key)) {
                            decodedToken.set(
                                    URLDecoder.decode(
                                            pair.substring(eq + 1), StandardCharsets.UTF_8));
                        }
                    }
                    byte[] payload =
                            "{\"resultCode\":\"000000\",\"user\":{\"id\":\"K150024\"}}"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders()
                            .add("Content-Type", "application/json;charset=UTF-8");
                    exchange.sendResponseHeaders(200, payload.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(payload);
                    }
                });

        client().authorize(originalToken, "sess", "127.0.0.1");

        // '+'가 %2B로 인코딩되어 전송되어야 서버 폼디코딩 후 원본과 일치한다.
        // 과거 코드(UriComponentsBuilder.encode())는 '+'를 그대로 보내 공백으로 깨졌다(토큰 복호화 실패).
        assertThat(decodedToken.get()).isEqualTo(originalToken);
    }

    @Test
    @DisplayName("authorize: 실제 컨버터로 token/authorization JSON을 역직렬화해 사용자 데이터를 추출한다")
    void authorize_realConverter_extractsUser() {
        respondJson(
                "/token/authorization",
                """
                {
                  "resultCode": "000000",
                  "resultMessage": "OK",
                  "returnUrl": "/return",
                  "useCSMode": true,
                  "user": { "id": "K150024", "name": "홍길동" }
                }
                """);

        SsoAgentClient.TokenAuthResult result = client().authorize("tok", "sess", "127.0.0.1");

        assertThat(result.resultCode()).isEqualTo("000000");
        assertThat(result.resultMessage()).isEqualTo("OK");
        assertThat(result.resultData()).isEqualTo("K150024,홍길동");
        assertThat(result.returnUrl()).isEqualTo("/return");
        assertThat(result.useCSMode()).isTrue();
    }
}
