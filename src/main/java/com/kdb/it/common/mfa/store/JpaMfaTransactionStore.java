package com.kdb.it.common.mfa.store;

import com.kdb.it.common.mfa.domain.MfaMethod;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.domain.MfaTransaction;
import com.kdb.it.common.mfa.domain.MfaTransactionStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 공유 테이블({@code TPRMPP_CMFATM})로 다중 인스턴스 배포를 지원하는 MFA 거래 저장소다.
 *
 * <p>모든 상태 전이를 {@link MfaTransactionJpaRepository}의 조건부 UPDATE에 위임하고, 영향 행 수로 성공 여부를 판정한 뒤 최신 행을 다시
 * 조회해 도메인 객체로 매핑한다.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.mfa",
        name = "store",
        havingValue = "jpa",
        matchIfMissing = true)
public class JpaMfaTransactionStore implements MfaTransactionStore {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final MfaTransactionJpaRepository repository;

    public JpaMfaTransactionStore(MfaTransactionJpaRepository repository) {
        this.repository = repository;
    }

    // 인터페이스 계약(MfaTransactionStore#save)대로 PENDING 신규 거래만 지원한다. status/proofHash/verifiedAt/
    // failureCount는 반영하지 않고 MfaTransactionEntity.create(...)가 status=PENDING, failureCount=0으로
    // 고정한다. 이미 전이된 거래를 넘기면 PENDING/0으로 초기화되어 저장되므로 호출하지 말 것.
    @Override
    @Transactional
    public void save(MfaTransaction transaction) {
        repository.save(
                MfaTransactionEntity.create(
                        transaction.tokenHash(),
                        transaction.eno(),
                        purposeCode(transaction.purpose()),
                        methodCode(transaction.method()),
                        toLocalDateTime(transaction.expiresAt()),
                        transaction.providerChallengeHash(),
                        transaction.svcTrId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MfaTransaction> findByTokenHash(String tokenHash, Instant now) {
        return repository
                .findActiveByTokenHash(tokenHash, toLocalDateTime(now))
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<MfaTransaction> verifyAndBindProof(
            String tokenHash, String proofHash, Instant now) {
        LocalDateTime nowDtm = toLocalDateTime(now);
        if (repository.verifyAndBindProof(tokenHash, proofHash, nowDtm) != 1) {
            return Optional.empty();
        }
        return repository.findActiveByTokenHash(tokenHash, nowDtm).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<MfaTransaction> fail(String tokenHash, Instant now, int maxFailures) {
        LocalDateTime nowDtm = toLocalDateTime(now);
        if (repository.fail(tokenHash, nowDtm, maxFailures) != 1) {
            return Optional.empty();
        }
        return repository.findActiveByTokenHash(tokenHash, nowDtm).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<MfaTransaction> delete(String tokenHash, Instant now) {
        LocalDateTime nowDtm = toLocalDateTime(now);
        if (repository.cancel(tokenHash, nowDtm) != 1) {
            return Optional.empty();
        }
        return repository.findActiveByTokenHash(tokenHash, nowDtm).map(this::toDomain);
    }

    @Override
    @Transactional
    public ProofConsumption consumeVerifiedOnce(
            String tokenHash, String eno, MfaPurpose purpose, Instant now) {
        LocalDateTime nowDtm = toLocalDateTime(now);
        int updated = repository.consumeVerifiedOnce(tokenHash, eno, purposeCode(purpose), nowDtm);
        if (updated == 1) {
            return ProofConsumption.CONSUMED;
        }
        return repository
                .findByProofTokenHash(tokenHash)
                .map(
                        entity ->
                                entity.getEndDtm().isBefore(nowDtm)
                                        ? ProofConsumption.EXPIRED
                                        : ProofConsumption.REJECTED)
                .orElse(ProofConsumption.MISSING);
    }

    private MfaTransaction toDomain(MfaTransactionEntity entity) {
        return MfaTransaction.restore(
                entity.getTokenHash(),
                entity.getEno(),
                purpose(entity.getPurposeCode()),
                method(entity.getMethodCode()),
                toInstant(entity.getEndDtm()),
                entity.getTryTokenHash(),
                entity.getSvcTrNo(),
                entity.getProofTokenHash(),
                status(entity.getStatusCode()),
                entity.getVrfDtm() == null ? null : toInstant(entity.getVrfDtm()),
                entity.getFailureCount());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isExpired(String tokenHash, Instant now) {
        return repository
                .findById(tokenHash)
                .map(entity -> !toLocalDateTime(now).isBefore(entity.getEndDtm()))
                .orElse(false);
    }

    private static String purposeCode(MfaPurpose purpose) {
        return purpose == MfaPurpose.LOGIN ? "10" : "20";
    }

    private static MfaPurpose purpose(String code) {
        return "10".equals(code) ? MfaPurpose.LOGIN : MfaPurpose.APPROVAL;
    }

    private static String methodCode(MfaMethod method) {
        return switch (method) {
            case FINGER_VEIN -> "10";
            case FIDO -> "20";
            case MOTP -> "30";
        };
    }

    private static MfaMethod method(String code) {
        return switch (code) {
            case "10" -> MfaMethod.FINGER_VEIN;
            case "20" -> MfaMethod.FIDO;
            case "30" -> MfaMethod.MOTP;
            default -> throw new IllegalStateException("알 수 없는 IT포탈추가인증수단구분코드: " + code);
        };
    }

    private static MfaTransactionStatus status(String code) {
        return switch (code) {
            case "10" -> MfaTransactionStatus.PENDING;
            case "20" -> MfaTransactionStatus.VERIFIED;
            case "30" -> MfaTransactionStatus.LOCKED;
            case "40" -> MfaTransactionStatus.CANCELLED;
            case "50" -> MfaTransactionStatus.CONSUMED;
            default -> throw new IllegalStateException("알 수 없는 IT포탈추가인증상태구분코드: " + code);
        };
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZONE);
    }

    private static Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZONE).toInstant();
    }
}
