package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BplevmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 정보기술부문계획 협의회 사업별 평가의견 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_BPLEVM}</p>
 *
 * <p>정보기술부문계획(dbrTc='02') 협의회에서 각 평가위원이 계획에 포함된
 * 정보화사업별로 적정/유보(PPRT_YN)와 사유(EVAL_OPNN_CONE)를 남깁니다.
 * 사업별 최종 판정은 "위원 중 1명이라도 유보(N)면 유보"입니다(집계는 서비스 계층).</p>
 *
 * <p>기존 타당성검토 평가의견(Bevalm)과 구조가 유사하나, 세 번째 복합키가
 * 점검항목코드가 아니라 사업관리번호(ABUS_MNG_NO)이고, 점수 대신 적정여부(PPRT_YN)를 씁니다.</p>
 *
 * <p>복합키: ({@code itPtlAsctId}, {@code eno}, {@code abusMngNo})</p>
 */
@LogTarget(entity = BplevmL.class)
@Entity
@Table(name = "TPRMPP_BPLEVM", comment = "정보기술부문계획 협의회 사업별 평가의견")
@IdClass(BplevmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bplevm extends BaseEntity {

    /** 협의회ID: 복합키 첫 번째 컬럼 */
    @Id
    @Column(name = "IT_PTL_ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String itPtlAsctId;

    /** 사번: 복합키 두 번째 컬럼 (평가위원) */
    @Id
    @Column(name = "ENO", length = 32, nullable = false, comment = "사번")
    private String eno;

    /** 사업관리번호: 복합키 세 번째 컬럼 (심의 대상 정보화사업) */
    @Id
    @Column(name = "ABUS_MNG_NO", length = 32, nullable = false, comment = "사업관리번호")
    private String abusMngNo;

    /** 적정여부: Y(적정) / N(유보). 위원이 사업별로 1택. */
    @Column(name = "PPRT_YN", length = 1, nullable = false, comment = "적정여부(Y=적정/N=유보)")
    private String pprtYn;

    /** 평가의견내용: 적정/유보 사유 (최대 1000자) */
    @Column(name = "EVAL_OPNN_CONE", length = 1000, comment = "평가의견내용")
    private String evalOpnn;

    /**
     * 평가의견 업데이트 (위원이 수정 시 재호출)
     *
     * @param pprtYn    적정여부 (Y=적정 / N=유보)
     * @param evalOpnn 평가의견(사유)
     */
    public void update(String pprtYn, String evalOpnn) {
        this.pprtYn = pprtYn;
        this.evalOpnn = evalOpnn;
    }
}
