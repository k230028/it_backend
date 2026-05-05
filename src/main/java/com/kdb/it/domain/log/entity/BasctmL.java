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
 * 정보화실무협의회 기본정보(TAAABB_BASCTM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BASCTL", comment = "정보화실무협의회 기본정보 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BasctmL extends BaseLogEntity {

    @Column(name = "ASCT_ID", length = 32, comment = "협의회ID")
    private String asctId;

    @Column(name = "PRJ_MNG_NO", length = 32, comment = "프로젝트관리번호")
    private String prjMngNo;

    @Column(name = "PRJ_SNO", comment = "프로젝트순번")
    private Integer prjSno;

    @Column(name = "ASCT_STS", length = 20, comment = "협의회상태")
    private String asctSts;

    @Column(name = "DBR_TP", length = 20, comment = "심의유형")
    private String dbrTp;

    @Column(name = "CNRC_DT", comment = "회의일자")
    private LocalDate cnrcDt;

    @Column(name = "CNRC_TM", length = 10, comment = "회의시간")
    private String cnrcTm;

    @Column(name = "CNRC_PLC", length = 200, comment = "회의장소")
    private String cnrcPlc;
}
