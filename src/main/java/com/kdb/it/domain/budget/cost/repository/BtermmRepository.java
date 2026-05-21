package com.kdb.it.domain.budget.cost.repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.entity.BtermmId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 단말기관리마스터(Btermm) 데이터 접근 리포지토리
 */
@Repository
public interface BtermmRepository extends JpaRepository<Btermm, BtermmId> {

    /**
     * 특정 전산관리비와 연관된 모든 단말기 목록 조회
     *
     * @param itMngcNo  전산관리비 관리번호
     * @param itMngcSno 전산관리비 일련번호
     * @param delYn     삭제 여부 ('N'=미삭제)
     * @return 연관된 단말기 목록
     */
    List<Btermm> findByItMngcNoAndItMngcSnoAndDelYn(String itMngcNo, Integer itMngcSno, String delYn);

    /**
     * 특정 전산관리비와 연관된 모든 단말기 일괄 삭제(Soft Delete) 처리를 위해 목록 조회
     *
     * @param itMngcNo  전산관리비 관리번호
     * @param itMngcSno 전산관리비 일련번호
     * @return 연관된 모든 단말기 목록
     */
    List<Btermm> findByItMngcNoAndItMngcSno(String itMngcNo, Integer itMngcSno);

    /**
     * Oracle 시퀀스(SEQ_BTERMM) 다음 값 조회
     *
     * @return 시퀀스의 다음 값 (Long)
     */
    @Query(value = "SELECT SEQ_BTERMM.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();

    /**
     * 특정 관리번호 내 다음 일련번호(SNO) 계산
     *
     * @param tmnMngNo 단말기 관리번호
     * @return 다음 일련번호 (기존 레코드가 없으면 1)
     */
    @Query(value = "SELECT NVL(MAX(TMN_SNO), 0) + 1 FROM TPRMPP_BTERMM WHERE TMN_MNG_NO = :tmnMngNo", nativeQuery = true)
    Integer getNextSnoValue(@Param("tmnMngNo") String tmnMngNo);
}
