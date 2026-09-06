package com.kdb.it.common.notification.repository;

import com.kdb.it.common.notification.entity.Cinfmm;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 알림 마스터 리포지토리
 *
 * <p>표준 CRUD는 {@link JpaRepository}, 동적·집계 쿼리는 {@link CinfmmRepositoryCustom} 구현체({@code
 * CinfmmRepositoryImpl})에 위임한다.
 *
 * <p>채번: {@code SQ_TPRMPP_CINFMM_1.NEXTVAL} Native Query.
 */
public interface CinfmmRepository extends JpaRepository<Cinfmm, String>, CinfmmRepositoryCustom {

    /**
     * 알림 마스터 시퀀스 다음 값 채번.
     *
     * @return 다음 시퀀스 값 (1부터 시작, 99,999,999 도달 시 CYCLE)
     */
    @Query(value = "SELECT SQ_TPRMPP_CINFMM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextVal();

    /**
     * 발송 처리 전용 알림 행 잠금 조회입니다.
     *
     * <p>다중 인스턴스에서 여러 재시도 스케줄러가 같은 알림을 동시에 집을 수 있으므로, 상태 검사와 외부 발송을 같은 잠금 구간 안에서 수행해 중복 발송을 막습니다.
     * 잠금은 호출자({@code NotificationDispatchService#dispatch})의 {@code REQUIRES_NEW} 트랜잭션 범위에서만 유지됩니다.
     *
     * @param infmMsgNo 알림메시지번호
     * @return 잠긴 알림 행. 없으면 비어 있음
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cinfmm c where c.infmMsgNo = :infmMsgNo")
    Optional<Cinfmm> findByIdForUpdate(@Param("infmMsgNo") String infmMsgNo);

    /** 재시도 가능한 실패·정체 알림 번호를 오래된 순서로 제한 조회합니다. */
    @Query(
            "select c.infmMsgNo from Cinfmm c where c.delYn = 'N' "
                    + "and c.infmSdStsC in :statuses and c.reTryNot < :maxAttempts "
                    + "and (c.infmSdStsC = '03' or c.fstEnrDtm <= :pendingBefore) "
                    + "order by coalesce(c.sdDtm, c.fstEnrDtm) asc")
    List<String> findRetryableIds(
            @Param("statuses") List<String> statuses,
            @Param("maxAttempts") int maxAttempts,
            @Param("pendingBefore") LocalDateTime pendingBefore,
            Pageable pageable);
}
