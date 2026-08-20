package com.kdb.it.common.mfa.store;

import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.domain.MfaTransaction;
import java.time.Instant;
import java.util.Optional;

/** MFA 거래 저장소다. */
public interface MfaTransactionStore {

    void save(MfaTransaction transaction);

    Optional<MfaTransaction> findByTokenHash(String tokenHash, Instant now);

    Optional<MfaTransaction> verifyAndBindProof(String tokenHash, String proofHash, Instant now);

    Optional<MfaTransaction> fail(String tokenHash, Instant now, int maxFailures);

    Optional<MfaTransaction> delete(String tokenHash, Instant now);

    ProofConsumption consumeVerifiedOnce(
            String tokenHash, String eno, MfaPurpose purpose, Instant now);

    /** 검증 증표의 원자적 소비 결과다. */
    enum ProofConsumption {
        CONSUMED,
        EXPIRED,
        REJECTED,
        MISSING
    }
}
