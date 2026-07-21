package com.kdb.it.domain.log.entity;

import com.kdb.it.common.util.Yyyymmdd8DateConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 정보화실무협의회 기본정보(TPRMPP_BASCTM) 변경 로그 엔티티. */
@Entity
@Table(name = "TPRMPP_BASCTL", comment = "정보화실무협의회 기본정보 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BasctmL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "ABUS_MNG_NO", length = 30, comment = "프로젝트관리번호")
    private String abusMngNo;

    @Column(name = "SNO", comment = "프로젝트순번")
    private Integer sno;

    @Column(name = "IT_PTL_ASCT_PRG_STS_TC", length = 2, comment = "협의회상태코드")
    private String itPtlAsctPrgStsTc;

    @Column(name = "IT_PTL_ASCT_DBR_TC", length = 2, comment = "심의유형구분코드")
    private String itPtlAsctDbrTc;

    @Column(name = "CNRC_DT", length = 8, comment = "회의일자")
    @Convert(converter = Yyyymmdd8DateConverter.class)
    private LocalDate cnrcDt;

    @Column(name = "CNRC_STT_TM", length = 6, comment = "회의시간")
    private String cnrcSttTm;

    @Column(name = "CNRC_PLC_NM", length = 100, comment = "회의장소명")
    private String cnrcPlc;

    @Column(name = "PRTY_IVG_OMT_YN", length = 1, comment = "타당성검토생략여부")
    private String prtyIvgOmtYn;

    @Column(name = "PRTY_IVG_OMT_RSN", length = 200, comment = "타당성검토생략사유")
    private String prtyIvgOmtRsn;

    @Column(name = "CSF_HELD_YN", length = 1, comment = "대면개최여부")
    private String csfHeldYn;
}
