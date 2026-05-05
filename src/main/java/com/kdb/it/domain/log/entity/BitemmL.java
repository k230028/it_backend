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
 * 정보화사업 품목(TAAABB_BITEMM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BITEML", comment = "정보화사업 품목 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BitemmL extends BaseLogEntity {

    @Column(name = "GCL_MNG_NO", length = 32, comment = "품목관리번호")
    private String gclMngNo;

    @Column(name = "GCL_SNO", comment = "품목일련번호")
    private Integer gclSno;

    @Column(name = "PRJ_MNG_NO", length = 32, comment = "사업관리번호")
    private String prjMngNo;

    @Column(name = "PRJ_SNO", comment = "사업일련번호")
    private Integer prjSno;

    @Column(name = "GCL_DTT", length = 32, comment = "품목구분")
    private String gclDtt;

    @Column(name = "GCL_NM", length = 100, comment = "품목명")
    private String gclNm;

    @Column(name = "GCL_QTT", precision = 9, comment = "품목수량")
    private BigDecimal gclQtt;

    @Column(name = "CUR", length = 10, comment = "통화")
    private String cur;

    @Column(name = "XCR", precision = 15, scale = 4, comment = "환율")
    private BigDecimal xcr;

    @Column(name = "XCR_BSE_DT", comment = "환율기준일자")
    private LocalDate xcrBseDt;

    @Column(name = "BG_FDTN", length = 100, comment = "예산근거")
    private String bgFdtn;

    @Column(name = "ITD_DT", length = 32, comment = "도입시기")
    private String itdDt;

    @Column(name = "DFR_CLE", length = 10, comment = "지급주기")
    private String dfrCle;

    @Column(name = "INF_PRT_YN", length = 1, comment = "정보보호여부")
    private String infPrtYn;

    @Column(name = "ITR_INFR_YN", length = 1, comment = "통합인프라여부")
    private String itrInfrYn;

    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    @Column(name = "GCL_AMT", precision = 15, comment = "품목금액")
    private BigDecimal gclAmt;
}
