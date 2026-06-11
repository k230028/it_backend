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
 * 협의회 일정(TPRMPP_BSCHDM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BSCHDL", comment = "협의회 일정 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BschdmL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "ENO", length = 32, comment = "사번")
    private String eno;

    @Column(name = "CNRC_DT", length = 8, comment = "일정일자")
    private String cnrcDt;

    @Column(name = "CNRC_STT_TM", length = 6, comment = "일정시간")
    private String cnrcSttTm;

    @Column(name = "USE_PSB_YN", length = 1, comment = "가능여부")
    private String usePsbYn;
}
