package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.dto.CouncilProjectRow;
import com.kdb.it.domain.council.entity.Basctm;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 협의회 기본정보(Basctm) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BASCTM}
 *
 * <p>Soft Delete 패턴 적용: 조회 시 항상 {@code delYn='N'} 조건을 사용합니다.
 */
public interface CouncilRepository extends JpaRepository<Basctm, String> {

    /**
     * 협의회 단건 조회 (삭제되지 않은 항목)
     *
     * @param itPtlAsctId 협의회ID
     * @param delYn 삭제여부 ('N')
     * @return 협의회 (없으면 empty)
     */
    Optional<Basctm> findByItPtlAsctIdAndDelYn(String itPtlAsctId, String delYn);

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
     * <p>특정 사업의 협의회 진행이력 조회 시 사용합니다.
     *
     * @param abusMngNo 프로젝트관리번호
     * @param delYn 삭제여부 ('N')
     * @return 해당 사업의 협의회 목록
     */
    List<Basctm> findByAbusMngNoAndDelYn(String abusMngNo, String delYn);

    /**
     * 특정 심의유형·진행상태의 협의회 목록 (최근 등록순).
     *
     * <p>정보기술부문계획 협의회(dbrTc='02')의 완료(13) 이력에서 '직전 승인 계획'을 찾을 때 사용합니다(조정 협의회의 예산 최초/조정 비교).
     */
    List<Basctm> findByItPtlAsctDbrTcAndItPtlAsctPrgStsTcAndDelYnOrderByFstEnrDtmDesc(
            String itPtlAsctDbrTc, String itPtlAsctPrgStsTc, String delYn);

    /**
     * Oracle 시퀀스(SQ_TPRMPP_BASCTM_1) 다음 값 조회
     *
     * <p>새로운 협의회 생성 시 IT_PTL_ASCT_ID 채번에 사용합니다. ID 형식: {@code ASCT-{연도}-{4자리}} (예: ASCT-2026-0001)
     *
     * @return 시퀀스의 다음 값
     */
    @Query(value = "SELECT SQ_TPRMPP_BASCTM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();

    /**
     * 협의회 행 비관적 쓰기 잠금 조회 (질의응답 채번 직렬화용)
     *
     * <p>사전질의(QTN_ID)·본회의질의(MQT_ID)는 협의회별 {@code COUNT(*)+1} 순번으로 채번되므로, 동일 협의회에 동시 등록이 발생하면 같은 순번이
     * 산정되어 PK 충돌(ORA-00001)이 발생할 수 있다. 채번 직전 부모 협의회 행에 {@code PESSIMISTIC_WRITE} 잠금을 걸어 같은 협의회의 채번을
     * 직렬화한다. 서로 다른 협의회는 다른 행을 잠그므로 경합하지 않는다.
     *
     * @param itPtlAsctId 협의회ID
     * @return 잠금된 협의회 (없으면 empty)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Basctm b WHERE b.itPtlAsctId = :itPtlAsctId")
    Optional<Basctm> findByIdForUpdate(@Param("itPtlAsctId") String itPtlAsctId);

    /**
     * 사업 PRJ_STS 업데이트 (협의회 신청 시 상태 전이용)
     *
     * <p><b>주의(영속성 컨텍스트 초기화)</b>: {@code clearAutomatically = true}로 인해 이 메서드 실행 직후 영속성 컨텍스트가
     * 비워집니다. 따라서 동일 트랜잭션에서 이 호출 이후에 수행하는 {@link Basctm} 등 엔티티 변경은 Dirty Checking 대상에서 제외되어 DB에 반영되지
     * 않습니다. 같은 트랜잭션의 Basctm 변경은 반드시 명시적으로 {@code save()}하거나, 이 호출보다 먼저 수행해야 합니다.
     *
     * @param abusMngNo 프로젝트관리번호
     * @param sno 프로젝트순번
     * @param prjSts 변경할 상태값
     */
    /**
     * 소관부서(BBR_C) 기준 협의회 목록 조회 (일반사용자용)
     *
     * <p>ITPZZ001 권한 사용자는 자신의 소속 부서 사업에 해당하는 협의회만 조회합니다. BPROJM과 조인하여 사업 주관부서 기준으로 필터링합니다.
     *
     * @param svnDpmC 소속부서코드
     * @param delYn 삭제여부 ('N')
     * @return 해당 부서의 협의회 목록
     */
    @Query(
            value =
                    """
            SELECT a.* FROM TPRMPP_BASCTM a
            JOIN TPRMPP_BPROJM p ON a.ABUS_MNG_NO = p.ABUS_MNG_NO AND a.SNO = p.SNO
            WHERE p.SVN_DPM_C = :svnDpmC AND a.DEL_YN = :delYn
            ORDER BY a.FST_ENR_DTM DESC
            """,
            nativeQuery = true)
    List<Basctm> findByDepartment(@Param("svnDpmC") String svnDpmC, @Param("delYn") String delYn);

    /**
     * 평가위원으로 배정된 협의회 목록 조회
     *
     * <p>평가위원 권한 사용자는 BCMMTM에 ENO가 있는 협의회만 조회합니다.
     *
     * @param eno 사번
     * @param delYn 삭제여부 ('N')
     * @return 해당 위원이 배정된 협의회 목록
     */
    @Query(
            value =
                    """
            SELECT a.* FROM TPRMPP_BASCTM a
            JOIN TPRMPP_BCMMTM c ON a.IT_PTL_ASCT_ID = c.IT_PTL_ASCT_ID
            WHERE c.ENO = :eno AND a.DEL_YN = :delYn AND c.DEL_YN = :delYn
            ORDER BY a.FST_ENR_DTM DESC
            """,
            nativeQuery = true)
    List<Basctm> findByCommitteeMember(@Param("eno") String eno, @Param("delYn") String delYn);

    /**
     * 관리자용 협의회 신청 대상 목록 조회 (전체 부서, 통합)
     *
     * <p>부서 필터 없이 전체 사업을 대상으로 조회합니다. 상태는 IT_PTL_STS_TC 코드 기준입니다 (공통코드 그룹 IT_PTL_STS_TC).
     *
     * <ul>
     *   <li>협의회 미신청 대상: IT_PTL_STS_TC = '19'(예산편성 작업 완료) 이면서 BASCTM 미존재
     *   <li>협의회 신청된 건: IT_PTL_STS_TC = '21'(정실협 진행중) 이면서 BASCTM 존재
     * </ul>
     *
     * <p>상태코드 '19'는 예산편성 단계의 전자결재·작업 완료를 이미 의미하므로, 과거의 별도 결재완료(CAPPLA/CAPPLM) EXISTS 조건은 상태코드로
     * 대체했습니다.
     *
     * @param stsInProgress 정실협 진행중 코드 ('21')
     * @param stsPending 정실협 신청 대상 코드 ('19')
     * @return abusMngNo, sno, abusNm, itPtlAsctId(null 가능), itPtlAsctPrgStsTc(null 가능),
     *     itPtlAsctDbrTc(null 가능), cnrcDt(null 가능), applied(0/1) 컬럼 순서의 결과
     */
    @Query(
            value =
                    """
            SELECT
                p.ABUS_MNG_NO    AS abusMngNo,
                p.SNO       AS sno,
                p.ABUS_NM       AS abusNm,
                a.IT_PTL_ASCT_ID       AS itPtlAsctId,
                a.IT_PTL_ASCT_PRG_STS_TC      AS itPtlAsctPrgStsTc,
                a.IT_PTL_ASCT_DBR_TC        AS itPtlAsctDbrTc,
                a.CNRC_DT       AS cnrcDt,
                a.CNRC_STT_TM       AS cnrcSttTm,
                CASE WHEN a.IT_PTL_ASCT_ID IS NOT NULL THEN 1 ELSE 0 END AS applied,
                p.BSE_YY         AS prjYy,
                p.ABUS_PPO_CONE          AS prjTp,
                p.SVN_DPM_C       AS svnDpm,
                NULL                 AS rqmBgAmt,
                p.STT_DTM        AS sttDt,
                p.END_DTM        AS endDt,
                p.DVM_DPM_C        AS itDpm,
                p.ABUS_CONE       AS abusCone,
                a.CSF_HELD_YN       AS csfHeldYn
            FROM TPRMPP_BPROJM p
            JOIN (
                SELECT ABUS_MNG_NO, MAX(IT_PTL_STS_TC) AS IT_PTL_STS_TC
                FROM TPRMPP_BPROJA
                WHERE DEL_YN = 'N'
                GROUP BY ABUS_MNG_NO
            ) ps ON ps.ABUS_MNG_NO = p.ABUS_MNG_NO
            LEFT JOIN TPRMPP_BASCTM a
                ON p.ABUS_MNG_NO = a.ABUS_MNG_NO
               AND p.SNO    = a.SNO
               AND a.DEL_YN     = 'N'
            WHERE p.DEL_YN = 'N'
              AND (
                  (a.IT_PTL_ASCT_ID IS NOT NULL AND ps.IT_PTL_STS_TC = :stsInProgress)
                  OR
                  (a.IT_PTL_ASCT_ID IS NULL AND ps.IT_PTL_STS_TC = :stsPending)
              )
            ORDER BY p.FST_ENR_DTM DESC
            """,
            nativeQuery = true)
    List<Object[]> findProjectsForCouncilAll(
            @Param("stsInProgress") String stsInProgress, @Param("stsPending") String stsPending);

    /**
     * 일반사용자용 협의회 신청 대상 목록 조회 (통합)
     *
     * <p>부서 필터: 사용자의 BBR_C = BPROJM.SVN_DPM_C. 상태는 IT_PTL_STS_TC 코드 기준입니다.
     *
     * <ul>
     *   <li>협의회 미신청 대상: IT_PTL_STS_TC = '19'(예산편성 작업 완료) 이면서 BASCTM 미존재
     *   <li>협의회 신청된 건: IT_PTL_STS_TC = '21'(정실협 진행중) 이면서 BASCTM 존재
     * </ul>
     *
     * @param svnDpm 사용자 소속부서코드 (CustomUserDetails.getBbrC())
     * @param stsInProgress 정실협 진행중 코드 ('21')
     * @param stsPending 정실협 신청 대상 코드 ('19')
     * @return abusMngNo, sno, abusNm, itPtlAsctId(null 가능), itPtlAsctPrgStsTc(null 가능),
     *     itPtlAsctDbrTc(null 가능), cnrcDt(null 가능), applied(0/1) 컬럼 순서의 결과
     */
    @Query(
            value =
                    """
            SELECT
                p.ABUS_MNG_NO    AS abusMngNo,
                p.SNO       AS sno,
                p.ABUS_NM       AS abusNm,
                a.IT_PTL_ASCT_ID       AS itPtlAsctId,
                a.IT_PTL_ASCT_PRG_STS_TC      AS itPtlAsctPrgStsTc,
                a.IT_PTL_ASCT_DBR_TC        AS itPtlAsctDbrTc,
                a.CNRC_DT       AS cnrcDt,
                a.CNRC_STT_TM       AS cnrcSttTm,
                CASE WHEN a.IT_PTL_ASCT_ID IS NOT NULL THEN 1 ELSE 0 END AS applied,
                p.BSE_YY         AS prjYy,
                p.ABUS_PPO_CONE          AS prjTp,
                p.SVN_DPM_C       AS svnDpm,
                NULL                 AS rqmBgAmt,
                p.STT_DTM        AS sttDt,
                p.END_DTM        AS endDt,
                p.DVM_DPM_C        AS itDpm,
                p.ABUS_CONE       AS abusCone,
                a.CSF_HELD_YN       AS csfHeldYn
            FROM TPRMPP_BPROJM p
            JOIN (
                SELECT ABUS_MNG_NO, MAX(IT_PTL_STS_TC) AS IT_PTL_STS_TC
                FROM TPRMPP_BPROJA
                WHERE DEL_YN = 'N'
                GROUP BY ABUS_MNG_NO
            ) ps ON ps.ABUS_MNG_NO = p.ABUS_MNG_NO
            LEFT JOIN TPRMPP_BASCTM a
                ON p.ABUS_MNG_NO = a.ABUS_MNG_NO
               AND p.SNO    = a.SNO
               AND a.DEL_YN     = 'N'
            WHERE p.SVN_DPM_C = :svnDpm
              AND p.DEL_YN  = 'N'
              AND (
                  (a.IT_PTL_ASCT_ID IS NOT NULL AND ps.IT_PTL_STS_TC = :stsInProgress)
                  OR
                  (a.IT_PTL_ASCT_ID IS NULL AND ps.IT_PTL_STS_TC = :stsPending)
              )
            ORDER BY p.FST_ENR_DTM DESC
            """,
            nativeQuery = true)
    List<Object[]> findProjectsForCouncilByDepartment(
            @Param("svnDpm") String svnDpm,
            @Param("stsInProgress") String stsInProgress,
            @Param("stsPending") String stsPending);

    /**
     * 관리자용 협의회 신청대상 목록을 DTO로 봉인 반환한다(#5).
     *
     * <p>native {@link #findProjectsForCouncilAll(String, String)}의 {@code Object[]}를 {@link
     * CouncilProjectRow#fromRow(Object[])} 단일 팩토리로 매핑해, 인덱스 캐스팅이 서비스로 새지 않게 한다.
     *
     * @param stsInProgress 정실협 진행중 코드
     * @param stsPending 정실협 신청 대상 코드
     * @return 신청대상 DTO 목록
     */
    default List<CouncilProjectRow> findProjectRowsForCouncilAll(
            String stsInProgress, String stsPending) {
        return findProjectsForCouncilAll(stsInProgress, stsPending).stream()
                .map(CouncilProjectRow::fromRow)
                .toList();
    }

    /**
     * 일반사용자(부서)용 협의회 신청대상 목록을 DTO로 봉인 반환한다(#5).
     *
     * @param svnDpm 사용자 소속부서코드
     * @param stsInProgress 정실협 진행중 코드
     * @param stsPending 정실협 신청 대상 코드
     * @return 신청대상 DTO 목록
     */
    default List<CouncilProjectRow> findProjectRowsForCouncilByDepartment(
            String svnDpm, String stsInProgress, String stsPending) {
        return findProjectsForCouncilByDepartment(svnDpm, stsInProgress, stsPending).stream()
                .map(CouncilProjectRow::fromRow)
                .toList();
    }
}
