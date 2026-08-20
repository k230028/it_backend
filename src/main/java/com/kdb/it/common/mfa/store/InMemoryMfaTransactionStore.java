package com.kdb.it.common.mfa.store;

import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.domain.MfaTransaction;
import com.kdb.it.common.mfa.domain.MfaTransactionStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 원자적 소비와 지연 만료 정리를 제공하는 메모리 MFA 거래 저장소다. */
@Component
@ConditionalOnProperty(prefix = "app.mfa", name = "store", havingValue = "memory")
public class InMemoryMfaTransactionStore implements MfaTransactionStore {

    private final ConcurrentHashMap<String, MfaTransaction> transactions =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MfaTransaction> proofTransactions =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> expiryByTokenHash = new ConcurrentHashMap<>();

    // 인터페이스 계약보다 관대하게, 주어진 거래를 상태와 무관하게 그대로 저장한다.
    @Override
    public void save(MfaTransaction transaction) {
        transactions.put(transaction.tokenHash(), transaction);
        expiryByTokenHash.put(transaction.tokenHash(), transaction.expiresAt());
    }

    @Override
    public Optional<MfaTransaction> findByTokenHash(String tokenHash, Instant now) {
        AtomicReference<MfaTransaction> found = new AtomicReference<>();
        transactions.computeIfPresent(
                tokenHash,
                (ignored, transaction) -> {
                    if (transaction.isExpiredAt(now)) {
                        return null;
                    }
                    found.set(transaction);
                    return transaction;
                });
        return Optional.ofNullable(found.get());
    }

    @Override
    public synchronized Optional<MfaTransaction> verifyAndBindProof(
            String tokenHash, String proofHash, Instant now) {
        MfaTransaction transaction = transactions.get(tokenHash);
        if (transaction == null || transaction.isExpiredAt(now)) {
            transactions.remove(tokenHash, transaction);
            return Optional.empty();
        }
        MfaTransaction verified = transaction.verify(now);
        MfaTransaction bound = verified.bindProofHash(proofHash);
        if (bound.proofHash() == null || proofTransactions.putIfAbsent(proofHash, bound) != null) {
            return Optional.empty();
        }
        if (!transactions.remove(tokenHash, transaction)) {
            proofTransactions.remove(proofHash, bound);
            return Optional.empty();
        }
        return Optional.of(bound);
    }

    @Override
    public Optional<MfaTransaction> fail(String tokenHash, Instant now, int maxFailures) {
        AtomicReference<MfaTransaction> failed = new AtomicReference<>();
        transactions.compute(
                tokenHash,
                (ignored, transaction) -> {
                    if (transaction == null || transaction.isExpiredAt(now)) {
                        return null;
                    }
                    MfaTransaction nextTransaction = transaction.fail(now, maxFailures);
                    failed.set(nextTransaction);
                    return nextTransaction;
                });
        return Optional.ofNullable(failed.get());
    }

    @Override
    public Optional<MfaTransaction> delete(String tokenHash, Instant now) {
        AtomicReference<MfaTransaction> deleted = new AtomicReference<>();
        transactions.computeIfPresent(
                tokenHash,
                (ignored, transaction) -> {
                    if (transaction.isExpiredAt(now)) {
                        return null;
                    }
                    deleted.set(transaction);
                    return null;
                });
        return Optional.ofNullable(deleted.get());
    }

    @Override
    public ProofConsumption consumeVerifiedOnce(
            String tokenHash, String eno, MfaPurpose purpose, Instant now) {
        ProofConsumption consumedProof = consume(proofTransactions, tokenHash, eno, purpose, now);
        if (consumedProof != ProofConsumption.MISSING) {
            return consumedProof;
        }
        return consume(transactions, tokenHash, eno, purpose, now);
    }

    private ProofConsumption consume(
            ConcurrentHashMap<String, MfaTransaction> source,
            String tokenHash,
            String eno,
            MfaPurpose purpose,
            Instant now) {
        AtomicReference<ProofConsumption> result = new AtomicReference<>(ProofConsumption.MISSING);
        source.compute(
                tokenHash,
                (ignored, transaction) -> {
                    if (transaction == null) {
                        return null;
                    }
                    if (transaction.isExpiredAt(now)) {
                        result.set(ProofConsumption.EXPIRED);
                        return null;
                    }
                    if (!transaction.eno().equals(eno)
                            || transaction.purpose() != purpose
                            || transaction.status() != MfaTransactionStatus.VERIFIED) {
                        result.set(ProofConsumption.REJECTED);
                        return transaction;
                    }
                    result.set(ProofConsumption.CONSUMED);
                    return null;
                });
        return result.get();
    }

    /**
     * 인스턴스 로컬 보조 맵으로 만료 여부를 판정한다. 단일 인스턴스 dev 전용 저장소라 인스턴스 간 정확도 손실이 없다. {@code save()} 시 채우며,
     * {@code findByTokenHash}의 읽기 시 축출(evict-on-read) 동작과는 독립적으로 유지한다 — 그 축출 순서에 기존 테스트 여러 개가 의존하고
     * 있어 건드리지 않는다.
     */
    @Override
    public boolean isExpired(String tokenHash, Instant now) {
        Instant expiresAt = expiryByTokenHash.get(tokenHash);
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
