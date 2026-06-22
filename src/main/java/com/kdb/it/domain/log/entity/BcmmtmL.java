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
 * 협의회 평가위원(TPRMPP_BCMMTM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BCMMTL", comment = "협의회 평가위원 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BcmmtmL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "ENO", length = 32, comment = "사번")
    private String eno;

    @Column(name = "IT_PTL_ASCT_MEB_TC", length = 2, comment = "위원유형구분코드")
    private String itPtlAsctMebTc;

    @Column(name = "CNFM_YN", length = 1, comment = "확인여부")
    private String cnfmYn;

    @Column(name = "CSF_HP_YN", length = 1, comment = "대면희망여부")
    private String csfHpYn;
}
