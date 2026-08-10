package com.kdb.it.common.mfa.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.mfa.domain.MfaMethod;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.domain.MfaTransaction;
import com.kdb.it.common.mfa.domain.MfaTransactionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("메모리 MFA 거래 저장소")
class InMemoryMfaTransactionStoreTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("대기 거래를 검증하면 VERIFIED 상태의 새 거래를 반환한다")
    void verify_pendingTransaction_becomesVerified() {
        MfaTransaction transaction = pending("token-1", now().plusSeconds(60));

        MfaTransaction verified = transaction.verify(now());

        assertThat(verified.status()).isEqualTo(MfaTransactionStatus.VERIFIED);
        assertThat(verified.verifiedAt()).isEqualTo(now());
        assertThat(transaction.status()).isEqualTo(MfaTransactionStatus.PENDING);
    }

    @Test
    @DisplayName("만료 시각과 같은 시각에는 검증하지 않고 EXPIRED 상태를 반환한다")
    void verify_atExpiry_becomesExpired() {
        MfaTransaction transaction = pending("token-2", now());

        assertThat(transaction.verify(now()).status()).isEqualTo(MfaTransactionStatus.EXPIRED);
    }

    @Test
    @DisplayName("VERIFIED 거래는 공개 생성자가 아니라 pending 전이로만 만들 수 있다")
    void verifiedTransaction_hasNoPublicConstructionPath() {
        assertThat(MfaTransaction.class.getConstructors()).isEmpty();

        MfaTransaction pending = pending("token-verified", now().plusSeconds(60));
        MfaTransaction verified = pending.verify(now());

        assertThat(pending.status()).isEqualTo(MfaTransactionStatus.PENDING);
        assertThat(verified.status()).isEqualTo(MfaTransactionStatus.VERIFIED);
        assertThat(verified.verifiedAt()).isEqualTo(now());
    }

    @Test
    @DisplayName("다른 사용자 또는 목적은 검증 완료 거래를 소비할 수 없다")
    void consumeVerifiedOnce_wrongUserOrPurpose_isRejected() {
        MfaTransactionStore store = verifiedStore("token-3");

        assertThat(store.consumeVerifiedOnce("token-3", "E0002", MfaPurpose.LOGIN, now()))
                .isEqualTo(MfaTransactionStore.ProofConsumption.REJECTED);
        assertThat(store.consumeVerifiedOnce("token-3", "E0001", MfaPurpose.APPROVAL, now()))
                .isEqualTo(MfaTransactionStore.ProofConsumption.REJECTED);
        assertThat(store.findByTokenHash("token-3", now())).isPresent();
    }

    @Test
    @DisplayName("실패 횟수가 최대치에 도달하면 LOCKED 상태가 되고 이후 검증할 수 없다")
    void fail_atMaximumFailures_locksTransaction() {
        MfaTransaction transaction = pending("token-4", now().plusSeconds(60));

        MfaTransaction locked = transaction.fail(now(), 1);

        assertThat(locked.failureCount()).isEqualTo(1);
        assertThat(locked.status()).isEqualTo(MfaTransactionStatus.LOCKED);
        assertThat(locked.verify(now()).status()).isEqualTo(MfaTransactionStatus.LOCKED);
    }

    @Test
    @DisplayName("검증 완료 거래는 한 번 소비되면 저장소에서 제거된다")
    void consumeVerifiedOnce_removesTransactionAfterSingleUse() {
        MfaTransactionStore store = verifiedStore("token-5");

        assertThat(store.consumeVerifiedOnce("token-5", "E0001", MfaPurpose.LOGIN, now()))
                .isEqualTo(MfaTransactionStore.ProofConsumption.CONSUMED);
        assertThat(store.consumeVerifiedOnce("token-5", "E0001", MfaPurpose.LOGIN, now()))
                .isEqualTo(MfaTransactionStore.ProofConsumption.MISSING);
        assertThat(store.findByTokenHash("token-5", now())).isEmpty();
    }

    @Test
    @DisplayName("만료된 거래는 조회 시 지연 정리되어 반환하지 않는다")
    void findByTokenHash_expiredTransaction_isCleanedUpLazily() {
        MfaTransactionStore store = new InMemoryMfaTransactionStore();
        store.save(pending("token-expired", now()));

        assertThat(store.findByTokenHash("token-expired", now())).isEmpty();
        assertThat(store.consumeVerifiedOnce("token-expired", "E0001", MfaPurpose.LOGIN, now()))
                .isEqualTo(MfaTransactionStore.ProofConsumption.MISSING);
    }

    @Test
    @DisplayName("동시 소비 요청 두 건 중 정확히 한 건만 성공한다")
    void consumeVerifiedOnce_concurrentConsumers_exactlyOneSucceeds() throws Exception {
        MfaTransactionStore store = verifiedStore("token-6");
        Callable<Boolean> consume =
                () ->
                        store.consumeVerifiedOnce("token-6", "E0001", MfaPurpose.LOGIN, now())
                                == MfaTransactionStore.ProofConsumption.CONSUMED;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = executor.invokeAll(List.of(consume, consume));

            assertThat(results).extracting(Future::get).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    @DisplayName("동시 실패 처리도 누락 없이 누적해 최대 횟수에서 거래를 잠근다")
    void fail_concurrentRequests_accumulatesFailuresAtomically() throws Exception {
        MfaTransactionStore store = new InMemoryMfaTransactionStore();
        store.save(pending("token-7", now().plusSeconds(60)));
        Callable<MfaTransaction> fail = () -> store.fail("token-7", now(), 2).orElseThrow();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            executor.invokeAll(List.of(fail, fail));
        }

        assertThat(store.findByTokenHash("token-7", now()))
                .get()
                .satisfies(
                        transaction -> {
                            assertThat(transaction.failureCount()).isEqualTo(2);
                            assertThat(transaction.status()).isEqualTo(MfaTransactionStatus.LOCKED);
                        });
    }

    private MfaTransactionStore verifiedStore(String tokenHash) {
        MfaTransactionStore store = new InMemoryMfaTransactionStore();
        store.save(pending(tokenHash, now().plus(Duration.ofMinutes(1))).verify(now()));
        return store;
    }

    private MfaTransaction pending(String tokenHash, Instant expiresAt) {
        return MfaTransaction.pending(
                tokenHash, "E0001", MfaPurpose.LOGIN, MfaMethod.FIDO, expiresAt);
    }

    private Instant now() {
        return CLOCK.instant();
    }
}
