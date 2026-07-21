package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 평가위원 평가의견(TPRMPP_BEVALM) 변경 로그 엔티티. */
@Entity
@Table(name = "TPRMPP_BEVALL", comment = "평가위원 평가의견 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BevalmL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "ENO", length = 32, comment = "사번")
    private String eno;

    @Column(name = "IT_PTL_CKG_ITM_TC", length = 2, comment = "점검항목코드")
    private String itPtlCkgItmTc;

    @Column(name = "QUEL_RCRD", comment = "점검점수")
    private Integer quelRcrd;

    @Column(name = "CKG_OPNN_CONE", length = 1000, comment = "점검의견내용")
    private String ckgOpnn;
}
