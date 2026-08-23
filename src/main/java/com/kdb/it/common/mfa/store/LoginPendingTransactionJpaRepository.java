package com.kdb.it.common.mfa.store;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 로그인 대기 거래({@code TPRMPP_CMFADM}) 데이터 접근 리포지토리. */
public interface LoginPendingTransactionJpaRepository
        extends JpaRepository<LoginPendingTransactionEntity, String> {

    @Query(
            "SELECT e FROM LoginPendingTransactionEntity e WHERE e.tokenHash = :tokenHash AND e.endDtm > :now")
    Optional<LoginPendingTransactionEntity> findActiveByTokenHash(
            @Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /** 상태 컬럼이 없어 1회 소비를 조건부 물리 삭제로 표현한다. 영향 행 수 1이 성공이다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "DELETE FROM LoginPendingTransactionEntity e "
                    + "WHERE e.tokenHash = :tokenHash AND e.eno = :eno AND e.endDtm > :now")
    int consumeOnce(
            @Param("tokenHash") String tokenHash,
            @Param("eno") String eno,
            @Param("now") LocalDateTime now);

    /**
     * 만료 후 유예 시간이 지난 행을 물리 삭제한다(용량 관리 전용).
     *
     * <p>{@code END_DTM IS NULL}도 함께 지운다 — {@code V20260820_007}이 {@code TPRMPP_CMFADM.END_DTM}의
     * NOT NULL을 해제해 조회({@code e.endDtm > :now})에도 정리에도 걸리지 않는 행이 생길 수 있다(BE-70).
     */
    @Modifying
    @Query(
            "DELETE FROM LoginPendingTransactionEntity e WHERE e.endDtm < :cutoff OR e.endDtm IS NULL")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
