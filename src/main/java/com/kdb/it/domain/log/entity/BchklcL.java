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
 * 타당성 자체점검(TPRMPP_BCHKLC) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BCHKLL", comment = "타당성 자체점검 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BchklcL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "IT포탈협의회ID")
    private String itPtlAsctId;

    @Column(name = "IT_PTL_CKG_ITM_TC", length = 2, comment = "IT포탈점검항목구분코드")
    private String itPtlCkgItmTc;

    @Column(name = "CKG_OPNN_CONE", length = 1000, comment = "점검의견내용")
    private String ckgOpnnCone;

    @Column(name = "QUEL_RCRD", comment = "문항점수")
    private Integer quelRcrd;
}
