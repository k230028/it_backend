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

/**
 * 전산관리비(TPRMPP_BCOSTM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BCOSTL", comment = "전산관리비 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BcostmL extends BaseLogEntity {

    @Column(name = "BG_NO", length = 15, comment = "전산업무비코드")
    private String costBgNo;

    @Column(name = "BG_SNO", precision = 9, comment = "예산일련번호")
    private Integer bgSno;

    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    @Column(name = "IOE_C", length = 7, comment = "비목코드")
    private String ioeC;

    @Column(name = "CTT_NM", length = 100, comment = "계약명")
    private String cttNm;

    @Column(name = "CTT_OPP_NM", length = 100, comment = "계약상대처명")
    private String cttOppNm;

    @Column(name = "AMT", precision = 18, scale = 3, comment = "전산업무비예산금액")
    private BigDecimal costTotXpAmt;

    @Column(name = "DFR_CLE_C", length = 1, comment = "지급주기코드")
    private String dfrCleC;

    @Column(name = "FST_DFR_DT", comment = "지급예정월")
    private String fstDfrDt;

    @Column(name = "CUR_C", length = 3, comment = "통화코드")
    private String curC;

    @Column(name = "XCR", precision = 9, scale = 4, comment = "환율")
    private BigDecimal xcr;

    @Column(name = "XCR_BSE_DT", comment = "환율기준일자")
    private String xcrBseDt;

    @Column(name = "SECT_SYS_UTZ_YN", length = 1, comment = "정보보호여부")
    private String sectSysUtzYn;

    @Column(name = "IND_RSN", length = 200, comment = "증감사유")
    private String indRsn;

    @Column(name = "CGPR_ID", length = 14, comment = "담당자행번")
    private String cgprId;

    @Column(name = "PRLM_HRK_OGZ_C_CONE", length = 100, comment = "인사상위조직코드내용")
    private String prlmHrkOgzCCone;

    @Column(name = "SVN_DPM_C", length = 20, comment = "담당부서코드")
    private String costSvnDpmC;

    @Column(name = "SVN_TEM_C", length = 5, comment = "담당팀코드")
    private String svnTemC;

    @Column(name = "SVN_DPM_NM", length = 100, comment = "주관부서명")
    private String svnDpmNm;

    @Column(name = "SVN_TEM_NM", length = 100, comment = "주관팀명")
    private String svnTemNm;

    @Column(name = "BSE_YY", length = 4, comment = "예산연도")
    private String bseYy;

    @Column(name = "BG_UNT_ABUS_C", length = 3, comment = "사업코드")
    private String bgUntAbusC;

    @Column(name = "TMN_YN", length = 1, comment = "단말여부")
    private String tmnYn;

    @Column(name = "ABUS_TC", length = 2, comment = "전산업무비구분")
    private String abusTc;

    /** 외화금액 (이력 거울 — @LogTarget AOP가 마스터 fcAmt를 동명 매핑) */
    @Column(name = "FC_AMT", precision = 18, scale = 3, comment = "외화금액")
    private BigDecimal fcAmt;
}
