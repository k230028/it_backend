package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 대금지급 상세(TPRMPP_BPAYTM) 변경 로그. */
@Entity
@Table(name = "TPRMPP_BPAYTL", comment = "대금지급 상세 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BpaymtL extends BaseLogEntity {
    @Column(name = "DOC_MNG_NO", length = 20, comment = "문서관리번호") private String docMngNo;
    @Column(name = "DOC_VRS_SNO", comment = "문서버전일련번호") private Integer docVrsSno;
    @Column(name = "DFR_TOD", comment = "지급회차") private Integer dfrTod;
    @Column(name = "DFR_AMT", precision = 18, scale = 3, comment = "지급금액") private BigDecimal dfrAmt;
    @Column(name = "DFR_DT", length = 8, comment = "지급일자") private String dfrDt;
    @Column(name = "DFR_MPL_DT", length = 8, comment = "지급예정일자") private String dfrMplDt;
    @Column(name = "OPNN_CONE", length = 1000, comment = "의견내용") private String opnnCone;
}
