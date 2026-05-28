package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Basctm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 협의회 기본정보(Basctm) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BASCTM}</p>
 *
 * <p>Soft Delete 패턴 적용: 조회 시 항상 {@code delYn='N'} 조건을 사용합니다.</p>
 */
public interface CouncilRepository extends JpaRepository<Basctm, String> {

    /**
     * 협의회 단건 조회 (삭제되지 않은 항목)
     *
     * @param asctId 협의회ID
     * @param delYn  삭제여부 ('N')
     * @return 협의회 (없으면 empty)
     */
    Optional<Basctm> findByAsctIdAndDelYn(String asctId, String delYn);

    /**
     * 전체 협의회 목록 조회 (관리자용, 삭제되지 않은 항목)
     *
     * @param delYn 삭제여부 ('N')
     * @return 전체 협의회 목록
     */
    List<Basctm> findAllByDelYn(String delYn);

    /**
     * 프로젝트 기준 협의회 목록 조회 (삭제되지 않은 항목)
     *
     * <p>특정 사업의 협의회 진행이력 조회 시 사용합니다.</p>
     *
     * @param prjMngNo 프로젝트관리번호
     * @param delYn    삭제여부 ('N')
     * @return 해당 사업의 협의회 목록
     */
    List<Basctm> findByPrjMngNoAndDelYn(String prjMngNo, String delYn);

    /**
     * Oracle 시퀀스(SEQ_BASCTM) 다음 값 조회
     *
     * <p>새로운 협의회 생성 시 ASCT_ID 채번에 사용합니다.
     * ID 형식: {@code ASCT-{연도}-{4자리}} (예: ASCT-2026-0001)</p>
     *
     * @return 시퀀스의 다음 값
     */
    @Query(value = "SELECT SEQ_BASCTM.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();

    /**
     * 사업 PRJ_STS 업데이트 (협의회 신청 시 상태 전이용)
     *
     * @param prjMngNo 프로젝트관리번호
     * @param prjSno   프로젝트순번
     * @param prjSts   변경할 상태값
     */
    @Modifying
    @Query(value = "UPDATE TPRMPP_BPROJM SET PRJ_STS = :prjSts WHERE PRJ_MNG_NO = :prjMngNo AND PRJ_SNO = :prjSno",
            nativeQuery = true)
    int updateProjectStatus(@Param("prjMngNo") String prjMngNo,
                            @Param("prjSno") Integer prjSno,
                            @Param("prjSts") String prjSts);

    /**
     * 소관부서(BBR_C) 기준 협의회 목록 조회 (일반사용자용)
     *
     * <p>ITPZZ001 권한 사용자는 자신의 소속 부서 사업에 해당하는 협의회만 조회합니다.
     * BPROJM과 조인하여 사업 주관부서 기준으로 필터링합니다.</p>
     *
     * @param bbrC  소속부서코드
     * @param delYn 삭제여부 ('N')
     * @return 해당 부서의 협의회 목록
     */
    @Query(value = """
            SELECT a.* FROM TPRMPP_BASCTM a
            JOIN TPRMPP_BPROJM p ON a.PRJ_MNG_NO = p.PRJ_MNG_NO AND a.PRJ_SNO = p.PRJ_SNO
            WHERE p.BBR_C = :bbrC AND a.DEL_YN = :delYn
            ORDER BY a.FST_ENR_DTM DESC
            """, nativeQuery = true)
    List<Basctm> findByDepartment(@Param("bbrC") String bbrC, @Param("delYn") String delYn);

    /**
     * 평가위원으로 배정된 협의회 목록 조회
     *
     * <p>평가위원 권한 사용자는 BCMMTM에 ENO가 있는 협의회만 조회합니다.</p>
     *
     * @param eno   사번
     * @param delYn 삭제여부 ('N')
     * @return 해당 위원이 배정된 협의회 목록
     */
    @Query(value = """
            SELECT a.* FROM TPRMPP_BASCTM a
            JOIN TPRMPP_BCMMTM c ON a.ASCT_ID = c.ASCT_ID
            WHERE c.ENO = :eno AND a.DEL_YN = :delYn AND c.DEL_YN = :delYn
            ORDER BY a.FST_ENR_DTM DESC
            """, nativeQuery = true)
    List<Basctm> findByCommitteeMember(@Param("eno") String eno, @Param("delYn") String delYn);

    /**
     * 관리자용 협의회 신청 대상 목록 조회 (전체 부서, 통합)
     *
     * <p>부서 필터 없이 전체 사업을 대상으로 조회합니다.</p>
     * <ul>
     *   <li>협의회 미신청: PRJ_STS = '예산 작성' + 결재완료(APF_STS='결재완료') 사업</li>
     *   <li>협의회 신청된 건: PRJ_STS = '정실협 진행중'</li>
     * </ul>
     *
     * @return prjMngNo, prjSno, prjNm, asctId(null 가능), asctStsC(null 가능),
     *         dbrTc(null 가능), cnrcDt(null 가능), applied(0/1) 컬럼 순서의 결과
     */
    @Query(value = """
            SELECT
                p.PRJ_MNG_NO    AS prjMngNo,
                p.PRJ_SNO       AS prjSno,
                p.PRJ_NM        AS prjNm,
                a.ASCT_ID       AS asctId,
                a.ASCT_STS_C      AS asctStsC,
                a.DBR_TC        AS dbrTc,
                a.CNRC_DT       AS cnrcDt,
                a.CNRC_TM       AS cnrcTm,
                CASE WHEN a.ASCT_ID IS NOT NULL THEN 1 ELSE 0 END AS applied,
                p.BG_YY         AS prjYy,
                p.PRJ_TP        AS prjTp,
                p.SVN_DPM       AS svnDpm,
                p.PRJ_BG        AS prjBg,
                p.STT_DT        AS sttDt,
                p.END_DT        AS endDt,
                p.IT_DPM        AS itDpm,
                p.PRJ_DES       AS prjDes
            FROM TPRMPP_BPROJM p
            LEFT JOIN TPRMPP_BASCTM a
                ON p.PRJ_MNG_NO = a.PRJ_MNG_NO
               AND p.PRJ_SNO    = a.PRJ_SNO
               AND a.DEL_YN     = 'N'
            WHERE p.DEL_YN = 'N'
              AND (
                  (a.ASCT_ID IS NOT NULL AND p.PRJ_STS = :stsInProgress)
                  OR
                  (a.ASCT_ID IS NULL AND p.PRJ_STS IN (:stsPending1, :stsPending2)
                  AND EXISTS (
                      SELECT 1
                      FROM TPRMPP_CAPPLA ca
                      JOIN TPRMPP_CAPPLM cm ON ca.APF_DCM_NO = cm.APF_DCM_NO
                      WHERE ca.FNT_TB_NM   = 'BPROJM'
                        AND ca.PK_COL_NM   = p.PRJ_MNG_NO
                        AND ca.FNT_TB_CRY_SNO = p.PRJ_SNO
                        AND cm.APF_PRG_STS_C = :apfSts
                        AND ca.APF_SNO = (
                            SELECT MAX(ca2.APF_SNO)
                            FROM TPRMPP_CAPPLA ca2
                            WHERE ca2.FNT_TB_NM   = 'BPROJM'
                              AND ca2.PK_COL_NM   = p.PRJ_MNG_NO
                              AND ca2.FNT_TB_CRY_SNO = p.PRJ_SNO
                        )
                  ))
              )
            ORDER BY p.FST_ENR_DTM DESC
            """, nativeQuery = true)
    List<Object[]> findProjectsForCouncilAll(
            @Param("stsInProgress") String stsInProgress,
            @Param("stsPending1") String stsPending1,
            @Param("stsPending2") String stsPending2,
            @Param("apfSts") String apfSts);

    /**
     * 일반사용자용 협의회 신청 대상 목록 조회 (통합)
     *
     * <p>부서 필터: 사용자의 BBR_C = BPROJM.SVN_DPM</p>
     * <ul>
     *   <li>협의회 미신청: PRJ_STS = '예산 작성' + 결재완료(APF_STS='결재완료') 사업</li>
     *   <li>협의회 신청된 건: PRJ_STS = '정실협 진행중'</li>
     * </ul>
     *
     * @param svnDpm 사용자 소속부서코드 (CustomUserDetails.getBbrC())
     * @return prjMngNo, prjSno, prjNm, asctId(null 가능), asctStsC(null 가능),
     *         dbrTc(null 가능), cnrcDt(null 가능), applied(0/1) 컬럼 순서의 결과
     */
    @Query(value = """
            SELECT
                p.PRJ_MNG_NO    AS prjMngNo,
                p.PRJ_SNO       AS prjSno,
                p.PRJ_NM        AS prjNm,
                a.ASCT_ID       AS asctId,
                a.ASCT_STS_C      AS asctStsC,
                a.DBR_TC        AS dbrTc,
                a.CNRC_DT       AS cnrcDt,
                a.CNRC_TM       AS cnrcTm,
                CASE WHEN a.ASCT_ID IS NOT NULL THEN 1 ELSE 0 END AS applied,
                p.BG_YY         AS prjYy,
                p.PRJ_TP        AS prjTp,
                p.SVN_DPM       AS svnDpm,
                p.PRJ_BG        AS prjBg,
                p.STT_DT        AS sttDt,
                p.END_DT        AS endDt,
                p.IT_DPM        AS itDpm,
                p.PRJ_DES       AS prjDes
            FROM TPRMPP_BPROJM p
            LEFT JOIN TPRMPP_BASCTM a
                ON p.PRJ_MNG_NO = a.PRJ_MNG_NO
               AND p.PRJ_SNO    = a.PRJ_SNO
               AND a.DEL_YN     = 'N'
            WHERE p.SVN_DPM = :svnDpm
              AND p.DEL_YN  = 'N'
              AND (
                  (a.ASCT_ID IS NOT NULL AND p.PRJ_STS = :stsInProgress)
                  OR
                  (a.ASCT_ID IS NULL AND p.PRJ_STS IN (:stsPending1, :stsPending2)
                  AND EXISTS (
                      SELECT 1
                      FROM TPRMPP_CAPPLA ca
                      JOIN TPRMPP_CAPPLM cm ON ca.APF_DCM_NO = cm.APF_DCM_NO
                      WHERE ca.FNT_TB_NM   = 'BPROJM'
                        AND ca.PK_COL_NM   = p.PRJ_MNG_NO
                        AND ca.FNT_TB_CRY_SNO = p.PRJ_SNO
                        AND cm.APF_PRG_STS_C = :apfSts
                        AND ca.APF_SNO = (
                            SELECT MAX(ca2.APF_SNO)
                            FROM TPRMPP_CAPPLA ca2
                            WHERE ca2.FNT_TB_NM   = 'BPROJM'
                              AND ca2.PK_COL_NM   = p.PRJ_MNG_NO
                              AND ca2.FNT_TB_CRY_SNO = p.PRJ_SNO
                        )
                  ))
              )
            ORDER BY p.FST_ENR_DTM DESC
            """, nativeQuery = true)
    List<Object[]> findProjectsForCouncilByDepartment(
            @Param("svnDpm") String svnDpm,
            @Param("stsInProgress") String stsInProgress,
            @Param("stsPending1") String stsPending1,
            @Param("stsPending2") String stsPending2,
            @Param("apfSts") String apfSts);
}
