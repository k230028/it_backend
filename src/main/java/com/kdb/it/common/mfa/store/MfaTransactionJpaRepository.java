package com.kdb.it.common.mfa.store;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 추가인증 거래({@code TPRMPP_CMFATM}) 데이터 접근 리포지토리
 *
 * <p>모든 상태 전이를 조건부 {@code UPDATE} 한 문장으로 표현한다. 영향 행 수 1이 성공이며, 동시 요청 중 정확히 한 건만 1을 받으므로 원자성은 DB가
 * 담당한다(분산 락 불필요).
 */
public interface MfaTransactionJpaRepository extends JpaRepository<MfaTransactionEntity, String> {

    /** 만료되지 않은 거래만 조회한다. 만료 판정은 EXPIRED로 저장하지 않고 END_DTM 비교로만 한다. */
    @Query(
            "SELECT e FROM MfaTransactionEntity e WHERE e.tokenHash = :tokenHash AND e.endDtm > :now")
    Optional<MfaTransactionEntity> findActiveByTokenHash(
            @Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /** 증표 해시로 조회한다. consumeVerifiedOnce가 0행일 때 사유(만료/거부/없음)를 판별하는 용도다. */
    @Query("SELECT e FROM MfaTransactionEntity e WHERE e.proofTokenHash = :proofHash")
    Optional<MfaTransactionEntity> findByProofTokenHash(@Param("proofHash") String proofHash);

    /** 대기 중인 거래를 검증완료로 전이하며 증표 해시를 결속한다(메모리 구현의 두 맵 전이를 대체). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE MfaTransactionEntity e SET e.statusCode = '20', e.vrfDtm = :now, "
                    + "e.proofTokenHash = :proofHash "
                    + "WHERE e.tokenHash = :tokenHash AND e.statusCode = '10' AND e.endDtm > :now")
    int verifyAndBindProof(
            @Param("tokenHash") String tokenHash,
            @Param("proofHash") String proofHash,
            @Param("now") LocalDateTime now);

    /**
     * 실패 횟수를 올리고 최대 횟수 도달 시 잠근다. 증가와 잠금 판정을 한 문장으로 처리한다.
     *
     * <p>{@code V20260820_007}이 {@code FLUR_NOT}의 NOT NULL을 해제해 DB가 더 이상 값을 보증하지 않으므로 {@code
     * COALESCE}로 0을 깔아 둔다. NULL이면 {@code e.failureCount + 1}이 SQL에서 NULL이 되어 실패 잠금이 발화하지
     * 않는다(BE-70).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE MfaTransactionEntity e SET e.failureCount = COALESCE(e.failureCount, 0) + 1, "
                    + "e.statusCode = CASE WHEN COALESCE(e.failureCount, 0) + 1 >= :maxFailures THEN '30' ELSE '10' END "
                    + "WHERE e.tokenHash = :tokenHash AND e.statusCode = '10' AND e.endDtm > :now")
    int fail(
            @Param("tokenHash") String tokenHash,
            @Param("now") LocalDateTime now,
            @Param("maxFailures") int maxFailures);

    /** 대기 중인 거래를 취소로 전이한다. 물리 삭제하지 않아 이후 조회가 사유를 정확히 구분할 수 있다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE MfaTransactionEntity e SET e.statusCode = '40' "
                    + "WHERE e.tokenHash = :tokenHash AND e.statusCode = '10' AND e.endDtm > :now")
    int cancel(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /** 검증완료 거래의 증표를 정확히 한 번 소비한다. 동시 요청 중 한 건만 영향 행 수 1을 받는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE MfaTransactionEntity e SET e.statusCode = '50' "
                    + "WHERE e.proofTokenHash = :proofHash AND e.eno = :eno "
                    + "AND e.purposeCode = :purposeCode AND e.statusCode = '20' AND e.endDtm > :now")
    int consumeVerifiedOnce(
            @Param("proofHash") String proofHash,
            @Param("eno") String eno,
            @Param("purposeCode") String purposeCode,
            @Param("now") LocalDateTime now);

    /**
     * 만료 후 유예 시간이 지난 행을 물리 삭제한다(용량 관리 전용, 정확성과 무관).
     *
     * <p>{@code END_DTM IS NULL}도 함께 지운다. 업무 조회는 모두 {@code e.endDtm > :now}라 NULL 행은 조회되지 않는데, 정리
     * 술어까지 {@code e.endDtm < :cutoff}만 보면 어느 쪽에도 걸리지 않아 영구 잔존한다. {@code V20260820_007}이 {@code
     * END_DTM}의 NOT NULL을 해제해 DB가 더 이상 값을 보증하지 않으므로 정리 쪽에서 받아 낸다(BE-70).
     */
    @Modifying
    @Query("DELETE FROM MfaTransactionEntity e WHERE e.endDtm < :cutoff OR e.endDtm IS NULL")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
