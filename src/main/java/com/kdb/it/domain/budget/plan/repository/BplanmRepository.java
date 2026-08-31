package com.kdb.it.domain.budget.plan.repository;

import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.domain.budget.plan.entity.BplanmId;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 정보기술부문계획(TPRMPP_BPLANM) JPA 리포지토리 */
public interface BplanmRepository extends JpaRepository<Bplanm, BplanmId> {

    /** 계획 목록 조회에 필요한 필드만 읽는 프로젝션입니다. */
    interface PlanListView {
        String getReqDocNo();

        String getItPtlPlnTpC();

        String getBseYy();

        BigDecimal getAduTotAmt();

        BigDecimal getCpitBgApvAmt();

        BigDecimal getTotXpAmt();

        LocalDateTime getFstEnrDtm();

        String getFstEnrUsid();

        String getRedtConeInf();

        Integer getSno();

        String getLstYn();

        String getSvnDpmC();

        LocalDateTime getLstChgDtm();
    }

    /**
     * Oracle 시퀀스(SQ_TPRMPP_BPLANM_1)에서 다음 채번값을 조회합니다. 계획관리번호 생성에 사용됩니다. (형식: PLN-{연도}-{seq:04d})
     *
     * @return 다음 시퀀스 값
     */
    @Query(nativeQuery = true, value = "SELECT SQ_TPRMPP_BPLANM_1.NEXTVAL FROM DUAL")
    Long getNextSequenceValue();

    /**
     * 삭제되지 않은 전체 계획 목록을 등록일시 내림차순으로 조회합니다.
     *
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 계획 엔티티 목록
     */
    List<Bplanm> findAllByDelYnAndLstYnOrderByFstEnrDtmDesc(String delYn, String lstYn);

    /** 일반 계획 목록은 현재 최종 개정본만 반환합니다. */
    default List<Bplanm> findAllByDelYnOrderByFstEnrDtmDesc(String delYn) {
        return findAllByDelYnAndLstYnOrderByFstEnrDtmDesc(delYn, "Y");
    }

    /**
     * 삭제되지 않은 계획을 목록 전용 프로젝션으로 조회합니다.
     *
     * @param delYn 삭제여부
     * @return 등록일시 내림차순 계획 목록
     */
    @Query(
            """
            SELECT p.reqDocNo AS reqDocNo, p.itPtlPlnTpC AS itPtlPlnTpC, p.bseYy AS bseYy,
                   p.aduTotAmt AS aduTotAmt, p.cpitBgApvAmt AS cpitBgApvAmt,
                   p.totXpAmt AS totXpAmt, p.fstEnrDtm AS fstEnrDtm,
                   p.fstEnrUsid AS fstEnrUsid, p.redtConeInf AS redtConeInf,
                   p.sno AS sno, p.lstYn AS lstYn, p.svnDpmC AS svnDpmC,
                   p.lstChgDtm AS lstChgDtm
             FROM Bplanm p
             WHERE p.delYn = :delYn
               AND p.lstYn = 'Y'
             ORDER BY p.fstEnrDtm DESC
            """)
    List<PlanListView> findListViewsByDelYnOrderByFstEnrDtmDesc(@Param("delYn") String delYn);

    /**
     * 계획관리번호와 삭제여부로 단건 조회합니다.
     *
     * @param reqDocNo 계획관리번호
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 계획 엔티티 (Optional)
     */
    default Optional<Bplanm> findByReqDocNoAndDelYn(String reqDocNo, String delYn) {
        return findByReqDocNoAndLstYnAndDelYn(reqDocNo, "Y", delYn);
    }

    Optional<Bplanm> findByReqDocNoAndLstYnAndDelYn(String reqDocNo, String lstYn, String delYn);

    Optional<Bplanm> findByReqDocNoAndSnoAndDelYn(String reqDocNo, Integer sno, String delYn);

    List<Bplanm> findByReqDocNoAndDelYnOrderBySnoAsc(String reqDocNo, String delYn);

    @Query(
            value = "SELECT NVL(MAX(SNO), 0) + 1 FROM TPRMPP_BPLANM WHERE REQ_DOC_NO = :reqDocNo",
            nativeQuery = true)
    Integer getNextVersionSno(@Param("reqDocNo") String reqDocNo);

    /** 같은 부모의 최종본을 잠가 동시 재신청 순번 채번을 직렬화합니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT p
              FROM Bplanm p
             WHERE p.reqDocNo = :reqDocNo
               AND p.lstYn = 'Y'
               AND p.delYn = 'N'
            """)
    Optional<Bplanm> findCurrentVersionForUpdate(@Param("reqDocNo") String reqDocNo);

    /** 승인 완료 이벤트가 가리킨 정확한 개정본을 잠급니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT p
              FROM Bplanm p
             WHERE p.reqDocNo = :reqDocNo
               AND p.sno = :sno
               AND p.delYn = 'N'
            """)
    Optional<Bplanm> findVersionForUpdate(
            @Param("reqDocNo") String reqDocNo, @Param("sno") Integer sno);

    /** 승인된 대상 외 현재 최종본을 모두 이력본으로 내립니다. */
    @Modifying(flushAutomatically = true)
    @Query(
            """
            UPDATE Bplanm p
               SET p.lstYn = 'N'
             WHERE p.reqDocNo = :reqDocNo
               AND p.sno <> :sno
               AND p.delYn = 'N'
               AND p.lstYn = 'Y'
            """)
    int clearCurrentVersion(@Param("reqDocNo") String reqDocNo, @Param("sno") Integer sno);

    /** 승인된 정확한 개정본을 현재 최종본으로 올립니다. */
    @Modifying(flushAutomatically = true)
    @Query(
            """
            UPDATE Bplanm p
               SET p.lstYn = 'Y'
             WHERE p.reqDocNo = :reqDocNo
               AND p.sno = :sno
               AND p.delYn = 'N'
            """)
    int markVersionCurrent(@Param("reqDocNo") String reqDocNo, @Param("sno") Integer sno);

    /**
     * 같은 연도·계획구분의 미삭제 계획이 있는지 확인합니다.
     *
     * <p>수기 엑셀 이관의 계획 중복 판정에 사용합니다.
     *
     * @param bseYy 대상연도 (4자리)
     * @param itPtlPlnTpC 계획구분 ('신규' 또는 '조정')
     * @param delYn 삭제여부 ('N')
     * @return 존재하면 true
     */
    boolean existsByBseYyAndItPtlPlnTpCAndLstYnAndDelYn(
            String bseYy, String itPtlPlnTpC, String lstYn, String delYn);

    /** 기존 중복 검사는 현재 최종 계획만 대상으로 유지합니다. */
    default boolean existsByBseYyAndItPtlPlnTpCAndDelYn(
            String bseYy, String itPtlPlnTpC, String delYn) {
        return existsByBseYyAndItPtlPlnTpCAndLstYnAndDelYn(bseYy, itPtlPlnTpC, "Y", delYn);
    }
}
