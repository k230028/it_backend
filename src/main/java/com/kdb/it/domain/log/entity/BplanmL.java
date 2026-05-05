package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 정보기술부문계획(TAAABB_BPLANM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BPLANL", comment = "정보기술부문계획 변경 로그")
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
    @Column(name = "PLN_DTL_CONE", comment = "계획세부내용")
    private String plnDtlCone;

    @Column(name = "TTL_BG", precision = 15, scale = 2, comment = "총예산")
    private BigDecimal ttlBg;

    @Column(name = "CPT_BG", precision = 15, scale = 2, comment = "자본예산")
    private BigDecimal cptBg;

    @Column(name = "MNGC", precision = 15, scale = 2, comment = "일반관리비")
    private BigDecimal mngc;
}
