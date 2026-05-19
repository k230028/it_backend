package com.kdb.it.common.notification.repository;

import com.kdb.it.common.notification.entity.Cinfmm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
