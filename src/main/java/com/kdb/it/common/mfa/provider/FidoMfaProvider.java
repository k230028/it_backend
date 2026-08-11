package com.kdb.it.common.mfa.provider;

import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/** OnePass FIDO 인증 공급자이며 시작 거래의 서비스 거래 식별자를 서버 메모리에 보존한다. */
public final class FidoMfaProvider implements MfaProvider {

    private static final int DEFAULT_MAX_PENDING_TRANSACTIONS = 1_024;

    private final OnePassClient onePassClient;
    private final int maxPendingTransactions;
    private final ConcurrentHashMap<String, PendingFido> pendingByTransactionId =
            new ConcurrentHashMap<>();

    /** 기본 최대 대기 거래 수를 사용하는 FIDO 공급자를 생성한다. */
    public FidoMfaProvider(OnePassClient onePassClient) {
        this(onePassClient, DEFAULT_MAX_PENDING_TRANSACTIONS);
    }

    FidoMfaProvider(OnePassClient onePassClient, int maxPendingTransactions) {
        if (maxPendingTransactions < 1) {
            throw new IllegalArgumentException("대기 거래 최대 수는 1 이상이어야 합니다.");
        }
        this.onePassClient = onePassClient;
        this.maxPendingTransactions = maxPendingTransactions;
    }

    @Override
    public MfaChallengeData start(MfaStartContext context) {
        removeExpiredTransactions();
        if (!pendingByTransactionId.containsKey(context.transactionId())
                && pendingByTransactionId.size() >= maxPendingTransactions) {
            Iterator<String> iterator = pendingByTransactionId.keySet().iterator();
            if (iterator.hasNext()) {
                pendingByTransactionId.remove(iterator.next());
            }
        }
        OnePassClient.FidoStart start = onePassClient.startFido(context);
        PendingFido pending = new PendingFido(start.challenge(), start.svcTrId());
        PendingFido existing = pendingByTransactionId.putIfAbsent(context.transactionId(), pending);
        return existing == null ? pending.challenge() : existing.challenge();
    }

    @Override
    public MfaVerificationResult verify(MfaVerifyContext context) {
        removeExpiredTransactions();
        PendingFido pending = pendingByTransactionId.get(context.startContext().transactionId());
        if (pending == null || !pending.challenge().challengeId().equals(context.challengeId())) {
            return MfaVerificationResult.failure();
        }
        MfaVerificationResult result = onePassClient.confirmFido(pending.svcTrId());
        if (result.verified()
                && !pendingByTransactionId.remove(
                        context.startContext().transactionId(), pending)) {
            return MfaVerificationResult.failure();
        }
        return result;
    }

    private void removeExpiredTransactions() {
        Instant now = Instant.now();
        pendingByTransactionId
                .entrySet()
                .removeIf(entry -> !entry.getValue().challenge().expiresAt().isAfter(now));
    }

    private record PendingFido(MfaChallengeData challenge, String svcTrId) {}
}
