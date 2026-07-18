package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 사업일정 기본(TPRMPP_BBIZSM) 변경 로그. */
@Entity
@Table(name = "TPRMPP_BBIZSL", comment = "사업일정기본 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BbizsmL extends BaseLogEntity {

    @Column(name = "ABUS_MNG_NO", length = 30, comment = "사업관리번호")
    private String abusMngNo;

    @Column(name = "SNO", comment = "일련번호")
    private Integer sno;

    @Column(name = "DSD_CONE", length = 1000, comment = "일정내용")
    private String dsdCone;

    @Column(name = "STT_DT", length = 8, comment = "시작일자")
    private String sttDt;

    @Column(name = "END_DT", length = 8, comment = "종료일자")
    private String endDt;
}
