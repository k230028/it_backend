package com.kdb.it.common.mfa.provider;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;

/** OnePass FIDO 인증 공급자이며 시작 거래의 서비스 거래 식별자를 서버 메모리에 보존한다. */
public final class FidoMfaProvider implements MfaProvider {

    private static final int MAX_PENDING_TRANSACTIONS = 1_024;

    private final OnePassClient onePassClient;
    private final LinkedHashMap<String, PendingFido> pendingByTransactionId = new LinkedHashMap<>();

    public FidoMfaProvider(OnePassClient onePassClient) {
        this.onePassClient = onePassClient;
    }

    @Override
    public synchronized MfaChallengeData start(MfaStartContext context) {
        removeExpiredTransactions();
        if (!pendingByTransactionId.containsKey(context.transactionId())
                && pendingByTransactionId.size() >= MAX_PENDING_TRANSACTIONS) {
            Iterator<String> iterator = pendingByTransactionId.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        OnePassClient.FidoStart start = onePassClient.startFido(context);
        pendingByTransactionId.put(
                context.transactionId(), new PendingFido(start.challenge(), start.svcTrId()));
        return start.challenge();
    }

    @Override
    public synchronized MfaVerificationResult verify(MfaVerifyContext context) {
        removeExpiredTransactions();
        PendingFido pending = pendingByTransactionId.get(context.startContext().transactionId());
        if (pending == null || !pending.challenge().challengeId().equals(context.challengeId())) {
            return MfaVerificationResult.failure();
        }
        MfaVerificationResult result = onePassClient.confirmFido(pending.svcTrId());
        if (result.verified()) {
            pendingByTransactionId.remove(context.startContext().transactionId());
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
