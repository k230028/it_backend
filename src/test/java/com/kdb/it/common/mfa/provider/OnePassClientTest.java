package com.kdb.it.common.mfa.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.mfa.config.MfaProperties;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OnePassClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void motpStart_mapsRequestServiceAuthAndReturnsOnlySafeChallengeData() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        startServer(
                exchange -> {
                    request.set(readBody(exchange));
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100000\",\"challengeId\":\"challenge-1\",\"qrData\":\"not-an-otp\",\"token\":\"must-not-leak\"}");
                });

        MfaChallengeData challenge = client().requestMotpChallenge(context());

        assertThat(request.get()).contains("\"operation\":\"requestServiceAuth\"");
        assertThat(request.get()).contains("\"method\":\"MOTP\"");
        assertThat(request.get()).containsPattern("\\\"svcTrId\\\":\\\"\\d{20}\\\"");
        assertThat(challenge.challengeId()).isEqualTo("challenge-1");
        assertThat(challenge.qrData()).isEqualTo("not-an-otp");
        assertThat(challenge.expiresAt()).isEqualTo(context().expiresAt());
    }

    @Test
    void motpVerify_mapsRequestVerifyOtpAndAcceptsSuccessCode() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        startServer(
                exchange -> {
                    request.set(readBody(exchange));
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100000\",\"externalToken\":\"must-not-leak\"}");
                });

        MfaVerificationResult result = client().verifyMotp(context(), "challenge-1", "123456");

        assertThat(request.get()).contains("\"operation\":\"requestVerifyOtp\"");
        assertThat(request.get()).contains("\"challengeId\":\"challenge-1\"");
        assertThat(result.verified()).isTrue();
    }

    @Test
    void fidoStart_mapsRequestServiceAuth() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        startServer(
                exchange -> {
                    request.set(readBody(exchange));
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100000\",\"challengeId\":\"fido-1\",\"qrData\":\"qr\"}");
                });

        MfaChallengeData challenge = client().requestFidoChallenge(context());

        assertThat(request.get()).contains("\"operation\":\"requestServiceAuth\"");
        assertThat(request.get()).contains("\"method\":\"FIDO\"");
        assertThat(challenge.challengeId()).isEqualTo("fido-1");
    }

    @Test
    void fidoVerify_mapsTrResultConfirmAndRequiresApprovedStatus() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        startServer(
                exchange -> {
                    request.set(readBody(exchange));
                    respond(exchange, 200, "{\"resultCode\":\"100000\",\"trStatus\":1}");
                });

        MfaVerificationResult result = client().confirmFido(context(), "fido-1");

        assertThat(request.get()).contains("\"operation\":\"trResultConfirm\"");
        assertThat(result.verified()).isTrue();
    }

    @Test
    void malformedJsonIsReportedAsUnavailableWithoutLeakingTheResponse() throws Exception {
        startServer(exchange -> respond(exchange, 200, "not-json"));

        assertThatThrownBy(() -> client().verifyMotp(context(), "challenge-1", "123456"))
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

        assertThatThrownBy(() -> client().verifyMotp(context(), "challenge-1", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OnePass MFA 통신 또는 응답 처리에 실패했습니다.");
    }

    @Test
    void rejectsOversizedQrResponse() throws Exception {
        String qr = "x".repeat(16_385);
        startServer(
                exchange ->
                        respond(
                                exchange,
                                200,
                                "{\"resultCode\":\"100000\",\"challengeId\":\"fido-1\",\"qrData\":\""
                                        + qr
                                        + "\"}"));

        assertThatThrownBy(() -> client().requestFidoChallenge(context()))
                .isInstanceOf(IllegalArgumentException.class);
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
                Instant.parse("2026-08-10T12:00:00Z"));
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/onepass", handler::handle);
        server.start();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
