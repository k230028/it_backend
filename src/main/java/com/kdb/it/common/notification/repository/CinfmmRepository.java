package com.kdb.it.common.notification.repository;

import com.kdb.it.common.notification.entity.Cinfmm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 마스터 리포지토리
 *
 * <p>표준 CRUD는 {@link JpaRepository}, 동적·집계 쿼리는
 * {@link CinfmmRepositoryCustom} 구현체({@code CinfmmRepositoryImpl})에 위임한다.</p>
 *
 * <p>채번: {@code SEQ_CINFMM.NEXTVAL} Native Query.</p>
 */
public interface CinfmmRepository extends JpaRepository<Cinfmm, String>, CinfmmRepositoryCustom {

    /**
     * 알림 마스터 시퀀스 다음 값 채번.
     *
     * @return 다음 시퀀스 값 (1부터 시작, 99,999,999 도달 시 CYCLE)
     */
    @Query(value = "SELECT SEQ_CINFMM.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextVal();

    /** 재시도 가능한 실패·정체 알림 번호를 오래된 순서로 제한 조회합니다. */
    @Query("select c.infmMsgNo from Cinfmm c where c.delYn = 'N' " +
            "and c.infmSdStsC in :statuses and c.reTryNot < :maxAttempts " +
            "and (c.infmSdStsC = '03' or c.fstEnrDtm <= :pendingBefore) " +
            "order by coalesce(c.sdDtm, c.fstEnrDtm) asc")
    List<String> findRetryableIds(@Param("statuses") List<String> statuses,
                                  @Param("maxAttempts") int maxAttempts,
                                  @Param("pendingBefore") LocalDateTime pendingBefore,
                                  Pageable pageable);
}
