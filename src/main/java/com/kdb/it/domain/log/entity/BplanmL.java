package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 정보기술부문계획(TPRMPP_BPLANM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BPLANL", comment = "정보기술부문계획 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BplanmL extends BaseLogEntity {

    @Column(name = "PLN_MNG_NO", length = 32, comment = "계획관리번호")
    private String plnMngNo;

    @Column(name = "PLN_TP", length = 16, comment = "계획구분")
    private String plnTp;

    @Column(name = "PLN_YY", length = 4, comment = "대상년도")
    private String plnYy;

    @Lob
    @Column(name = "PLN_DTL_INF", comment = "계획상세정보")
    private String plnDtlInf;

    @Column(name = "IT_PRJ_CONE", length = 4000, comment = "IT프로젝트내용")
    private String itPrjCone;

    @Column(name = "IT_BG_CONE", length = 4000, comment = "IT예산내용")
    private String itBgCone;

    @Column(name = "IT_PRJ_RMK", length = 600, comment = "IT예산비고")
    private String itPrjRmk;

    @Column(name = "CPIT_BG_RMK", length = 600, comment = "자본예산비고")
    private String cpitBgRmk;

    @Column(name = "MNGC_BG_RMK", length = 600, comment = "관리비예산비고")
    private String mngcBgRmk;

    @Column(name = "TTL_BG", precision = 15, scale = 2, comment = "총예산")
    private BigDecimal ttlBg;

    @Column(name = "CPT_BG", precision = 15, scale = 2, comment = "자본예산")
    private BigDecimal cptBg;

    @Column(name = "MNGC", precision = 15, scale = 2, comment = "일반관리비")
    private BigDecimal mngc;
}
