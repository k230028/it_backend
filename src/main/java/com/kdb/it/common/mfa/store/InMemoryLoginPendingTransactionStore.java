package com.kdb.it.common.mfa.store;

import com.kdb.it.common.mfa.domain.LoginPendingTransaction;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** 지연 만료 정리를 수행하는 메모리 로그인 대기 거래 저장소다. */
@Component
public class InMemoryLoginPendingTransactionStore implements LoginPendingTransactionStore {

    private final ConcurrentHashMap<String, LoginPendingTransaction> transactions =
            new ConcurrentHashMap<>();

    @Override
    public void save(LoginPendingTransaction transaction) {
        transactions.put(transaction.tokenHash(), transaction);
    }

    @Override
    public Optional<LoginPendingTransaction> findByTokenHash(String tokenHash, Instant now) {
        AtomicReference<LoginPendingTransaction> found = new AtomicReference<>();
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
}
