package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Bevalm;
import com.kdb.it.domain.council.entity.BevalmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 평가위원 평가의견(Bevalm) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BEVALM}</p>
 *
 * <p>평가위원별 6개 점검항목에 대한 점수 및 의견을 관리합니다.</p>
 */
public interface EvaluationRepository extends JpaRepository<Bevalm, BevalmId> {

    /**
     * 협의회별 전체 평가의견 조회 (삭제되지 않은 항목)
     *
     * <p>IT관리자가 평균점수 계산 및 결과서 작성 시 사용합니다.</p>
     *
     * @param asctId 협의회ID
     * @param delYn  삭제여부 ('N')
     * @return 해당 협의회의 전체 평가의견 목록
     */
    List<Bevalm> findByAsctIdAndDelYn(String asctId, String delYn);

    /**
     * 특정 위원의 평가의견 목록 조회
     *
     * @param asctId 협의회ID
     * @param eno    사번
     * @param delYn  삭제여부 ('N')
     * @return 해당 위원의 평가의견 목록 (최대 6개)
     */
    List<Bevalm> findByAsctIdAndEnoAndDelYn(String asctId, String eno, String delYn);

    /**
     * 특정 위원의 특정 항목 평가의견 단건 조회
     *
     * @param asctId   협의회ID
     * @param eno      사번
     * @param ckgItmC  점검항목코드
     * @param delYn    삭제여부 ('N')
     * @return 평가의견 (없으면 empty)
     */
    Optional<Bevalm> findByAsctIdAndEnoAndCkgItmCAndDelYn(
            String asctId, String eno, String ckgItmC, String delYn);

    /**
     * 점검항목별 평균 점수 조회
     *
     * <p>결과서 2page의 평균점수 산출에 사용됩니다.</p>
     *
     * @param asctId  협의회ID
     * @param delYn   삭제여부 ('N')
     * @return 항목코드, 평균점수 배열 (Object[]: [ckgItmC, avgScore])
     */
    @Query(value = """
            SELECT CKG_ITM_C, AVG(CKG_RCRD)
            FROM TPRMPP_BEVALM
            WHERE ASCT_ID = :asctId AND DEL_YN = :delYn
            GROUP BY CKG_ITM_C
            ORDER BY CKG_ITM_C
            """, nativeQuery = true)
    List<Object[]> findAverageScoreByItem(@Param("asctId") String asctId, @Param("delYn") String delYn);
}
