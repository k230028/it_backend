package com.kdb.it.common.mfa.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.mfa.config.MfaProperties;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OnePassClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void motpStart_sendsDocumentedRequestAndMapsNestedSafeResponse() throws Exception {
        List<Map<String, Object>> requests = new java.util.ArrayList<>();
        startServer(
                exchange -> {
                    requests.add(requestBody(exchange));
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"motp-tr\",\"qrImage\":\"qr\",\"deviceId\":\"must-not-leak\"}}");
                });

        MfaChallengeData challenge = client().requestMotpChallenge(context());

        assertMotpStartRequest(requests.getFirst());
        assertThat(challenge)
                .isEqualTo(new MfaChallengeData("motp-tr", "qr", context().expiresAt()));
    }

    @Test
    void motpVerify_sendsDocumentedRequestAndAcceptsSuccessCode() throws Exception {
        List<Map<String, Object>> requests = new java.util.ArrayList<>();
        startServer(
                exchange -> {
                    requests.add(requestBody(exchange));
                    respond(exchange, 200, "{\"resultCode\":\"100000\"}");
                });

        MfaVerificationResult result = client().verifyMotp(context(), "motp-tr", "123456");

        assertThat(requests)
                .containsExactly(
                        Map.of(
                                "command", "requestVerifyOtp",
                                "trId", "motp-tr",
                                "otpValue", "123456",
                                "crossDomain", true,
                                "authType", "OTP01"));
        assertThat(result.verified()).isTrue();
    }

    @Test
    void fidoProvider_reusesStartServiceTransactionIdForDocumentedConfirmRequest()
            throws Exception {
        List<Map<String, Object>> requests = new java.util.ArrayList<>();
        AtomicInteger callCount = new AtomicInteger();
        startServer(
                exchange -> {
                    requests.add(requestBody(exchange));
                    if (callCount.getAndIncrement() == 0) {
                        respond(
                                exchange,
                                200,
                                "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"fido-tr\",\"qrImage\":\"qr\"}}");
                    } else {
                        respond(
                                exchange,
                                200,
                                "{\"resultCode\":\"100000\",\"resultData\":{\"trStatus\":\"1\"}}");
                    }
                });
        FidoMfaProvider provider = new FidoMfaProvider(client());

        MfaChallengeData challenge = provider.start(context());
        MfaVerificationResult result =
                provider.verify(new MfaVerifyContext(context(), challenge.challengeId(), ""));

        assertFidoStartRequest(requests.getFirst());
        assertThat(requests.get(1))
                .isEqualTo(
                        Map.of(
                                "command",
                                "trResultConfirm",
                                "svcTrId",
                                requests.getFirst().get("svcTrId"),
                                "crossDomain",
                                true));
        assertThat(challenge)
                .isEqualTo(new MfaChallengeData("fido-tr", "qr", context().expiresAt()));
        assertThat(result.verified()).isTrue();
    }

    @Test
    void fidoProvider_rejectsNestedNonApprovedStatus() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        startServer(
                exchange -> {
                    requestBody(exchange);
                    if (callCount.getAndIncrement() == 0) {
                        respond(
                                exchange,
                                200,
                                "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"fido-tr\",\"qrImage\":\"qr\"}}");
                    } else {
                        respond(
                                exchange,
                                200,
                                "{\"resultCode\":\"100000\",\"resultData\":{\"trStatus\":\"0\"}}");
                    }
                });
        FidoMfaProvider provider = new FidoMfaProvider(client());
        MfaChallengeData challenge = provider.start(context());

        MfaVerificationResult result =
                provider.verify(new MfaVerifyContext(context(), challenge.challengeId(), ""));

        assertThat(result.verified()).isFalse();
    }

    @Test
    void malformedJsonIsReportedAsUnavailableWithoutLeakingTheResponse() throws Exception {
        startServer(exchange -> respond(exchange, 200, "not-json"));

        assertThatThrownBy(() -> client().verifyMotp(context(), "motp-tr", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OnePass MFA 통신 또는 응답 처리에 실패했습니다.");
    }

    @Test
    void timeoutIsReportedAsUnavailable() throws Exception {
        startServer(
                exchange -> {
                    try {
                        Thread.sleep(300);
                        respond(exchange, 200, "{\"resultCode\":\"100000\"}");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });

        assertThatThrownBy(() -> client().verifyMotp(context(), "motp-tr", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OnePass MFA 통신 또는 응답 처리에 실패했습니다.");
    }

    @Test
    void rejectsOversizedNestedQrImage() throws Exception {
        String qr = "x".repeat(16_385);
        startServer(
                exchange ->
                        respond(
                                exchange,
                                200,
                                "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"fido-tr\",\"qrImage\":\""
                                        + qr
                                        + "\"}}"));

        assertThatThrownBy(() -> client().requestFidoChallenge(context()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertMotpStartRequest(Map<String, Object> request) {
        assertThat(request)
                .isEqualTo(
                        Map.of(
                                "command", "requestServiceAuth",
                                "svcTrId", request.get("svcTrId"),
                                "siteId", "site",
                                "svcId", "service",
                                "loginId", "10000001",
                                "crossDomain", true,
                                "authType", "OTP01"));
        assertThat(request.get("svcTrId")).isInstanceOf(String.class).asString().matches("\\d{20}");
    }

    private void assertFidoStartRequest(Map<String, Object> request) {
        assertThat(request)
                .isEqualTo(
                        Map.of(
                                "command", "requestServiceAuth",
                                "svcTrId", request.get("svcTrId"),
                                "siteId", "site",
                                "svcId", "service",
                                "loginId", "10000001",
                                "bizAlarmType", "1",
                                "crossDomain", true));
        assertThat(request.get("svcTrId")).isInstanceOf(String.class).asString().matches("\\d{20}");
    }

    private OnePassClient client() {
        return new OnePassClient(
                new MfaProperties(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/onepass",
                        "site",
                        "service",
                        Duration.ofMillis(100),
                        Duration.ofMillis(100),
                        false,
                        Duration.ofSeconds(90),
                        5));
    }

    private MfaStartContext context() {
        return new MfaStartContext(
                "transaction-1",
                "10000001",
                MfaPurpose.LOGIN,
                Instant.parse("2099-01-01T00:00:00Z"));
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/onepass", handler::handle);
        server.start();
    }

    private static Map<String, Object> requestBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return OBJECT_MAPPER.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
