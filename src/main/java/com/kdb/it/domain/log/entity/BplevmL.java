package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 정보기술부문계획 협의회 사업별 평가의견(TPRMPP_BPLEVM) 변경 로그 엔티티.
 *
 * <p>PK(LOG_HIS_TGR_SNO)는 AuditLogIdGenerator가 SEQ_BPLEVL.NEXTVAL로 채번한다.</p>
 */
@Entity
@Table(name = "TPRMPP_BPLEVL", comment = "정보기술부문계획 협의회 사업별 평가의견 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BplevmL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "ENO", length = 32, comment = "사번")
    private String eno;

    @Column(name = "ABUS_MNG_NO", length = 32, comment = "사업관리번호")
    private String abusMngNo;

    @Column(name = "PPRT_YN", length = 1, comment = "적정여부(Y=적정/N=유보)")
    private String pprtYn;

    @Column(name = "EVAL_OPNN_CONE", length = 1000, comment = "평가의견내용")
    private String evalOpnn;
}
