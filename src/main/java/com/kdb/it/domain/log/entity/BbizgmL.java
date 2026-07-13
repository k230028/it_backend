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

/** 사업품목 기본(TPRMPP_BBIZGM) 변경 로그. */
@Entity
@Table(name = "TPRMPP_BBIZGL", comment = "사업품목기본 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BbizgmL extends BaseLogEntity {

    @Column(name = "ABUS_MNG_NO", length = 30, comment = "사업관리번호")
    private String abusMngNo;

    @Column(name = "SNO", comment = "일련번호")
    private Integer sno;

    @Column(name = "GCL_NM", length = 100, comment = "품목명")
    private String gclNm;

    @Column(name = "IOE_C", length = 7, comment = "비목코드")
    private String ioeC;

    @Column(name = "QTY", comment = "수량")
    private Long qty;

    @Column(name = "AMT", precision = 18, scale = 3, comment = "금액")
    private BigDecimal amt;

    @Column(name = "FC_AMT", precision = 18, scale = 3, comment = "외화금액")
    private BigDecimal fcAmt;

    @Column(name = "CUR_C", length = 3, comment = "통화코드")
    private String curC;

    @Column(name = "XCR", precision = 9, scale = 4, comment = "환율")
    private BigDecimal xcr;

    @Column(name = "XCR_BSE_DT", length = 8, comment = "환율기준일자")
    private String xcrBseDt;

    @Column(name = "CTT_SNO", comment = "계약일련번호")
    private Integer cttSno;
}
