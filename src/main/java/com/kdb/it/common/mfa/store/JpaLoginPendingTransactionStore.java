package com.kdb.it.common.mfa.store;

import com.kdb.it.common.mfa.domain.LoginPendingTransaction;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 공유 테이블({@code TPRMPP_CMFADM})로 다중 인스턴스 배포를 지원하는 로그인 대기 거래 저장소다.
 *
 * <p>상태 컬럼이 없어 1회 소비를 조건부 물리 삭제로 구현한다. 삭제 전 조회는 반환값 구성용이며, 실제
 * "정확히 한 번" 보장은 {@link LoginPendingTransactionJpaRepository#consumeOnce}의 영향 행 수가 담당한다
 * (동시 요청이 같은 행을 봐도 DELETE는 한 트랜잭션만 성공한다).
 */
@Component
@ConditionalOnProperty(prefix = "app.mfa", name = "store", havingValue = "jpa", matchIfMissing = true)
public class JpaLoginPendingTransactionStore implements LoginPendingTransactionStore {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final LoginPendingTransactionJpaRepository repository;

    public JpaLoginPendingTransactionStore(LoginPendingTransactionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(LoginPendingTransaction transaction) {
        repository.save(
                LoginPendingTransactionEntity.create(
                        transaction.tokenHash(), transaction.eno(), toLocalDateTime(transaction.expiresAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoginPendingTransaction> findByTokenHash(String tokenHash, Instant now) {
        return repository.findActiveByTokenHash(tokenHash, toLocalDateTime(now)).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<LoginPendingTransaction> consumeOnce(String tokenHash, String eno, Instant now) {
        LocalDateTime nowDtm = toLocalDateTime(now);
        Optional<LoginPendingTransactionEntity> found = repository.findActiveByTokenHash(tokenHash, nowDtm);
        if (found.isEmpty() || !found.get().getEno().equals(eno)) {
            return Optional.empty();
        }
        if (repository.consumeOnce(tokenHash, eno, nowDtm) != 1) {
            return Optional.empty();
        }
        return found.map(this::toDomain);
    }

    private LoginPendingTransaction toDomain(LoginPendingTransactionEntity entity) {
        return new LoginPendingTransaction(
                entity.getTokenHash(), entity.getEno(), toInstant(entity.getEndDtm()));
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZONE);
    }

    private static Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZONE).toInstant();
    }
}
