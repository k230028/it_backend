package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 사업계약 기본(TPRMPP_BBIZCM) 변경 로그. */
@Entity
@Table(name = "TPRMPP_BBIZCL", comment = "사업계약기본 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BbizcmL extends BaseLogEntity {

    @Column(name = "ABUS_MNG_NO", length = 30, comment = "사업관리번호")
    private String abusMngNo;

    @Column(name = "SNO", comment = "일련번호")
    private Integer sno;

    @Column(name = "CTT_NM", length = 100, comment = "계약명")
    private String cttNm;

    @Column(name = "NOW_CTT_MANR_C", length = 2, nullable = false, comment = "현재계약방법코드")
    private String nowCttManrC;

    @Column(name = "CTT_TRM_MM_NBR", comment = "계약기간월수")
    private Integer cttTrmMmNbr;
}
