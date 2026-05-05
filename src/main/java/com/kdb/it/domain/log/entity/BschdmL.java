package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 협의회 일정(TAAABB_BSCHDM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BSCHDL", comment = "협의회 일정 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BschdmL extends BaseLogEntity {

    @Column(name = "ASCT_ID", length = 32, comment = "협의회ID")
    private String asctId;

    @Column(name = "ENO", length = 32, comment = "사번")
    private String eno;

    @Column(name = "DSD_DT", comment = "일정일자")
    private LocalDate dsdDt;

    @Column(name = "DSD_TM", length = 10, comment = "일정시간")
    private String dsdTm;

    @Column(name = "PSB_YN", length = 1, comment = "가능여부")
    private String psbYn;
}
