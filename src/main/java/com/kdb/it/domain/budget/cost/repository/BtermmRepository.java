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
}
