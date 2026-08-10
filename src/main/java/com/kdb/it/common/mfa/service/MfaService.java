package com.kdb.it.common.mfa.service;

import com.kdb.it.common.mfa.config.MfaProperties;
import com.kdb.it.common.mfa.domain.LoginPendingTransaction;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.domain.MfaTransaction;
import com.kdb.it.common.mfa.domain.MfaTransactionStatus;
import com.kdb.it.common.mfa.dto.MfaDto;
import com.kdb.it.common.mfa.exception.MfaErrorCode;
import com.kdb.it.common.mfa.exception.MfaException;
import com.kdb.it.common.mfa.provider.MfaChallengeData;
import com.kdb.it.common.mfa.provider.MfaProviderRegistry;
import com.kdb.it.common.mfa.provider.MfaStartContext;
import com.kdb.it.common.mfa.provider.MfaVerificationResult;
import com.kdb.it.common.mfa.provider.MfaVerifyContext;
import com.kdb.it.common.mfa.store.LoginPendingTransactionStore;
import com.kdb.it.common.mfa.store.MfaTransactionStore;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** MFA 거래 생성, 소유권 검증, 외부 인증 및 1회용 증표 소비를 조정한다. */
@Service
public class MfaService {

    private final MfaTransactionStore transactionStore;
    private final LoginPendingTransactionStore loginPendingTransactionStore;
    private final MfaProviderRegistry providerRegistry;
    private final MfaProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> knownExpiryByTokenHash =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> cancelledExpiryByTokenHash =
            new ConcurrentHashMap<>();

    public MfaService(
            MfaTransactionStore transactionStore,
            LoginPendingTransactionStore loginPendingTransactionStore,
            MfaProviderRegistry providerRegistry,
            MfaProperties properties,
            Clock clock) {
        this.transactionStore = transactionStore;
        this.loginPendingTransactionStore = loginPendingTransactionStore;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Task 5의 자격증명 검증 성공 뒤 사용할 로그인 대기 거래를 등록한다.
     *
     * @param eno 자격증명을 통과한 사용자 사원번호
     * @return httpOnly 쿠키에 보관할 대기 식별자와 서버 기준 남은 시간
     * @throws IllegalArgumentException 사원번호가 비어 있는 경우
     */
    public MfaDto.LoginPendingRegistration registerLoginPending(String eno) {
        if (eno == null || eno.isBlank()) {
            throw new IllegalArgumentException("로그인 대기 거래의 사원번호는 필수입니다.");
        }
        Instant now = Instant.now(clock);
        removeExpiredTracking(now);
        Instant expiresAt = now.plus(properties.challengeTtl());
        UUID pendingId = UUID.randomUUID();
        loginPendingTransactionStore.save(
                new LoginPendingTransaction(hash(pendingId.toString()), eno, expiresAt));
        return new MfaDto.LoginPendingRegistration(pendingId, remainingSeconds(expiresAt, now));
    }

    /**
     * 소유자가 새 MFA challenge를 시작한다.
     *
     * @param request 목적과 인증 수단
     * @param currentUser JWT에서 복원한 현재 사용자
     * @param pendingCookie 로그인 대기 증표 원문
     * @return 공급자 표시 정보와 서버 기준 남은 시간
     * @throws MfaException 로그인 대기 증표 또는 JWT 소유권이 없거나 공급자를 시작할 수 없는 경우
     */
    public MfaDto.MfaChallengeResponse startChallenge(
            MfaDto.MfaStartRequest request,
            Optional<CustomUserDetails> currentUser,
            String pendingCookie) {
        Instant now = Instant.now(clock);
        removeExpiredTracking(now);
        String eno = resolveOwnerForStart(request.purpose(), currentUser, pendingCookie, now);
        Instant expiresAt = now.plus(properties.challengeTtl());
        UUID challengeId = UUID.randomUUID();
        MfaStartContext context =
                new MfaStartContext(challengeId.toString(), eno, request.purpose(), expiresAt);
        MfaChallengeData challenge;
        try {
            challenge = providerRegistry.start(request.method(), context);
        } catch (RuntimeException exception) {
            throw new MfaException(MfaErrorCode.MFA_UNAVAILABLE);
        }
        String tokenHash = hash(challengeId.toString());
        transactionStore.save(
                MfaTransaction.pending(
                        tokenHash,
                        eno,
                        request.purpose(),
                        request.method(),
                        expiresAt,
                        hash(challenge.challengeId())));
        knownExpiryByTokenHash.put(tokenHash, expiresAt);
        return new MfaDto.MfaChallengeResponse(
                challengeId,
                challenge.challengeId(),
                challenge.qrData(),
                remainingSeconds(expiresAt, now));
    }

    /**
     * 소유자가 공급자 검증을 요청하고 성공 시 짧은 수명의 증표를 얻는다.
     *
     * @param challengeId 서버가 발급한 MFA 거래 식별자
     * @param request 공급자 challenge 식별자와 검증 값
     * @param currentUser JWT에서 복원한 현재 사용자
     * @param pendingCookie 로그인 대기 증표 원문
     * @return 검증 성공 여부와 서버 기준 증표 남은 시간
     * @throws MfaException 거래 만료, 소유권 위반, 인증 실패 또는 잠금 상태인 경우
     */
    public MfaDto.MfaVerifyResponse verifyChallenge(
            UUID challengeId,
            MfaDto.MfaVerifyRequest request,
            Optional<CustomUserDetails> currentUser,
            String pendingCookie) {
        Instant now = Instant.now(clock);
        String tokenHash = hash(challengeId.toString());
        MfaTransaction transaction = findActiveTransaction(tokenHash, now);
        assertTransactionOwner(transaction, currentUser, pendingCookie, now);
        assertVerifiable(transaction);

        if (!isExpectedProviderChallenge(transaction, request.providerChallengeId())) {
            throwFailedVerification(tokenHash, now);
        }

        MfaVerificationResult verification;
        try {
            verification =
                    providerRegistry.verify(
                            transaction.method(),
                            new MfaVerifyContext(
                                    new MfaStartContext(
                                            challengeId.toString(),
                                            transaction.eno(),
                                            transaction.purpose(),
                                            transaction.expiresAt()),
                                    request.providerChallengeId(),
                                    request.verificationValue()));
        } catch (RuntimeException exception) {
            throw new MfaException(MfaErrorCode.MFA_UNAVAILABLE);
        }
        if (!verification.verified()) {
            throwFailedVerification(tokenHash, now);
        }

        MfaTransaction verified =
                transactionStore
                        .verify(tokenHash, now)
                        .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_EXPIRED));
        if (verified.status() != MfaTransactionStatus.VERIFIED) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
        return new MfaDto.MfaVerifyResponse(true, remainingSeconds(verified.expiresAt(), now));
    }

    /**
     * 진행 중 MFA 거래를 취소해 이후 검증을 막는다.
     *
     * @param challengeId 서버가 발급한 MFA 거래 식별자
     * @param currentUser JWT에서 복원한 현재 사용자
     * @param pendingCookie 로그인 대기 증표 원문
     * @throws MfaException 거래 소유자가 아닌 경우
     */
    public void cancelChallenge(
            UUID challengeId, Optional<CustomUserDetails> currentUser, String pendingCookie) {
        Instant now = Instant.now(clock);
        String tokenHash = hash(challengeId.toString());
        MfaTransaction transaction = findActiveTransaction(tokenHash, now);
        assertTransactionOwner(transaction, currentUser, pendingCookie, now);
        transactionStore.delete(tokenHash, now);
        cancelledExpiryByTokenHash.put(tokenHash, transaction.expiresAt());
    }

    /**
     * 결재 명령 직전에 현재 JWT 사용자에게 귀속된 MFA 증표를 원자적으로 한 번 소비한다.
     *
     * @param currentUser 현재 JWT 사용자
     * @param proofCookie MFA 증표 원문
     * @throws MfaException 유효하지 않거나 이미 사용된 증표인 경우
     */
    public void consumeApprovalProof(CustomUserDetails currentUser, String proofCookie) {
        consumeProof(currentUser.getEno(), proofCookie, MfaPurpose.APPROVAL);
    }

    /**
     * 로그인 완료 직전에 로그인 대기 사용자에게 귀속된 MFA 증표를 원자적으로 한 번 소비한다.
     *
     * @param eno 로그인 대기 거래가 소유한 사원번호
     * @param proofCookie MFA 증표 원문
     * @throws MfaException 유효하지 않거나 이미 사용된 증표인 경우
     */
    public void consumeLoginProof(String eno, String proofCookie) {
        consumeProof(eno, proofCookie, MfaPurpose.LOGIN);
    }

    private String resolveOwnerForStart(
            MfaPurpose purpose,
            Optional<CustomUserDetails> currentUser,
            String pendingCookie,
            Instant now) {
        if (purpose == MfaPurpose.APPROVAL) {
            return currentUser
                    .map(CustomUserDetails::getEno)
                    .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_REQUIRED));
        }
        return findLoginPending(pendingCookie, now).eno();
    }

    private MfaTransaction findActiveTransaction(String tokenHash, Instant now) {
        return transactionStore
                .findByTokenHash(tokenHash, now)
                .orElseThrow(
                        () -> {
                            Instant expiresAt = knownExpiryByTokenHash.remove(tokenHash);
                            if (expiresAt != null && !now.isBefore(expiresAt)) {
                                throw new MfaException(MfaErrorCode.MFA_EXPIRED);
                            }
                            if (cancelledExpiryByTokenHash.containsKey(tokenHash)) {
                                throw new MfaException(MfaErrorCode.MFA_REQUIRED);
                            }
                            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
                        });
    }

    private void assertTransactionOwner(
            MfaTransaction transaction,
            Optional<CustomUserDetails> currentUser,
            String pendingCookie,
            Instant now) {
        String ownerEno;
        if (transaction.purpose() == MfaPurpose.LOGIN) {
            if (pendingCookie == null || pendingCookie.isBlank()) {
                throw new MfaException(MfaErrorCode.MFA_REQUIRED);
            }
            ownerEno =
                    loginPendingTransactionStore
                            .findByTokenHash(hash(pendingCookie), now)
                            .map(LoginPendingTransaction::eno)
                            .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_REQUIRED));
        } else {
            ownerEno =
                    currentUser
                            .map(CustomUserDetails::getEno)
                            .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_REQUIRED));
        }
        if (!transaction.eno().equals(ownerEno)) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
    }

    private LoginPendingTransaction findLoginPending(String pendingCookie, Instant now) {
        if (pendingCookie == null || pendingCookie.isBlank()) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
        return loginPendingTransactionStore
                .findByTokenHash(hash(pendingCookie), now)
                .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_EXPIRED));
    }

    private static void assertVerifiable(MfaTransaction transaction) {
        if (transaction.status() == MfaTransactionStatus.LOCKED) {
            throw new MfaException(MfaErrorCode.MFA_LOCKED);
        }
        if (transaction.status() != MfaTransactionStatus.PENDING) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
    }

    private static long remainingSeconds(Instant expiresAt, Instant now) {
        long milliseconds = Duration.between(now, expiresAt).toMillis();
        return Math.max(0, (milliseconds + 999) / 1_000);
    }

    private void removeExpiredTracking(Instant now) {
        knownExpiryByTokenHash.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        cancelledExpiryByTokenHash.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
    }

    private boolean isExpectedProviderChallenge(
            MfaTransaction transaction, String providerChallengeId) {
        if (transaction.providerChallengeHash() == null || providerChallengeId == null) {
            return false;
        }
        return MessageDigest.isEqual(
                transaction.providerChallengeHash().getBytes(StandardCharsets.US_ASCII),
                hash(providerChallengeId).getBytes(StandardCharsets.US_ASCII));
    }

    private void throwFailedVerification(String tokenHash, Instant now) {
        MfaTransaction failed =
                transactionStore
                        .fail(tokenHash, now, properties.maxFailures())
                        .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_EXPIRED));
        if (failed.status() == MfaTransactionStatus.LOCKED) {
            throw new MfaException(MfaErrorCode.MFA_LOCKED);
        }
        throw new MfaException(MfaErrorCode.MFA_FAILED);
    }

    private void consumeProof(String eno, String proofCookie, MfaPurpose purpose) {
        if (proofCookie == null || proofCookie.isBlank()) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
        String tokenHash = hash(proofCookie);
        transactionStore
                .consumeVerifiedOnce(tokenHash, eno, purpose, Instant.now(clock))
                .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_REQUIRED));
        knownExpiryByTokenHash.remove(tokenHash);
        cancelledExpiryByTokenHash.remove(tokenHash);
    }

    private static String hash(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
