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
 * 단말기관리마스터(TAAABB_BTERMM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BTERML", comment = "단말기관리마스터 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BtermmL extends BaseLogEntity {

    @Column(name = "TMN_MNG_NO", length = 32, comment = "단말기관리번호")
    private String tmnMngNo;

    @Column(name = "TMN_SNO", length = 32, comment = "단말기일련번호")
    private String tmnSno;

    @Column(name = "IT_MNGC_NO", length = 32, comment = "IT관리비관리번호")
    private String itMngcNo;

    @Column(name = "IT_MNGC_SNO", comment = "IT관리비일련번호")
    private Integer itMngcSno;

    @Column(name = "TMN_NM", length = 100, comment = "단말기명")
    private String tmnNm;

    @Column(name = "TMN_TUZ_MANR", length = 100, comment = "단말기이용방법")
    private String tmnTuzManr;

    @Column(name = "TMN_USG", length = 100, comment = "단말기용도")
    private String tmnUsg;

    @Column(name = "TMN_SVC", length = 100, comment = "단말기서비스")
    private String tmnSvc;

    @Column(name = "TML_AMT", precision = 15, scale = 0, comment = "단말기금액")
    private BigDecimal tmlAmt;

    @Column(name = "CUR", length = 10, comment = "통화")
    private String cur;

    @Column(name = "XCR", precision = 9, comment = "환율")
    private BigDecimal xcr;

    @Column(name = "XCR_BSE_DT", comment = "환율기준일자")
    private LocalDate xcrBseDt;

    @Column(name = "DFR_CLE", length = 100, comment = "지급주기")
    private String dfrCle;

    @Column(name = "IND_RSN", length = 1000, comment = "증감사유")
    private String indRsn;

    @Column(name = "CGPR", length = 32, comment = "담당자")
    private String cgpr;

    @Column(name = "BICE_TEM", length = 100, comment = "담당팀")
    private String biceTem;

    @Column(name = "BICE_DPM", length = 100, comment = "담당부서")
    private String biceDpm;

    @Column(name = "RMK", length = 1000, comment = "비고")
    private String rmk;
}
