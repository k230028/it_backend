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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OnePassClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;
    private ExecutorService serverExecutor;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
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
                .isEqualTo(new MfaChallengeData("motp-tr", "qr", null, context().expiresAt()));
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
                .isEqualTo(new MfaChallengeData("fido-tr", "qr", null, context().expiresAt()));
        assertThat(result.verified()).isTrue();
    }

    @Test
    void fidoProvider_startTwiceForSameTransaction_reusesFirstChallenge() throws Exception {
        AtomicInteger startCount = new AtomicInteger();
        startServer(
                exchange -> {
                    requestBody(exchange);
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"fido-tr-"
                                    + startCount.incrementAndGet()
                                    + "\",\"qrImage\":\"qr\"}}");
                });
        FidoMfaProvider provider = new FidoMfaProvider(client());

        MfaChallengeData first = provider.start(context());
        MfaChallengeData second = provider.start(context());

        // 같은 거래를 다시 시작해도 최초 challenge를 유지해야 확인 요청이 어긋나지 않는다.
        assertThat(second).isEqualTo(first);
    }

    @Test
    void fidoProvider_dropsOldestPendingTransactionAtCapacity() throws Exception {
        startServer(
                exchange -> {
                    requestBody(exchange);
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"fido-tr\",\"qrImage\":\"qr\"}}");
                });
        FidoMfaProvider provider = new FidoMfaProvider(client(), 1);

        MfaChallengeData first = provider.start(context("transaction-1"));
        provider.start(context("transaction-2"));

        // 용량을 넘기면 오래된 거래가 밀려나 확인할 수 없어야 한다.
        MfaVerificationResult evicted =
                provider.verify(
                        new MfaVerifyContext(context("transaction-1"), first.challengeId(), ""));
        assertThat(evicted.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    void fidoProvider_rejectsCapacityBelowOne() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{\"resultCode\":\"100000\"}"));

        assertThatThrownBy(() -> new FidoMfaProvider(client(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fidoProvider_removesExpiredPendingTransactionsBeforeConfirming() throws Exception {
        startServer(
                exchange -> {
                    requestBody(exchange);
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"fido-tr\",\"qrImage\":\"qr\"}}");
                });
        FidoMfaProvider provider = new FidoMfaProvider(client());
        MfaStartContext expiredContext =
                new MfaStartContext(
                        "transaction-expired",
                        "10000001",
                        MfaPurpose.LOGIN,
                        Instant.parse("2000-01-01T00:00:00Z"));
        MfaChallengeData challenge = provider.start(expiredContext);

        MfaVerificationResult result =
                provider.verify(new MfaVerifyContext(expiredContext, challenge.challengeId(), ""));

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    void motpProvider_delegatesStartAndVerifyToClient() throws Exception {
        List<Map<String, Object>> requests = new java.util.ArrayList<>();
        AtomicInteger callCount = new AtomicInteger();
        startServer(
                exchange -> {
                    requests.add(requestBody(exchange));
                    if (callCount.getAndIncrement() == 0) {
                        respond(
                                exchange,
                                200,
                                "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"motp-tr\",\"qrImage\":\"qr\"}}");
                    } else {
                        respond(exchange, 200, "{\"resultCode\":\"100000\"}");
                    }
                });
        MotpMfaProvider provider = new MotpMfaProvider(client());

        MfaChallengeData challenge = provider.start(context());
        MfaVerificationResult result =
                provider.verify(new MfaVerifyContext(context(), challenge.challengeId(), "123456"));

        assertThat(challenge.challengeId()).isEqualTo("motp-tr");
        assertThat(result.verified()).isTrue();
        assertThat(requests.get(1)).containsEntry("otpValue", "123456");
    }

    @Test
    void motpVerify_rejectsNonSuccessResultCode() throws Exception {
        startServer(
                exchange -> {
                    requestBody(exchange);
                    respond(exchange, 200, "{\"resultCode\":\"900001\"}");
                });

        MfaVerificationResult result = client().verifyMotp(context(), "motp-tr", "000000");

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    void motpStart_rejectsNonSuccessResultCode() throws Exception {
        startServer(
                exchange -> {
                    requestBody(exchange);
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100108\",\"resultMsg\":\"등록되지 않은 사용자 입니다.\"}");
                });

        assertThatThrownBy(() -> client(Duration.ofSeconds(10)).requestMotpChallenge(context()))
                .isInstanceOf(OnePassProviderException.class)
                .satisfies(
                        throwable -> {
                            OnePassProviderException exception =
                                    (OnePassProviderException) throwable;
                            assertThat(exception.providerCode()).isEqualTo("100108");
                            assertThat(exception.providerMessage()).isEqualTo("등록되지 않은 사용자 입니다.");
                        });
    }

    @Test
    void motpStart_rejectsResponseWithoutChallengeId() throws Exception {
        startServer(
                exchange -> {
                    requestBody(exchange);
                    respond(exchange, 200, "{\"resultCode\":\"100000\",\"resultData\":{}}");
                });

        assertThatThrownBy(() -> client().requestMotpChallenge(context()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void motpStart_treatsNonObjectResultDataAsMissingChallengeId() throws Exception {
        startServer(
                exchange -> {
                    requestBody(exchange);
                    respond(exchange, 200, "{\"resultCode\":\"100000\",\"resultData\":\"plain\"}");
                });

        assertThatThrownBy(() -> client().requestMotpChallenge(context()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void motpStart_allowsResponseWithoutQrImage() throws Exception {
        startServer(
                exchange -> {
                    requestBody(exchange);
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"motp-tr\"}}");
                });

        MfaChallengeData challenge = client().requestMotpChallenge(context());

        assertThat(challenge.qrData()).isNull();
    }

    @Test
    void fidoStart_exposesChallengeThroughClientFacade() throws Exception {
        startServer(
                exchange -> {
                    requestBody(exchange);
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"fido-tr\",\"qrImage\":\"qr\"}}");
                });

        MfaChallengeData challenge = client().requestFidoChallenge(context());

        assertThat(challenge)
                .isEqualTo(new MfaChallengeData("fido-tr", "qr", null, context().expiresAt()));
    }

    @Test
    void fidoProvider_treatsNestedNonApprovedStatusAsUndecided() throws Exception {
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

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.UNDECIDED);
        assertThat(result.verified()).isFalse();
    }

    @Test
    void fidoProvider_treatsNonSuccessResultCodeAsFailure() throws Exception {
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
                        respond(exchange, 200, "{\"resultCode\":\"900001\"}");
                    }
                });
        FidoMfaProvider provider = new FidoMfaProvider(client());
        MfaChallengeData challenge = provider.start(context());

        MfaVerificationResult result =
                provider.verify(new MfaVerifyContext(context(), challenge.challengeId(), ""));

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    void fidoProvider_treatsUnknownChallengeAsFailureNotUndecided() throws Exception {
        startServer(
                exchange -> {
                    requestBody(exchange);
                    respond(
                            exchange,
                            200,
                            "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"fido-tr\",\"qrImage\":\"qr\"}}");
                });
        FidoMfaProvider provider = new FidoMfaProvider(client());
        provider.start(context());

        MfaVerificationResult result =
                provider.verify(new MfaVerifyContext(context(), "other-challenge", ""));

        assertThat(result.outcome()).isEqualTo(MfaVerificationResult.Outcome.FAILED);
    }

    @Test
    void fidoProvider_confirmsDifferentChallengesConcurrentlyAndConsumesEachOnSuccess()
            throws Exception {
        AtomicInteger startCount = new AtomicInteger();
        CountDownLatch confirmationsEntered = new CountDownLatch(2);
        CountDownLatch releaseConfirmations = new CountDownLatch(1);
        startServer(
                exchange -> {
                    Map<String, Object> request = requestBody(exchange);
                    if ("requestServiceAuth".equals(request.get("command"))) {
                        int sequence = startCount.incrementAndGet();
                        respond(
                                exchange,
                                200,
                                "{\"resultCode\":\"100000\",\"resultData\":{\"trId\":\"fido-"
                                        + sequence
                                        + "\",\"qrImage\":\"qr\"}}");
                        return;
                    }
                    confirmationsEntered.countDown();
                    try {
                        releaseConfirmations.await(2, TimeUnit.SECONDS);
                        respond(
                                exchange,
                                200,
                                "{\"resultCode\":\"100000\",\"resultData\":{\"trStatus\":\"1\"}}");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
        FidoMfaProvider provider = new FidoMfaProvider(client(Duration.ofSeconds(3)));
        MfaStartContext firstContext = context("transaction-1");
        MfaStartContext secondContext = context("transaction-2");
        MfaChallengeData firstChallenge = provider.start(firstContext);
        MfaChallengeData secondChallenge = provider.start(secondContext);
        ExecutorService verifierExecutor = Executors.newFixedThreadPool(2);
        try {
            Future<MfaVerificationResult> firstResult =
                    verifierExecutor.submit(
                            () ->
                                    provider.verify(
                                            new MfaVerifyContext(
                                                    firstContext,
                                                    firstChallenge.challengeId(),
                                                    "")));
            Future<MfaVerificationResult> secondResult =
                    verifierExecutor.submit(
                            () ->
                                    provider.verify(
                                            new MfaVerifyContext(
                                                    secondContext,
                                                    secondChallenge.challengeId(),
                                                    "")));

            assertThat(confirmationsEntered.await(500, TimeUnit.MILLISECONDS)).isTrue();
            releaseConfirmations.countDown();

            assertThat(firstResult.get(2, TimeUnit.SECONDS).verified()).isTrue();
            assertThat(secondResult.get(2, TimeUnit.SECONDS).verified()).isTrue();
            assertThat(
                            provider.verify(
                                            new MfaVerifyContext(
                                                    firstContext, firstChallenge.challengeId(), ""))
                                    .verified())
                    .isFalse();
        } finally {
            releaseConfirmations.countDown();
            verifierExecutor.shutdownNow();
        }
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
        return client(Duration.ofMillis(100));
    }

    private OnePassClient client(Duration timeout) {
        return new OnePassClient(
                new MfaProperties(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/onepass",
                        "site",
                        "service",
                        timeout,
                        timeout,
                        false,
                        Duration.ofSeconds(90),
                        5,
                        "test-fixed-key"));
    }

    private MfaStartContext context() {
        return context("transaction-1");
    }

    private MfaStartContext context(String transactionId) {
        return new MfaStartContext(
                transactionId, "10000001", MfaPurpose.LOGIN, Instant.parse("2099-01-01T00:00:00Z"));
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/onepass", handler::handle);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
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
