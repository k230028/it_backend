package com.kdb.it.common.mfa.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.mfa.domain.MfaMethod;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.domain.MfaTransaction;
import com.kdb.it.common.mfa.domain.MfaTransactionStatus;
import com.kdb.it.config.JpaAuditConfig;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JpaMfaTransactionStore의 원자적 전이를 실 Oracle로 검증한다.
 *
 * <p>{@code @DataJpaTest} 슬라이스는 {@code @Component}와 JPA Auditing 설정을 포함하지 않으므로 검증 대상 저장소 빈과 {@link
 * JpaAuditConfig}를 명시적으로 가져온다({@code ClangmChangeLogIt}과 같은 이유). {@code JpaAuditConfig} 없이는 {@code
 * BaseEntity}의 {@code @CreatedDate}/{@code @LastModifiedDate}(FST_ENR_DTM/LST_CHG_DTM, 물리 NOT
 * NULL)가 채워지지 않아 저장이 실패한다.
 */
@Import({JpaMfaTransactionStore.class, JpaAuditConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaMfaTransactionStoreIT extends AbstractOracleRepositoryTest {

    @Autowired private JpaMfaTransactionStore store;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String tokenHash;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM TPRMPP_CMFATM WHERE ENO = ?", "ITEST01");
    }

    @Test
    @DisplayName("저장한 거래는 만료 전까지 조회되고 만료 시각부터는 조회되지 않는다")
    void findByTokenHash_expiresAtBoundary() {
        Instant now = Instant.now();
        tokenHash = newToken();
        store.save(pending(tokenHash, now.plusSeconds(90)));

        assertThat(store.findByTokenHash(tokenHash, now)).isPresent();
        assertThat(store.findByTokenHash(tokenHash, now.plusSeconds(90))).isEmpty();
    }

    @Test
    @DisplayName("verifyAndBindProof는 대기 거래를 검증완료로 바꾸고 증표 해시를 결속한다")
    void verifyAndBindProof_pendingTransaction_becomesVerified() {
        Instant now = Instant.now();
        tokenHash = newToken();
        store.save(pending(tokenHash, now.plusSeconds(90)));

        MfaTransaction verified =
                store.verifyAndBindProof(tokenHash, "proof-" + tokenHash, now).orElseThrow();

        assertThat(verified.status()).isEqualTo(MfaTransactionStatus.VERIFIED);
        assertThat(verified.proofHash()).isEqualTo("proof-" + tokenHash);
    }

    @Test
    @DisplayName("저장한 svcTrId는 조회 시 그대로 돌아온다")
    void save_svcTrId_roundTrips() {
        Instant now = Instant.now();
        tokenHash = newToken();
        store.save(
                MfaTransaction.pending(
                        tokenHash,
                        "ITEST01",
                        MfaPurpose.LOGIN,
                        MfaMethod.FIDO,
                        now.plusSeconds(90),
                        "try-hash",
                        "12345678901234567890"));

        assertThat(store.findByTokenHash(tokenHash, now))
                .get()
                .extracting(MfaTransaction::svcTrId)
                .isEqualTo("12345678901234567890");
    }

    @Test
    @DisplayName("isExpired는 만료 전에는 false, 만료 후에는 true다")
    void isExpired_reflectsEndDtm() {
        Instant now = Instant.now();
        tokenHash = newToken();
        store.save(pending(tokenHash, now.plusSeconds(1)));

        assertThat(store.isExpired(tokenHash, now)).isFalse();
        assertThat(store.isExpired(tokenHash, now.plusSeconds(2))).isTrue();
        assertThat(store.isExpired("absent-token", now)).isFalse();
    }

    @Test
    @DisplayName("취소된 거래는 삭제되지 않고 CANCELLED 상태로 계속 조회된다")
    void delete_pendingTransaction_becomesCancelledButStillFound() {
        Instant now = Instant.now();
        tokenHash = newToken();
        store.save(pending(tokenHash, now.plusSeconds(90)));

        MfaTransaction cancelled = store.delete(tokenHash, now).orElseThrow();

        assertThat(cancelled.status()).isEqualTo(MfaTransactionStatus.CANCELLED);
        assertThat(store.findByTokenHash(tokenHash, now)).isPresent();
    }

    @Test
    @DisplayName("동시 증표 소비 두 건 중 정확히 한 건만 CONSUMED다")
    void consumeVerifiedOnce_concurrentConsumers_exactlyOneSucceeds() throws Exception {
        Instant now = Instant.now();
        tokenHash = newToken();
        String proofHash = "proof-" + tokenHash;
        store.save(pending(tokenHash, now.plusSeconds(90)));
        store.verifyAndBindProof(tokenHash, proofHash, now);

        Callable<MfaTransactionStore.ProofConsumption> consume =
                () -> store.consumeVerifiedOnce(proofHash, "ITEST01", MfaPurpose.LOGIN, now);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<MfaTransactionStore.ProofConsumption>> results =
                    executor.invokeAll(List.of(consume, consume));
            List<MfaTransactionStore.ProofConsumption> outcomes =
                    List.of(results.get(0).get(), results.get(1).get());
            assertThat(outcomes)
                    .containsExactlyInAnyOrder(
                            MfaTransactionStore.ProofConsumption.CONSUMED,
                            MfaTransactionStore.ProofConsumption.REJECTED);
        }
    }

    private static MfaTransaction pending(String tokenHash, Instant expiresAt) {
        return MfaTransaction.pending(
                tokenHash, "ITEST01", MfaPurpose.LOGIN, MfaMethod.FIDO, expiresAt);
    }

    private static String newToken() {
        return "it-mfatm-" + UUID.randomUUID();
    }
}
