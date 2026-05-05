package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 전산관리비(TAAABB_BCOSTM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BCOSTL", comment = "전산관리비 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BcostmL extends BaseLogEntity {

    @Column(name = "IT_MNGC_NO", length = 128, comment = "전산업무비코드")
    private String itMngcNo;

    @Column(name = "IT_MNGC_SNO", comment = "전산업무비일련번호")
    private Integer itMngcSno;

    @Column(name = "LST_YN", length = 4, comment = "최종여부")
    private String lstYn;

    @Column(name = "IOE_C", length = 400, comment = "비목코드")
    private String ioeC;

    @Column(name = "CTT_NM", length = 800, comment = "계약명")
    private String cttNm;

    @Column(name = "CTT_OPP", length = 400, comment = "계약상대처")
    private String cttOpp;

    @Column(name = "IT_MNGC_BG", precision = 15, scale = 2, comment = "전산업무비예산")
    private BigDecimal itMngcBg;

    @Column(name = "DFR_CLE", length = 40, comment = "지급주기")
    private String dfrCle;

    @Column(name = "FST_DFR_DT", comment = "지급예정월")
    private LocalDate fstDfrDt;

    @Column(name = "CUR", length = 40, comment = "통화")
    private String cur;

    @Column(name = "XCR", precision = 9, comment = "환율")
    private BigDecimal xcr;

    @Column(name = "XCR_BSE_DT", comment = "환율기준일자")
    private LocalDate xcrBseDt;

    @Column(name = "INF_PRT_YN", length = 4, comment = "정보보호여부")
    private String infPrtYn;

    @Column(name = "IND_RSN", length = 4000, comment = "증감사유")
    private String indRsn;

    @Column(name = "CGPR", length = 128, comment = "담당자")
    private String cgpr;

    @Column(name = "BICE_DPM", length = 100, comment = "담당부서")
    private String biceDpm;

    @Column(name = "BICE_TEM", length = 100, comment = "담당팀")
    private String biceTem;

    @Column(name = "BG_YY", length = 4, comment = "예산연도")
    private String bgYy;

    @Column(name = "ABUS_C", length = 100, comment = "사업코드")
    private String abusC;

    @Column(name = "IT_MNGC_TP", length = 100, comment = "전산업무비유형")
    private String itMngcTp;

    @Column(name = "PUL_DTT", length = 100, comment = "전산업무비구분")
    private String pulDtt;
}
