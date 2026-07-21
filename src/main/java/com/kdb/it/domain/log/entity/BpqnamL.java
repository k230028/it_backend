package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 사전질의응답(TPRMPP_BPQNAM) 변경 로그 엔티티. */
@Entity
@Table(name = "TPRMPP_BPQNAL", comment = "사전질의응답 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BpqnamL extends BaseLogEntity {

    @Column(name = "QTN_ID", length = 36, comment = "질의응답ID")
    private String qtnId;

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "QTN_DWU_USID", length = 14, comment = "질의자사번")
    private String qtnDwuUsid;

    @Column(name = "QTN_CONE", length = 4000, comment = "질의내용")
    private String qtnCone;

    @Column(name = "REP_DWU_USID", length = 14, comment = "답변자사번")
    private String repDwuUsid;

    @Column(name = "REP_CONE", length = 2000, comment = "답변내용")
    private String repCone;

    @Column(name = "QTN_RPD_RLT_YN", length = 1, comment = "답변여부")
    private String qtnRpdRltYn;
}
