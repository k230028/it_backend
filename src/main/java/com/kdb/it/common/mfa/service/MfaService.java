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
import com.kdb.it.common.mfa.provider.OnePassProviderException;
import com.kdb.it.common.mfa.store.LoginPendingTransactionStore;
import com.kdb.it.common.mfa.store.MfaTransactionStore;
import com.kdb.it.common.mfa.store.MfaTransactionStore.ProofConsumption;
import com.kdb.it.common.security.TokenFingerprint;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** MFA 거래 생성, 소유권 검증, 외부 인증 및 1회용 증표 소비를 조정한다. */
@Slf4j
@Service
public class MfaService {

    private static final SecureRandom PROOF_RANDOM = new SecureRandom();
    private static final int PROOF_BYTES = 32;

    private final MfaTransactionStore transactionStore;
    private final LoginPendingTransactionStore loginPendingTransactionStore;
    private final MfaProviderRegistry providerRegistry;
    private final MfaProperties properties;
    private final Clock clock;
    private final TokenFingerprint tokenFingerprint;

    public MfaService(
            MfaTransactionStore transactionStore,
            LoginPendingTransactionStore loginPendingTransactionStore,
            MfaProviderRegistry providerRegistry,
            MfaProperties properties,
            Clock clock,
            TokenFingerprint tokenFingerprint) {
        this.transactionStore = transactionStore;
        this.loginPendingTransactionStore = loginPendingTransactionStore;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.clock = clock;
        this.tokenFingerprint = tokenFingerprint;
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
        Instant expiresAt = now.plus(properties.challengeTtl());
        UUID pendingId = UUID.randomUUID();
        loginPendingTransactionStore.save(
                new LoginPendingTransaction(
                        tokenFingerprint.forMfa(pendingId.toString()), eno, expiresAt));
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
        String eno = resolveOwnerForStart(request.purpose(), currentUser, pendingCookie, now);
        Instant expiresAt = now.plus(properties.challengeTtl());
        UUID challengeId = UUID.randomUUID();
        MfaStartContext context =
                new MfaStartContext(challengeId.toString(), eno, request.purpose(), expiresAt);
        MfaChallengeData challenge;
        try {
            challenge = providerRegistry.start(request.method(), context);
        } catch (OnePassProviderException exception) {
            log.warn(
                    "MFA 공급자 challenge 시작 거부: method={}, purpose={}, providerCode={}, providerMessage={}",
                    request.method(),
                    request.purpose(),
                    exception.providerCode(),
                    exception.providerMessage());
            throw new MfaException(
                    MfaErrorCode.MFA_UNAVAILABLE,
                    exception.providerCode(),
                    exception.providerMessage());
        } catch (RuntimeException exception) {
            // 통신 실패·파싱 실패는 모두 MFA_UNAVAILABLE 한 코드로 수렴하므로 원인 예외를 여기서 남긴다.
            log.warn(
                    "MFA 공급자 challenge 시작 실패: method={}, purpose={}",
                    request.method(),
                    request.purpose(),
                    exception);
            throw new MfaException(MfaErrorCode.MFA_UNAVAILABLE);
        }
        String tokenHash = tokenFingerprint.forMfa(challengeId.toString());
        transactionStore.save(
                MfaTransaction.pending(
                        tokenHash,
                        eno,
                        request.purpose(),
                        request.method(),
                        expiresAt,
                        tokenFingerprint.forMfa(challenge.challengeId()),
                        challenge.providerTransactionId()));
        return new MfaDto.MfaChallengeResponse(
                challengeId,
                challenge.challengeId(),
                challenge.qrData(),
                challenge.randomKey(),
                remainingSeconds(expiresAt, now));
    }

    /**
     * 소유자가 공급자 검증을 요청하고 성공 시 짧은 수명의 증표를 얻는다.
     *
     * @param challengeId 서버가 발급한 MFA 거래 식별자
     * @param request 공급자 challenge 식별자와 검증 값
     * @param currentUser JWT에서 복원한 현재 사용자
     * @param pendingCookie 로그인 대기 증표 원문
     * @return 검증 성공 여부와 서버 기준 증표 남은 시간. 외부 공급자가 아직 결과를 확정하지 않았으면 {@code verified=false}와 {@code
     *     proof=null}을 반환하며 실패 횟수를 늘리지 않는다.
     * @throws MfaException 거래 만료, 소유권 위반, 인증 실패 또는 잠금 상태인 경우
     */
    public VerifiedChallenge verifyChallenge(
            UUID challengeId,
            MfaDto.MfaVerifyRequest request,
            Optional<CustomUserDetails> currentUser,
            String pendingCookie) {
        Instant now = Instant.now(clock);
        String tokenHash = tokenFingerprint.forMfa(challengeId.toString());
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
                                    request.verificationValue(),
                                    transaction.svcTrId()));
        } catch (RuntimeException exception) {
            // 통신 실패·파싱 실패는 모두 MFA_UNAVAILABLE 한 코드로 수렴하므로 원인 예외를 여기서 남긴다.
            log.warn(
                    "MFA 공급자 검증 실패: method={}, purpose={}",
                    transaction.method(),
                    transaction.purpose(),
                    exception);
            throw new MfaException(MfaErrorCode.MFA_UNAVAILABLE);
        }
        if (!verification.decided()) {
            // FIDO처럼 사용자가 다른 기기에서 승인하는 수단의 재조회는 실패로 집계하지 않는다.
            return new VerifiedChallenge(
                    new MfaDto.MfaVerifyResponse(
                            false, remainingSeconds(transaction.expiresAt(), now)),
                    null);
        }
        if (!verification.verified()) {
            throwFailedVerification(tokenHash, now);
        }

        String proof = newProof();
        MfaTransaction verified =
                transactionStore
                        .verifyAndBindProof(tokenHash, tokenFingerprint.forMfa(proof), now)
                        .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_EXPIRED));
        if (verified.status() != MfaTransactionStatus.VERIFIED) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
        return new VerifiedChallenge(
                new MfaDto.MfaVerifyResponse(true, remainingSeconds(verified.expiresAt(), now)),
                proof);
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
        String tokenHash = tokenFingerprint.forMfa(challengeId.toString());
        MfaTransaction transaction = findActiveTransaction(tokenHash, now);
        assertTransactionOwner(transaction, currentUser, pendingCookie, now);
        transactionStore.delete(tokenHash, now);
    }

    /**
     * 결재 명령 직전에 현재 JWT 사용자에게 귀속된 MFA 증표를 원자적으로 한 번 소비한다.
     *
     * @param currentUser 현재 JWT 사용자
     * @param proofCookie MFA 증표 원문
     * @throws MfaException 유효하지 않거나 이미 사용된 증표인 경우
     */
    public void consumeApprovalProof(CustomUserDetails currentUser, String proofCookie) {
        consumeProof(currentUser.getEno(), proofCookie, MfaPurpose.APPROVAL, Instant.now(clock));
    }

    /**
     * 로그인 완료 직전에 로그인 대기 사용자에게 귀속된 MFA 증표를 원자적으로 한 번 소비한다.
     *
     * @param pendingCookie 로그인 대기 증표 원문
     * @param proofCookie MFA 증표 원문
     * @return pending 거래와 동일한 사번
     * @throws MfaException 유효하지 않거나 이미 사용된 증표인 경우
     */
    public synchronized String consumeLoginProof(String pendingCookie, String proofCookie) {
        Instant now = Instant.now(clock);
        if (pendingCookie == null || pendingCookie.isBlank()) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
        LoginPendingTransaction pending =
                loginPendingTransactionStore
                        .findByTokenHash(tokenFingerprint.forMfa(pendingCookie), now)
                        .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_REQUIRED));
        consumeProof(pending.eno(), proofCookie, MfaPurpose.LOGIN, now);
        loginPendingTransactionStore
                .consumeOnce(tokenFingerprint.forMfa(pendingCookie), pending.eno(), now)
                .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_REQUIRED));
        return pending.eno();
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

    /**
     * 활성 거래를 조회한다. 없으면 저장소에 사유(만료 vs 그 밖)를 물어 정확한 에러 코드를 던진다. 만료 정리 배치가 이미 물리 삭제한 오래된 거래는 저장소도 사유를
     * 알 수 없어 MFA_REQUIRED로 폴백한다(만료 직후 유예 창 안에서만 정확한 구분이 보장됨 — SEC-13 §7.2와 동일한 한계).
     */
    private MfaTransaction findActiveTransaction(String tokenHash, Instant now) {
        return transactionStore
                .findByTokenHash(tokenHash, now)
                .orElseThrow(
                        () ->
                                new MfaException(
                                        transactionStore.isExpired(tokenHash, now)
                                                ? MfaErrorCode.MFA_EXPIRED
                                                : MfaErrorCode.MFA_REQUIRED));
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
                            .findByTokenHash(tokenFingerprint.forMfa(pendingCookie), now)
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
                .findByTokenHash(tokenFingerprint.forMfa(pendingCookie), now)
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

    private boolean isExpectedProviderChallenge(
            MfaTransaction transaction, String providerChallengeId) {
        if (transaction.providerChallengeHash() == null || providerChallengeId == null) {
            return false;
        }
        return MessageDigest.isEqual(
                transaction.providerChallengeHash().getBytes(StandardCharsets.US_ASCII),
                tokenFingerprint
                        .forMfa(providerChallengeId)
                        .getBytes(StandardCharsets.US_ASCII));
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

    private void consumeProof(String eno, String proofCookie, MfaPurpose purpose, Instant now) {
        if (proofCookie == null || proofCookie.isBlank()) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
        String tokenHash = tokenFingerprint.forMfa(proofCookie);
        ProofConsumption consumption =
                transactionStore.consumeVerifiedOnce(tokenHash, eno, purpose, now);
        if (consumption == ProofConsumption.EXPIRED) {
            throw new MfaException(MfaErrorCode.MFA_EXPIRED);
        }
        if (consumption != ProofConsumption.CONSUMED) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
    }

    private static String newProof() {
        byte[] bytes = new byte[PROOF_BYTES];
        PROOF_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 검증 응답 본문과 분리되어 쿠키로만 전달할 1회용 증표다.
     *
     * @param response 클라이언트에 반환할 검증 응답
     * @param proof 검증 성공 시의 증표 원문. 아직 결과가 확정되지 않은 재조회 응답에서는 null이다.
     */
    public record VerifiedChallenge(MfaDto.MfaVerifyResponse response, String proof) {}
}
