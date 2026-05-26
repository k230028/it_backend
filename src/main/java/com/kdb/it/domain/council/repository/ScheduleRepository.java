package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Bschdm;
import com.kdb.it.domain.council.entity.BschdmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 협의회 일정(Bschdm) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BSCHDM}</p>
 *
 * <p>평가위원별 가능 일정을 수집하고, IT관리자가 최종 일정을 확정합니다.</p>
 */
public interface ScheduleRepository extends JpaRepository<Bschdm, BschdmId> {

    /**
     * 협의회별 일정 전체 조회 (삭제되지 않은 항목)
     *
     * <p>IT관리자가 위원별 응답 현황을 확인할 때 사용합니다.</p>
     *
     * @param asctId 협의회ID
     * @param delYn  삭제여부 ('N')
     * @return 해당 협의회의 전체 일정 응답 목록
     */
    List<Bschdm> findByAsctIdAndDelYn(String asctId, String delYn);

    /**
     * 특정 위원의 협의회 일정 응답 목록 조회
     *
     * @param asctId 협의회ID
     * @param eno    사번
     * @param delYn  삭제여부 ('N')
     * @return 해당 위원의 일정 응답 목록
     */
    List<Bschdm> findByAsctIdAndEnoAndDelYn(String asctId, String eno, String delYn);

    /**
     * 특정 날짜/시간대의 일정 단건 조회
     *
     * @param asctId 협의회ID
     * @param eno    사번
     * @param dsdDt  일정일자
     * @param dsdTm  일정시간
     * @param delYn  삭제여부 ('N')
     * @return 일정 응답 (없으면 empty)
     */
    Optional<Bschdm> findByAsctIdAndEnoAndDsdDtAndDsdTmAndDelYn(
            String asctId, String eno, String dsdDt, String dsdTm, String delYn);

    /**
     * 아직 일정을 입력하지 않은 위원 수 조회
     *
     * <p>전원 입력 완료 여부를 확인하여 일정확정 버튼 활성화 조건에 사용합니다.
     * BCMMTM에 있지만 BSCHDM에 응답이 없는 위원 수를 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return 미응답 위원 수
     */
    @Query(value = """
            SELECT COUNT(*) FROM TPRMPP_BCMMTM c
            WHERE c.ASCT_ID = :asctId AND c.DEL_YN = 'N'
            AND NOT EXISTS (
                SELECT 1 FROM TPRMPP_BSCHDM s
                WHERE s.ASCT_ID = c.ASCT_ID AND s.ENO = c.ENO AND s.DEL_YN = 'N'
            )
            """, nativeQuery = true)
    Long countPendingMembers(@Param("asctId") String asctId);
}
