package com.kdb.it.common.mfa.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.mfa.domain.LoginPendingTransaction;
import com.kdb.it.config.JpaAuditConfig;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JpaLoginPendingTransactionStore의 조건부 삭제 기반 1회 소비를 실 Oracle로 검증한다.
 *
 * <p>{@code @DataJpaTest} 슬라이스는 {@code @Component}와 JPA Auditing 설정을 포함하지 않으므로 검증 대상 저장소 빈과 {@link
 * JpaAuditConfig}를 명시적으로 가져온다. {@code JpaAuditConfig} 없이는 {@code BaseEntity}의 {@code
 * @CreatedDate}/{@code @LastModifiedDate}(FST_ENR_DTM/LST_CHG_DTM, 물리 NOT NULL)가 채워지지 않아 저장이
 * 실패한다.
 */
@Import({JpaLoginPendingTransactionStore.class, JpaAuditConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaLoginPendingTransactionStoreIT extends AbstractOracleRepositoryTest {

    @Autowired private JpaLoginPendingTransactionStore store;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM TPRMPP_CMFADM WHERE ENO = ?", "ITEST01");
    }

    @Test
    @DisplayName("소유자가 소비하면 한 번만 성공하고 이후 조회되지 않는다")
    void consumeOnce_owner_succeedsExactlyOnceThenDisappears() {
        Instant now = Instant.now();
        String tokenHash = "it-mfadm-" + UUID.randomUUID();
        store.save(new LoginPendingTransaction(tokenHash, "ITEST01", now.plusSeconds(90)));

        assertThat(store.consumeOnce(tokenHash, "ITEST01", now)).isPresent();
        assertThat(store.consumeOnce(tokenHash, "ITEST01", now)).isEmpty();
        assertThat(store.findByTokenHash(tokenHash, now)).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자는 소비하지 못하고 거래도 사라지지 않는다")
    void consumeOnce_otherUser_keepsTransaction() {
        Instant now = Instant.now();
        String tokenHash = "it-mfadm-" + UUID.randomUUID();
        store.save(new LoginPendingTransaction(tokenHash, "ITEST01", now.plusSeconds(90)));

        assertThat(store.consumeOnce(tokenHash, "OTHERUSR", now)).isEmpty();
        assertThat(store.findByTokenHash(tokenHash, now)).isPresent();
    }
}
