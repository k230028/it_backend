package com.kdb.it.domain.budget.cost.repository;

import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.entity.BtermmId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 단말기관리마스터(Btermm) 데이터 접근 리포지토리 */
public interface BtermmRepository extends JpaRepository<Btermm, BtermmId> {

    /**
     * 특정 전산관리비와 연관된 모든 단말기 목록 조회
     *
     * @param termBgNo 전산관리비 관리번호
     * @param termBgSno 전산관리비 일련번호
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 연관된 단말기 목록
     */
    List<Btermm> findByTermBgNoAndTermBgSnoAndDelYn(
            String termBgNo, Integer termBgSno, String delYn);

    /**
     * 특정 전산관리비와 연관된 모든 단말기 일괄 삭제(Soft Delete) 처리를 위해 목록 조회
     *
     * @param termBgNo 전산관리비 관리번호
     * @param termBgSno 전산관리비 일련번호
     * @return 연관된 모든 단말기 목록
     */
    List<Btermm> findByTermBgNoAndTermBgSno(String termBgNo, Integer termBgSno);

    /**
     * 여러 전산관리비에 연관된 단말기 일괄 조회 (N+1 방지용 배치 조회)
     *
     * @param termBgNos 전산관리비 관리번호 목록
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 연관 단말기 목록 (호출자가 termBgNo+termBgSno로 그룹핑)
     */
    List<Btermm> findByTermBgNoInAndDelYn(java.util.Collection<String> termBgNos, String delYn);

    /**
     * Oracle 시퀀스(SQ_TPRMPP_BTERMM_1) 다음 값 조회
     *
     * @return 시퀀스의 다음 값 (Long)
     */
    @Query(value = "SELECT SQ_TPRMPP_BTERMM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();

    /**
     * 특정 관리번호 내 다음 일련번호(SNO) 계산
     *
     * @param tmnMngNo 단말기 관리번호
     * @return 다음 일련번호 (기존 레코드가 없으면 1)
     */
    @Query(
            value = "SELECT NVL(MAX(SNO), 0) + 1 FROM TPRMPP_BTERMM WHERE TMN_MNG_NO = :tmnMngNo",
            nativeQuery = true)
    Integer getNextSnoValue(@Param("tmnMngNo") String tmnMngNo);

    /**
     * 최근 예산연도 구간에서 사용된 단말기 서비스명(SPF_TMN_NM) 후보 목록 조회
     *
     * <p>단말기종류(TMN_CLSF_C)가 같은 미삭제 단말기 중 서비스명이 비어 있지 않은 값을 중복 제거하여 사용 빈도 내림차순으로 반환합니다. 연도 판단은 연결된
     * 전산업무비(Bcostm)의 예산연도(BSE_YY)를 기준으로 하며, 개정 이력이 빈도를 부풀리지 않도록 최종 버전(LST_YN='Y')만 집계합니다.
     *
     * @param tmnClsfC 단말기종류 코드 (공통코드 IT_PTL_TMN_SVC_TC)
     * @param fromBseYy 집계 시작 예산연도 (YYYY, 이상)
     * @return 빈도 내림차순·동률 시 이름 오름차순으로 정렬된 서비스명 목록
     */
    @Query(
            """
            SELECT t.spfTmnNm FROM Btermm t JOIN t.bcostm c
            WHERE t.delYn = 'N' AND c.delYn = 'N' AND c.lstYn = 'Y'
              AND TRIM(t.spfTmnNm) IS NOT NULL
              AND c.bseYy >= :fromBseYy
              AND t.tmnClsfC = :tmnClsfC
            GROUP BY t.spfTmnNm
            ORDER BY COUNT(t) DESC, t.spfTmnNm ASC
            """)
    List<String> findServiceNamesByTmnClsfC(
            @Param("tmnClsfC") String tmnClsfC, @Param("fromBseYy") String fromBseYy);

    /**
     * 최근 예산연도 구간에서 사용된 단말기 서비스명(SPF_TMN_NM) 후보 목록 조회 (단말기종류 무관)
     *
     * <p>{@link #findServiceNamesByTmnClsfC}와 동일하지만 단말기종류를 아직 고르지 않은 행을 위해 종류 조건 없이 집계합니다.
     *
     * @param fromBseYy 집계 시작 예산연도 (YYYY, 이상)
     * @return 빈도 내림차순·동률 시 이름 오름차순으로 정렬된 서비스명 목록
     */
    @Query(
            """
            SELECT t.spfTmnNm FROM Btermm t JOIN t.bcostm c
            WHERE t.delYn = 'N' AND c.delYn = 'N' AND c.lstYn = 'Y'
              AND TRIM(t.spfTmnNm) IS NOT NULL
              AND c.bseYy >= :fromBseYy
            GROUP BY t.spfTmnNm
            ORDER BY COUNT(t) DESC, t.spfTmnNm ASC
            """)
    List<String> findServiceNames(@Param("fromBseYy") String fromBseYy);
}
