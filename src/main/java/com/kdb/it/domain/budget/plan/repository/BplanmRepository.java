package com.kdb.it.domain.budget.plan.repository;

import com.kdb.it.domain.budget.plan.entity.Bplanm;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 정보기술부문계획(TPRMPP_BPLANM) JPA 리포지토리 */
public interface BplanmRepository extends JpaRepository<Bplanm, String> {

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
    List<Bplanm> findAllByDelYnOrderByFstEnrDtmDesc(String delYn);

    /**
     * 삭제되지 않은 계획을 목록 전용 프로젝션으로 조회합니다.
     *
     * @param delYn 삭제여부
     * @return 등록일시 내림차순 계획 목록
     */
    List<PlanListView> findListViewsByDelYnOrderByFstEnrDtmDesc(String delYn);

    /**
     * 계획관리번호와 삭제여부로 단건 조회합니다.
     *
     * @param reqDocNo 계획관리번호
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 계획 엔티티 (Optional)
     */
    Optional<Bplanm> findByReqDocNoAndDelYn(String reqDocNo, String delYn);

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
    boolean existsByBseYyAndItPtlPlnTpCAndDelYn(String bseYy, String itPtlPlnTpC, String delYn);
}
