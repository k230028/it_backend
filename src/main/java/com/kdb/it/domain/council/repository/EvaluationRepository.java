package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.dto.EvaluationItemAvgRow;
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
     * @param itPtlAsctId 협의회ID
     * @param delYn  삭제여부 ('N')
     * @return 해당 협의회의 전체 평가의견 목록
     */
    List<Bevalm> findByItPtlAsctIdAndDelYn(String itPtlAsctId, String delYn);

    /**
     * 특정 위원의 평가의견 목록 조회
     *
     * @param itPtlAsctId 협의회ID
     * @param eno    사번
     * @param delYn  삭제여부 ('N')
     * @return 해당 위원의 평가의견 목록 (최대 6개)
     */
    List<Bevalm> findByItPtlAsctIdAndEnoAndDelYn(String itPtlAsctId, String eno, String delYn);

    /**
     * 특정 위원의 특정 항목 평가의견 단건 조회
     *
     * @param itPtlAsctId   협의회ID
     * @param eno      사번
     * @param itPtlCkgItmTc  점검항목코드
     * @param delYn    삭제여부 ('N')
     * @return 평가의견 (없으면 empty)
     */
    Optional<Bevalm> findByItPtlAsctIdAndEnoAndItPtlCkgItmTcAndDelYn(
            String itPtlAsctId, String eno, String itPtlCkgItmTc, String delYn);

    /**
     * 점검항목별 평균 점수 조회
     *
     * <p>결과서 2page의 평균점수 산출에 사용됩니다.</p>
     *
     * @param itPtlAsctId  협의회ID
     * @param delYn   삭제여부 ('N')
     * @return 항목코드, 평균점수 배열 (Object[]: [itPtlCkgItmTc, avgScore])
     */
    @Query(value = """
            SELECT IT_PTL_CKG_ITM_TC, AVG(QUEL_RCRD)
            FROM TPRMPP_BEVALM
            WHERE IT_PTL_ASCT_ID = :itPtlAsctId AND DEL_YN = :delYn
            GROUP BY IT_PTL_CKG_ITM_TC
            ORDER BY IT_PTL_CKG_ITM_TC
            """, nativeQuery = true)
    List<Object[]> findAverageScoreByItem(@Param("itPtlAsctId") String itPtlAsctId, @Param("delYn") String delYn);

    /**
     * 협의회별 평가자(ENO)별 제출 항목 수 집계 (#4 N+1 제거 — per-evaluator COUNT 루프 대체).
     *
     * <p>{@code completeCouncil}의 평가자별 6항목 제출 검증을 협의회ID당 1회 GROUP BY로 수렴시킨다.
     * 반환은 {@code Object[]{eno, count}} 목록이며, 서비스에서 {@code Map<eno, Long>}으로 접어 사용한다.
     * JPQL이므로 Hibernate가 {@code count(e)}를 {@code Long}, {@code e.eno}를 {@code String}으로
     * 자동 매핑한다(네이티브 타입 quirk 헬퍼 불필요).</p>
     *
     * @param itPtlAsctId 협의회ID
     * @param delYn       삭제여부 ('N')
     * @return {@code Object[]{eno, count}} 목록 (제출 이력이 있는 평가자만 포함)
     */
    @Query("SELECT e.eno, COUNT(e) FROM Bevalm e "
            + "WHERE e.itPtlAsctId = :itPtlAsctId AND e.delYn = :delYn "
            + "GROUP BY e.eno")
    List<Object[]> countByEnoForCouncil(@Param("itPtlAsctId") String itPtlAsctId, @Param("delYn") String delYn);

    /**
     * 항목별 평균점수를 DTO로 봉인 반환한다(#6).
     *
     * <p>native {@link #findAverageScoreByItem(String, String)}의 {@code Object[]}를
     * {@link EvaluationItemAvgRow#fromRow(Object[])}로 매핑해 인덱스 캐스팅을 봉인한다.</p>
     *
     * @param itPtlAsctId 협의회ID
     * @param delYn       삭제여부 ('N')
     * @return 항목별 평균점수 DTO 목록
     */
    default List<EvaluationItemAvgRow> findAvgRowsByItem(String itPtlAsctId, String delYn) {
        return findAverageScoreByItem(itPtlAsctId, delYn).stream()
                .map(EvaluationItemAvgRow::fromRow)
                .toList();
    }
}
