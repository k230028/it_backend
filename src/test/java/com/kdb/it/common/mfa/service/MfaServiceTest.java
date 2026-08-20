package com.kdb.it.common.mfa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kdb.it.common.mfa.config.MfaProperties;
import com.kdb.it.common.mfa.domain.LoginPendingTransaction;
import com.kdb.it.common.mfa.domain.MfaMethod;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.domain.MfaTransaction;
import com.kdb.it.common.mfa.domain.MfaTransactionStatus;
import com.kdb.it.common.mfa.dto.MfaDto;
import com.kdb.it.common.mfa.exception.MfaErrorCode;
import com.kdb.it.common.mfa.exception.MfaException;
import com.kdb.it.common.mfa.provider.MfaChallengeData;
import com.kdb.it.common.mfa.provider.MfaProvider;
import com.kdb.it.common.mfa.provider.MfaProviderRegistry;
import com.kdb.it.common.mfa.provider.MfaVerificationResult;
import com.kdb.it.common.mfa.provider.OnePassProviderException;
import com.kdb.it.common.mfa.store.InMemoryLoginPendingTransactionStore;
import com.kdb.it.common.mfa.store.InMemoryMfaTransactionStore;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** MFA 거래 서비스의 소유권, 만료, 1회용 증표 보안 계약을 검증한다. */
class MfaServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String PENDING_PROOF = "pending-proof-value";
    private static final String OTP_SECRET = "otp-secret-must-not-be-logged";

    /** 허용 실패 횟수(5)를 넘겨 폴링해도 잠기지 않는지 확인하기 위한 미결정 응답 횟수다. */
    private static final int UNDECIDED_POLLS = 7;

    @Test
    void loginChallenge_대기쿠키소유자로거래를생성한다() {
        InMemoryLoginPendingTransactionStore pendingStore =
                new InMemoryLoginPendingTransactionStore();
        pendingStore.save(
                new LoginPendingTransaction(hash(PENDING_PROOF), "E10001", NOW.plusSeconds(90)));
        InMemoryMfaTransactionStore transactionStore = new InMemoryMfaTransactionStore();
        MfaService service = service(transactionStore, pendingStore, successProvider(), CLOCK);

        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.LOGIN, MfaMethod.MOTP),
                        Optional.empty(),
                        PENDING_PROOF);

        assertThat(transactionStore.findByTokenHash(hash(response.challengeId().toString()), NOW))
                .get()
                .extracting(transaction -> transaction.eno(), transaction -> transaction.purpose())
                .containsExactly("E10001", MfaPurpose.LOGIN);
    }

    @Test
    void loginPending_해시만저장하고Task5에원문식별자를반환한다() {
        InMemoryLoginPendingTransactionStore pendingStore =
                new InMemoryLoginPendingTransactionStore();
        MfaService service =
                service(new InMemoryMfaTransactionStore(), pendingStore, successProvider(), CLOCK);

        MfaDto.LoginPendingRegistration registration = service.registerLoginPending("E10001");

        assertThat(pendingStore.findByTokenHash(hash(registration.pendingId().toString()), NOW))
                .get()
                .extracting(LoginPendingTransaction::eno)
                .isEqualTo("E10001");
        assertThat(pendingStore.findByTokenHash(registration.pendingId().toString(), NOW))
                .isEmpty();
        assertThat(registration.remainingSeconds()).isEqualTo(90);
    }

    @Test
    void approvalChallenge_JWT없이는거부한다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);

        assertMfaError(
                () ->
                        service.startChallenge(
                                new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.FIDO),
                                Optional.empty(),
                                null),
                MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void loginVerification_다른대기쿠키로는거부한다() {
        InMemoryLoginPendingTransactionStore pendingStore =
                new InMemoryLoginPendingTransactionStore();
        pendingStore.save(
                new LoginPendingTransaction(hash(PENDING_PROOF), "E10001", NOW.plusSeconds(90)));
        MfaService service =
                service(new InMemoryMfaTransactionStore(), pendingStore, successProvider(), CLOCK);
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.LOGIN, MfaMethod.MOTP),
                        Optional.empty(),
                        PENDING_PROOF);

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.empty(),
                                "other-pending-proof"),
                MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void verification_만료된거래는거부한다() {
        InMemoryLoginPendingTransactionStore pendingStore =
                new InMemoryLoginPendingTransactionStore();
        pendingStore.save(
                new LoginPendingTransaction(hash(PENDING_PROOF), "E10001", NOW.plusSeconds(90)));
        InMemoryMfaTransactionStore transactionStore = new InMemoryMfaTransactionStore();
        MutableClock clock = new MutableClock(NOW);
        MfaService startService = service(transactionStore, pendingStore, successProvider(), clock);
        MfaDto.MfaChallengeResponse response =
                startService.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.LOGIN, MfaMethod.MOTP),
                        Optional.empty(),
                        PENDING_PROOF);
        clock.setInstant(NOW.plusSeconds(91));

        assertMfaError(
                () ->
                        startService.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.empty(),
                                PENDING_PROOF),
                MfaErrorCode.MFA_EXPIRED);
    }

    @Test
    void cancelChallenge_취소후검증을거부한다() {
        InMemoryLoginPendingTransactionStore pendingStore =
                new InMemoryLoginPendingTransactionStore();
        pendingStore.save(
                new LoginPendingTransaction(hash(PENDING_PROOF), "E10001", NOW.plusSeconds(90)));
        MfaService service =
                service(new InMemoryMfaTransactionStore(), pendingStore, successProvider(), CLOCK);
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.LOGIN, MfaMethod.FIDO),
                        Optional.empty(),
                        PENDING_PROOF);

        service.cancelChallenge(response.challengeId(), Optional.empty(), PENDING_PROOF);

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.empty(),
                                PENDING_PROOF),
                MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void verification_최대실패횟수에도달하면잠근다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        failureProvider(),
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                        Optional.of(user),
                        null);

        for (int attempt = 0; attempt < 4; attempt++) {
            assertMfaError(
                    () ->
                            service.verifyChallenge(
                                    response.challengeId(),
                                    new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                    Optional.of(user),
                                    null),
                    MfaErrorCode.MFA_FAILED);
        }
        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.of(user),
                                null),
                MfaErrorCode.MFA_LOCKED);
    }

    @Test
    void approvalProof_한번소비하면재사용할수없다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.FIDO),
                        Optional.of(user),
                        null);
        MfaService.VerifiedChallenge completion =
                service.verifyChallenge(
                        response.challengeId(),
                        new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                        Optional.of(user),
                        null);

        assertThat(completion.proof()).isNotEqualTo(response.challengeId().toString());
        service.consumeApprovalProof(user, completion.proof());

        assertMfaError(
                () -> service.consumeApprovalProof(user, completion.proof()),
                MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void approvalProof_만료된실제Proof는Expired로구분한다() {
        MutableClock clock = new MutableClock(NOW);
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        clock);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.FIDO),
                        Optional.of(user),
                        null);
        MfaService.VerifiedChallenge completion =
                service.verifyChallenge(
                        response.challengeId(),
                        new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                        Optional.of(user),
                        null);

        clock.setInstant(NOW.plusSeconds(91));

        assertMfaError(
                () -> service.consumeApprovalProof(user, completion.proof()),
                MfaErrorCode.MFA_EXPIRED);
        assertMfaError(
                () -> service.consumeApprovalProof(user, completion.proof()),
                MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void approvalVerification_다른JWT사용자는거부한다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);
        CustomUserDetails owner = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.FIDO),
                        Optional.of(owner),
                        null);

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.of(new CustomUserDetails("E20002", List.of(), "D002")),
                                null),
                MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void approvalProof_로그인목적증표는소비할수없다() {
        InMemoryMfaTransactionStore transactionStore = new InMemoryMfaTransactionStore();
        String proof = "login-purpose-proof";
        transactionStore.save(
                MfaTransaction.pending(
                                hash(proof),
                                "E10001",
                                MfaPurpose.LOGIN,
                                MfaMethod.FIDO,
                                NOW.plusSeconds(90))
                        .verify(NOW));
        MfaService service =
                service(
                        transactionStore,
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);

        assertMfaError(
                () ->
                        service.consumeApprovalProof(
                                new CustomUserDetails("E10001", List.of(), "D001"), proof),
                MfaErrorCode.MFA_REQUIRED);
        assertThat(transactionStore.findByTokenHash(hash(proof), NOW)).isPresent();
    }

    @Test
    void verification_다른공급자ChallengeId를대입하면실패한다() {
        InMemoryMfaTransactionStore transactionStore = new InMemoryMfaTransactionStore();
        MfaService service =
                service(
                        transactionStore,
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                        Optional.of(user),
                        null);

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("other-provider-id", OTP_SECRET),
                                Optional.of(user),
                                null),
                MfaErrorCode.MFA_FAILED);
        assertThat(transactionStore.findByTokenHash(hash(response.challengeId().toString()), NOW))
                .get()
                .extracting(MfaTransaction::providerChallengeHash)
                .isEqualTo(hash("provider-id"));
    }

    @Test
    void loginProof_한번소비하면재사용할수없다() {
        InMemoryLoginPendingTransactionStore pendingStore =
                new InMemoryLoginPendingTransactionStore();
        pendingStore.save(
                new LoginPendingTransaction(hash(PENDING_PROOF), "E10001", NOW.plusSeconds(90)));
        MfaService service =
                service(new InMemoryMfaTransactionStore(), pendingStore, successProvider(), CLOCK);
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.LOGIN, MfaMethod.FIDO),
                        Optional.empty(),
                        PENDING_PROOF);
        MfaService.VerifiedChallenge completion =
                service.verifyChallenge(
                        response.challengeId(),
                        new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                        Optional.empty(),
                        PENDING_PROOF);

        service.consumeLoginProof(PENDING_PROOF, completion.proof());

        assertMfaError(
                () -> service.consumeLoginProof(PENDING_PROOF, completion.proof()),
                MfaErrorCode.MFA_REQUIRED);
        assertMfaError(
                () -> service.consumeLoginProof(PENDING_PROOF, "other-proof"),
                MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void verification_비밀값과QR원문을로그에남기지않는다() {
        String qrSecret = "qr-secret-must-not-be-logged";
        MfaProvider provider =
                new MfaProvider() {
                    @Override
                    public MfaChallengeData start(
                            com.kdb.it.common.mfa.provider.MfaStartContext context) {
                        return new MfaChallengeData(
                                "provider-id", qrSecret, null, context.expiresAt(), null);
                    }

                    @Override
                    public MfaVerificationResult verify(
                            com.kdb.it.common.mfa.provider.MfaVerifyContext context) {
                        return MfaVerificationResult.failure();
                    }
                };
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        provider,
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        Logger logger = (Logger) LoggerFactory.getLogger(MfaService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MfaDto.MfaChallengeResponse response =
                    service.startChallenge(
                            new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                            Optional.of(user),
                            null);
            assertMfaError(
                    () ->
                            service.verifyChallenge(
                                    response.challengeId(),
                                    new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                    Optional.of(user),
                                    null),
                    MfaErrorCode.MFA_FAILED);

            assertThat(appender.list)
                    .allSatisfy(
                            event -> {
                                assertThat(event.getFormattedMessage()).doesNotContain(OTP_SECRET);
                                assertThat(event.getFormattedMessage()).doesNotContain(qrSecret);
                            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void 미결정응답은실패횟수를소진하지않고증표도발급하지않는다() {
        AtomicInteger calls = new AtomicInteger();
        MfaProvider provider =
                new MfaProvider() {
                    @Override
                    public MfaChallengeData start(
                            com.kdb.it.common.mfa.provider.MfaStartContext context) {
                        return new MfaChallengeData("provider-id", null, null, context.expiresAt(), null);
                    }

                    @Override
                    public MfaVerificationResult verify(
                            com.kdb.it.common.mfa.provider.MfaVerifyContext context) {
                        return calls.getAndIncrement() < UNDECIDED_POLLS
                                ? MfaVerificationResult.undecided()
                                : MfaVerificationResult.success();
                    }
                };
        InMemoryMfaTransactionStore transactionStore = new InMemoryMfaTransactionStore();
        MfaService service =
                service(
                        transactionStore,
                        new InMemoryLoginPendingTransactionStore(),
                        provider,
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.FIDO),
                        Optional.of(user),
                        null);

        // 허용 실패 횟수(5)보다 많이 폴링해도 거래가 잠기지 않아야 한다.
        for (int poll = 0; poll < UNDECIDED_POLLS; poll++) {
            MfaService.VerifiedChallenge polled =
                    service.verifyChallenge(
                            response.challengeId(),
                            new MfaDto.MfaVerifyRequest("provider-id", ""),
                            Optional.of(user),
                            null);
            assertThat(polled.response().verified()).isFalse();
            assertThat(polled.proof()).isNull();
        }
        assertThat(transactionStore.findByTokenHash(hash(response.challengeId().toString()), NOW))
                .get()
                .extracting(MfaTransaction::status)
                .isEqualTo(MfaTransactionStatus.PENDING);

        MfaService.VerifiedChallenge completed =
                service.verifyChallenge(
                        response.challengeId(),
                        new MfaDto.MfaVerifyRequest("provider-id", ""),
                        Optional.of(user),
                        null);

        assertThat(completed.response().verified()).isTrue();
        assertThat(completed.proof()).isNotNull();
        service.consumeApprovalProof(user, completed.proof());
    }

    @Test
    void 미결정응답뒤의실제실패는여전히실패로집계한다() {
        AtomicInteger calls = new AtomicInteger();
        MfaProvider provider =
                new MfaProvider() {
                    @Override
                    public MfaChallengeData start(
                            com.kdb.it.common.mfa.provider.MfaStartContext context) {
                        return new MfaChallengeData("provider-id", null, null, context.expiresAt(), null);
                    }

                    @Override
                    public MfaVerificationResult verify(
                            com.kdb.it.common.mfa.provider.MfaVerifyContext context) {
                        return calls.getAndIncrement() == 0
                                ? MfaVerificationResult.undecided()
                                : MfaVerificationResult.failure();
                    }
                };
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        provider,
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.FIDO),
                        Optional.of(user),
                        null);
        service.verifyChallenge(
                response.challengeId(),
                new MfaDto.MfaVerifyRequest("provider-id", ""),
                Optional.of(user),
                null);

        for (int attempt = 0; attempt < 4; attempt++) {
            assertMfaError(
                    () ->
                            service.verifyChallenge(
                                    response.challengeId(),
                                    new MfaDto.MfaVerifyRequest("provider-id", ""),
                                    Optional.of(user),
                                    null),
                    MfaErrorCode.MFA_FAILED);
        }

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", ""),
                                Optional.of(user),
                                null),
                MfaErrorCode.MFA_LOCKED);
    }

    @Test
    void 로그인대기_등록은_사원번호를_요구한다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);

        assertThatThrownBy(() -> service.registerLoginPending(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registerLoginPending("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 로그인_challenge는_대기쿠키가_없으면_거부한다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);

        assertMfaError(
                () ->
                        service.startChallenge(
                                new MfaDto.MfaStartRequest(MfaPurpose.LOGIN, MfaMethod.MOTP),
                                Optional.empty(),
                                null),
                MfaErrorCode.MFA_REQUIRED);
        assertMfaError(
                () ->
                        service.startChallenge(
                                new MfaDto.MfaStartRequest(MfaPurpose.LOGIN, MfaMethod.MOTP),
                                Optional.empty(),
                                "  "),
                MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void 로그인_challenge는_모르는_대기쿠키를_만료로_처리한다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);

        assertMfaError(
                () ->
                        service.startChallenge(
                                new MfaDto.MfaStartRequest(MfaPurpose.LOGIN, MfaMethod.MOTP),
                                Optional.empty(),
                                "unknown-pending"),
                MfaErrorCode.MFA_EXPIRED);
    }

    @Test
    void 로그인_검증은_대기쿠키가_없으면_거부한다() {
        InMemoryLoginPendingTransactionStore pendingStore =
                new InMemoryLoginPendingTransactionStore();
        pendingStore.save(
                new LoginPendingTransaction(hash(PENDING_PROOF), "E10001", NOW.plusSeconds(90)));
        MfaService service =
                service(new InMemoryMfaTransactionStore(), pendingStore, successProvider(), CLOCK);
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.LOGIN, MfaMethod.MOTP),
                        Optional.empty(),
                        PENDING_PROOF);

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.empty(),
                                null),
                MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void 공급자_challenge_식별자가_다르면_실패로_집계한다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                        Optional.of(user),
                        null);

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("other-provider-id", OTP_SECRET),
                                Optional.of(user),
                                null),
                MfaErrorCode.MFA_FAILED);
    }

    @Test
    void 잠긴_거래는_이후_검증도_잠금으로_거부한다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        failureProvider(),
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                        Optional.of(user),
                        null);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(
                            () ->
                                    service.verifyChallenge(
                                            response.challengeId(),
                                            new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                            Optional.of(user),
                                            null))
                    .isInstanceOf(MfaException.class);
        }

        // 잠긴 뒤의 추가 검증은 실패 집계가 아니라 잠금 상태로 즉시 거부한다.
        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.of(user),
                                null),
                MfaErrorCode.MFA_LOCKED);
    }

    @Test
    void 결재_증표가_없으면_소비를_거부한다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");

        assertMfaError(() -> service.consumeApprovalProof(user, null), MfaErrorCode.MFA_REQUIRED);
        assertMfaError(() -> service.consumeApprovalProof(user, "  "), MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void 로그인_증표_소비는_대기쿠키와_증표를_모두_요구한다() {
        InMemoryLoginPendingTransactionStore pendingStore =
                new InMemoryLoginPendingTransactionStore();
        pendingStore.save(
                new LoginPendingTransaction(hash(PENDING_PROOF), "E10001", NOW.plusSeconds(90)));
        MfaService service =
                service(new InMemoryMfaTransactionStore(), pendingStore, successProvider(), CLOCK);

        assertMfaError(() -> service.consumeLoginProof(null, "proof"), MfaErrorCode.MFA_REQUIRED);
        assertMfaError(
                () -> service.consumeLoginProof("unknown-pending", "proof"),
                MfaErrorCode.MFA_REQUIRED);
        assertMfaError(
                () -> service.consumeLoginProof(PENDING_PROOF, null), MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void 만료된_거래의_검증은_만료로_구분한다() {
        MutableClock clock = new MutableClock(NOW);
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        clock);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                        Optional.of(user),
                        null);

        clock.setInstant(NOW.plusSeconds(120));

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.of(user),
                                null),
                MfaErrorCode.MFA_EXPIRED);

        service.startChallenge(
                new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                Optional.of(user),
                null);
    }

    @Test
    void 다른_사용자는_결재_거래를_검증할_수_없다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);
        CustomUserDetails owner = new CustomUserDetails("E10001", List.of(), "D001");
        CustomUserDetails other = new CustomUserDetails("E99999", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                        Optional.of(owner),
                        null);

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.of(other),
                                null),
                MfaErrorCode.MFA_REQUIRED);
        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.empty(),
                                null),
                MfaErrorCode.MFA_REQUIRED);
    }

    @Test
    void 공급자_시작이_실패하면_서비스_이용불가로_바꾼다() {
        MfaProvider provider =
                new MfaProvider() {
                    @Override
                    public MfaChallengeData start(
                            com.kdb.it.common.mfa.provider.MfaStartContext context) {
                        throw new IllegalStateException("OnePass 통신 실패");
                    }

                    @Override
                    public MfaVerificationResult verify(
                            com.kdb.it.common.mfa.provider.MfaVerifyContext context) {
                        return MfaVerificationResult.success();
                    }
                };
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        provider,
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");

        assertMfaError(
                () ->
                        service.startChallenge(
                                new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                                Optional.of(user),
                                null),
                MfaErrorCode.MFA_UNAVAILABLE);
    }

    @Test
    void 공급자_업무오류의_코드와_메시지를_보존한다() {
        MfaProvider provider =
                new MfaProvider() {
                    @Override
                    public MfaChallengeData start(
                            com.kdb.it.common.mfa.provider.MfaStartContext context) {
                        throw new OnePassProviderException("100108", "등록되지 않은 사용자 입니다.");
                    }

                    @Override
                    public MfaVerificationResult verify(
                            com.kdb.it.common.mfa.provider.MfaVerifyContext context) {
                        return MfaVerificationResult.success();
                    }
                };
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        provider,
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");

        assertThatThrownBy(
                        () ->
                                service.startChallenge(
                                        new MfaDto.MfaStartRequest(
                                                MfaPurpose.APPROVAL, MfaMethod.MOTP),
                                        Optional.of(user),
                                        null))
                .isInstanceOf(MfaException.class)
                .satisfies(
                        throwable -> {
                            MfaException exception = (MfaException) throwable;
                            assertThat(exception.providerCode()).isEqualTo("100108");
                            assertThat(exception.providerMessage()).isEqualTo("등록되지 않은 사용자 입니다.");
                        });
    }

    @Test
    void 공급자_검증이_예외를_던지면_서비스_이용불가로_바꾼다() {
        MfaProvider provider =
                new MfaProvider() {
                    @Override
                    public MfaChallengeData start(
                            com.kdb.it.common.mfa.provider.MfaStartContext context) {
                        return new MfaChallengeData("provider-id", null, null, context.expiresAt(), null);
                    }

                    @Override
                    public MfaVerificationResult verify(
                            com.kdb.it.common.mfa.provider.MfaVerifyContext context) {
                        throw new IllegalStateException("OnePass 통신 실패");
                    }
                };
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        provider,
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                        Optional.of(user),
                        null);

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.of(user),
                                null),
                MfaErrorCode.MFA_UNAVAILABLE);
    }

    @Test
    void 취소한_거래의_검증은_증표_요구로_거부한다() {
        MfaService service =
                service(
                        new InMemoryMfaTransactionStore(),
                        new InMemoryLoginPendingTransactionStore(),
                        successProvider(),
                        CLOCK);
        CustomUserDetails user = new CustomUserDetails("E10001", List.of(), "D001");
        MfaDto.MfaChallengeResponse response =
                service.startChallenge(
                        new MfaDto.MfaStartRequest(MfaPurpose.APPROVAL, MfaMethod.MOTP),
                        Optional.of(user),
                        null);

        service.cancelChallenge(response.challengeId(), Optional.of(user), null);

        assertMfaError(
                () ->
                        service.verifyChallenge(
                                response.challengeId(),
                                new MfaDto.MfaVerifyRequest("provider-id", OTP_SECRET),
                                Optional.of(user),
                                null),
                MfaErrorCode.MFA_REQUIRED);
    }

    private static MfaService service(
            InMemoryMfaTransactionStore transactionStore,
            InMemoryLoginPendingTransactionStore pendingStore,
            MfaProvider provider,
            Clock clock) {
        return new MfaService(
                transactionStore,
                pendingStore,
                new MfaProviderRegistry(Map.of(MfaMethod.FIDO, provider, MfaMethod.MOTP, provider)),
                new MfaProperties(
                        "",
                        "",
                        "",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        true,
                        Duration.ofSeconds(90),
                        5,
                        "test-fixed-key"),
                clock);
    }

    private static MfaProvider successProvider() {
        return new MfaProvider() {
            @Override
            public MfaChallengeData start(com.kdb.it.common.mfa.provider.MfaStartContext context) {
                return new MfaChallengeData("provider-id", null, null, context.expiresAt(), null);
            }

            @Override
            public MfaVerificationResult verify(
                    com.kdb.it.common.mfa.provider.MfaVerifyContext context) {
                return MfaVerificationResult.success();
            }
        };
    }

    private static MfaProvider failureProvider() {
        return new MfaProvider() {
            @Override
            public MfaChallengeData start(com.kdb.it.common.mfa.provider.MfaStartContext context) {
                return new MfaChallengeData("provider-id", null, null, context.expiresAt(), null);
            }

            @Override
            public MfaVerificationResult verify(
                    com.kdb.it.common.mfa.provider.MfaVerifyContext context) {
                return MfaVerificationResult.failure();
            }
        };
    }

    private static void assertMfaError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, MfaErrorCode code) {
        assertThatThrownBy(callable)
                .isInstanceOf(MfaException.class)
                .extracting(throwable -> ((MfaException) throwable).errorCode())
                .isEqualTo(code);
    }

    private static String hash(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
