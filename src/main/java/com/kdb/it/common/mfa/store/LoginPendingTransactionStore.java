package com.kdb.it.common.mfa.store;

import com.kdb.it.common.mfa.domain.LoginPendingTransaction;
import java.time.Instant;
import java.util.Optional;

/** MFA 전 로그인 대기 거래 저장소다. */
public interface LoginPendingTransactionStore {

    void save(LoginPendingTransaction transaction);

    Optional<LoginPendingTransaction> findByTokenHash(String tokenHash, Instant now);
}
