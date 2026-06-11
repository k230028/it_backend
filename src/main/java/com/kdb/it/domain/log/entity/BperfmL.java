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
 * 성과관리 자체계획(TPRMPP_BPERFM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BPERFL", comment = "성과관리 자체계획 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BperfmL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "EVL_DTP_SNO", comment = "지표순번")
    private Integer evlDtpSno;

    @Column(name = "EVL_DTP_NM", length = 100, comment = "성과지표명")
    private String evlDtpNm;

    @Column(name = "EVL_DTP_DFNT_CONE", length = 4000, comment = "성과지표정의")
    private String evlDtpDfntCone;


    @Column(name = "EVL_DTP_CLF_CONE", length = 4000, comment = "산식")
    private String evlDtpClfCone;




    @Column(name = "EVL_DTP_MSM_PTM_CONE", length = 300, comment = "측정시점내용")
    private String evlDtpMsmPtmCone;

    @Column(name = "EVL_DTP_MSM_CLE_CONE", length = 300, comment = "측정주기")
    private String evlDtpMsmCleCone;
}
