package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 사업계획 기본(TPRMPP_BBIZPM) 변경 로그. */
@Entity
@Table(name = "TPRMPP_BBIZPL", comment = "사업계획기본 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BbizpmL extends BaseLogEntity {

    @Column(name = "ABUS_MNG_NO", length = 30, comment = "사업관리번호")
    private String abusMngNo;

    @Column(name = "ABUS_NM", length = 100, comment = "사업명")
    private String abusNm;

    @Lob
    @Column(name = "ABUS_PUL_NCS_INF", comment = "사업추진필요성정보")
    private String abusPulNcsInf;

    @Lob
    @Column(name = "ABUS_PUL_DRCN_INF", comment = "사업추진방향정보")
    private String abusPulDrcnInf;

    @Lob
    @Column(name = "ABUS_PUL_CONE_INF", comment = "사업추진내용정보")
    private String abusPulConeInf;

    @Lob
    @Column(name = "ABUS_XPT_EFF_INF", comment = "사업기대효과정보")
    private String abusXptEffInf;

    @Lob
    @Column(name = "REDT_CONE_INF", comment = "보고서내용정보")
    private String redtConeInf;

    @Column(name = "BG_NO", length = 15, comment = "예산번호")
    private String bgNo;

    @Column(name = "TOT_RQM_AMT", precision = 18, scale = 3, comment = "총소요금액")
    private BigDecimal totRqmAmt;

    @Column(name = "IT_PTL_EDRT_TC", length = 2, comment = "IT포탈전결권구분코드")
    private String itPtlEdrtTc;
}
