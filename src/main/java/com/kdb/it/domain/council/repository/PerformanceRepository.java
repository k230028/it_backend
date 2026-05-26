package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Bperfm;
import com.kdb.it.domain.council.entity.BperfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 성과관리 자체계획(Bperfm) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BPERFM}</p>
 *
 * <p>협의회 1건당 1개 이상의 성과지표를 동적으로 관리합니다.</p>
 */
public interface PerformanceRepository extends JpaRepository<Bperfm, BperfmId> {

    /**
     * 협의회별 성과지표 목록 조회 (삭제되지 않은 항목, 순번 오름차순)
     *
     * @param asctId 협의회ID
     * @param delYn  삭제여부 ('N')
     * @return 해당 협의회의 성과지표 목록
     */
    List<Bperfm> findByAsctIdAndDelYnOrderByDtpSnoAsc(String asctId, String delYn);

    /**
     * 협의회의 다음 성과지표 순번 계산
     *
     * <p>성과지표 추가 시 자동으로 순번을 채번합니다.
     * 삭제된 항목은 제외하고 현재 최대 순번 + 1을 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return 다음 순번 (기존 항목 없으면 1)
     */
    @Query(value = "SELECT NVL(MAX(DTP_SNO), 0) + 1 FROM TPRMPP_BPERFM WHERE ASCT_ID = :asctId AND DEL_YN = 'N'",
           nativeQuery = true)
    Integer getNextDtpSno(@Param("asctId") String asctId);
}
