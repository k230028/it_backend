package com.kdb.it.common.mfa.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.config.JpaAuditConfig;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 만료 후 10분 유예 경계에서 정리 배치가 올바른 행만 지우는지 실 Oracle로 검증한다.
 *
 * <p>{@code @DataJpaTest} 슬라이스는 JPA Auditing 설정을 포함하지 않으므로 {@link JpaAuditConfig}를 명시적으로 가져온다({@code
 * JpaMfaTransactionStoreIT}와 같은 이유). {@code JpaAuditConfig} 없이는 {@code MfaTransactionEntity.create}가 채우지
 * 않는 {@code BaseEntity}의 {@code @CreatedDate}/{@code @LastModifiedDate}(FST_ENR_DTM/LST_CHG_DTM, 물리 NOT
 * NULL)가 채워지지 않아 저장이 실패한다.
 *
 * <p>{@code scheduler}는 {@code new}로 직접 만든 평범한 객체라 {@code cleanup()}의 {@code @Transactional}이 AOP로
 * 적용되지 않는다. 클래스 트랜잭션을 {@link Propagation#NOT_SUPPORTED}로 두어 테스트 메서드의 앰비언트 트랜잭션을 없애고,
 * {@link TransactionTemplate}으로 {@code cleanup()} 호출만 별도 트랜잭션에 감싼다. 이렇게 커밋된 삭제 결과를, 이후
 * {@code findById}가 여는 새 트랜잭션(신규 영속성 컨텍스트)에서 다시 조회해야 1차 캐시에 남은 예전 관리 인스턴스가 삭제 여부를 가리는 것을
 * 피할 수 있다({@code save} 시점에 이미 영속화된 엔티티가 벌크 DELETE 이후에도 캐시에 남는 JPA 벌크 연산의 특성 때문).
 */
@Import(JpaAuditConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MfaTransactionCleanupSchedulerIT extends AbstractOracleRepositoryTest {

    @Autowired private MfaTransactionJpaRepository transactionRepository;
    @Autowired private LoginPendingTransactionJpaRepository pendingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM TPRMPP_CMFATM WHERE ENO = ?", "ITEST01");
        jdbcTemplate.update("DELETE FROM TPRMPP_CMFADM WHERE ENO = ?", "ITEST01");
    }

    @Test
    @DisplayName("유예 10분 이전 행은 남기고 이후 행만 지운다")
    void cleanup_deletesOnlyRowsPastGracePeriod() {
        MfaTransactionCleanupScheduler scheduler =
                new MfaTransactionCleanupScheduler(transactionRepository, pendingRepository, CLOCK);
        String withinGrace = "it-cleanup-within-" + UUID.randomUUID();
        String pastGrace = "it-cleanup-past-" + UUID.randomUUID();
        transactionRepository.save(
                MfaTransactionEntity.create(
                        withinGrace, "ITEST01", "10", "20",
                        toLocalDateTime(NOW.minusSeconds(9 * 60)), null, null));
        transactionRepository.save(
                MfaTransactionEntity.create(
                        pastGrace, "ITEST01", "10", "20",
                        toLocalDateTime(NOW.minusSeconds(11 * 60)), null, null));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> scheduler.cleanup());

        assertThat(transactionRepository.findById(withinGrace)).isPresent();
        assertThat(transactionRepository.findById(pastGrace)).isEmpty();
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
