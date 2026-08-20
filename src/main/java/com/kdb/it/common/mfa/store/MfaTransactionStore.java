package com.kdb.it.common.mfa.store;

import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.domain.MfaTransaction;
import java.time.Instant;
import java.util.Optional;

/** MFA 거래 저장소다. */
public interface MfaTransactionStore {

    /**
     * 새로 생성된 PENDING 거래를 저장한다.
     *
     * <p>{@link MfaTransaction#pending}로 만든, 아직 어떤 상태 전이도 거치지 않은 거래만 인자로 넘겨야 한다.
     * 검증·실패·취소·소비 같은 상태 전이는 이 메서드를 다시 호출하는 방식이 아니라 저장소가 제공하는 전이
     * 전용 메서드({@link #verifyAndBindProof}, {@link #fail}, {@link #delete}, {@link #consumeVerifiedOnce})로만
     * 수행해야 한다. 구현체는 이미 전이된 거래가 이 메서드에 직접 주어지는 경우 그 상태를 그대로 보존해
     * 영속화할 의무가 없다.
     */
    void save(MfaTransaction transaction);

    Optional<MfaTransaction> findByTokenHash(String tokenHash, Instant now);

    /**
     * findByTokenHash가 이 tokenHash에 대해 방금 빈 값을 반환했을 때, 그 이유가 "존재했으나 만료됨"인지
     * 확인한다. 사유 판정 전용이며 업무 흐름에서 호출하지 않는다.
     */
    boolean isExpired(String tokenHash, Instant now);

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
