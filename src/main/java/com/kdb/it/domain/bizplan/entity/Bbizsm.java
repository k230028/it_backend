package com.kdb.it.domain.bizplan.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BbizsmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 사업일정 기본 엔티티. DB 테이블: {@code TPRMPP_BBIZSM}, PK=(ABUS_MNG_NO, SNO). */
@LogTarget(entity = BbizsmL.class)
@Entity
@Table(name = "TPRMPP_BBIZSM", comment = "사업일정기본")
@IdClass(BbizsmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bbizsm extends BaseEntity {

    @Id
    @Column(name = "ABUS_MNG_NO", length = 30, nullable = false, comment = "사업관리번호")
    private String abusMngNo;

    @Id
    @Column(name = "SNO", nullable = false, comment = "일련번호")
    private Integer sno;

    @Column(name = "DSD_CONE", length = 1000, comment = "일정내용")
    private String dsdCone;

    @Column(name = "STT_DT", length = 8, comment = "시작일자(YYYYMMDD)")
    private String sttDt;

    @Column(name = "END_DT", length = 8, comment = "종료일자(YYYYMMDD)")
    private String endDt;

    /** 일정 행 갱신 (save 병합에서 호출) */
    public void updateSchedule(String dsdCone, String sttDt, String endDt) {
        this.dsdCone = dsdCone;
        this.sttDt = sttDt;
        this.endDt = endDt;
    }
}
