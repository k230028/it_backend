package com.kdb.it.common.mfa.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.mfa.domain.LoginPendingTransaction;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 로그인 대기 거래 저장소의 만료·소유권·1회 소비 계약을 검증한다. */
@DisplayName("메모리 로그인 대기 거래 저장소")
class InMemoryLoginPendingTransactionStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    @DisplayName("만료 전 거래는 조회되고 만료 시각부터는 지연 정리된다")
    void findByTokenHash_expiresAtBoundary() {
        InMemoryLoginPendingTransactionStore store = new InMemoryLoginPendingTransactionStore();
        store.save(new LoginPendingTransaction("hash-1", "E10001", NOW.plusSeconds(90)));

        assertThat(store.findByTokenHash("hash-1", NOW)).isPresent();
        assertThat(store.findByTokenHash("hash-1", NOW.plusSeconds(90))).isEmpty();
        // 만료 조회에서 정리되었으므로 이전 시각으로 다시 물어도 남아 있지 않다.
        assertThat(store.findByTokenHash("hash-1", NOW)).isEmpty();
    }

    @Test
    @DisplayName("없는 토큰 조회는 빈 값이다")
    void findByTokenHash_missingToken_isEmpty() {
        InMemoryLoginPendingTransactionStore store = new InMemoryLoginPendingTransactionStore();

        assertThat(store.findByTokenHash("absent", NOW)).isEmpty();
    }

    @Test
    @DisplayName("소유자가 소비하면 한 번만 성공한다")
    void consumeOnce_owner_succeedsExactlyOnce() {
        InMemoryLoginPendingTransactionStore store = new InMemoryLoginPendingTransactionStore();
        store.save(new LoginPendingTransaction("hash-2", "E10001", NOW.plusSeconds(90)));

        assertThat(store.consumeOnce("hash-2", "E10001", NOW)).isPresent();
        assertThat(store.consumeOnce("hash-2", "E10001", NOW)).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자는 소비하지 못하고 거래도 사라지지 않는다")
    void consumeOnce_otherUser_keepsTransaction() {
        InMemoryLoginPendingTransactionStore store = new InMemoryLoginPendingTransactionStore();
        store.save(new LoginPendingTransaction("hash-3", "E10001", NOW.plusSeconds(90)));

        assertThat(store.consumeOnce("hash-3", "E99999", NOW)).isEmpty();
        assertThat(store.findByTokenHash("hash-3", NOW)).isPresent();
    }

    @Test
    @DisplayName("없는 토큰과 만료 거래는 소비되지 않는다")
    void consumeOnce_missingOrExpired_isEmpty() {
        InMemoryLoginPendingTransactionStore store = new InMemoryLoginPendingTransactionStore();
        store.save(new LoginPendingTransaction("hash-4", "E10001", NOW));

        assertThat(store.consumeOnce("absent", "E10001", NOW)).isEmpty();
        assertThat(store.consumeOnce("hash-4", "E10001", NOW)).isEmpty();
        assertThat(store.findByTokenHash("hash-4", NOW.minusSeconds(1))).isEmpty();
    }

    @Test
    @DisplayName("대기 거래의 필수 값은 null을 허용하지 않는다")
    void transaction_requiresAllValues() {
        assertThatThrownBy(() -> new LoginPendingTransaction(null, "E10001", NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LoginPendingTransaction("hash", null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LoginPendingTransaction("hash", "E10001", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("만료 판정은 만료 시각을 포함한다")
    void isExpiredAt_includesExpiryInstant() {
        LoginPendingTransaction transaction =
                new LoginPendingTransaction("hash-5", "E10001", NOW.plusSeconds(10));

        assertThat(transaction.isExpiredAt(NOW.plusSeconds(9))).isFalse();
        assertThat(transaction.isExpiredAt(NOW.plusSeconds(10))).isTrue();
    }
}
