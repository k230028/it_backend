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
 * 정보화사업 품목(TPRMPP_BITEMM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BITEML", comment = "정보화사업 품목 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BitemmL extends BaseLogEntity {

    @Column(name = "GCL_MNG_NO", length = 16, comment = "품목관리번호")
    private String gclMngNo;

    @Column(name = "SNO", precision = 9, comment = "일련번호")
    private Integer gclSno;

    @Column(name = "ABUS_MNG_NO", length = 32, comment = "사업관리번호")
    private String prjMngNo;

    @Column(name = "FNT_TB_CRY_SNO", comment = "원천테이블적재일련번호")
    private Integer prjSno;

    @Column(name = "IOE_C", length = 3, comment = "품목구분")
    private String ioeC;

    @Column(name = "GCL_NM", length = 100, comment = "품목명")
    private String gclNm;

    @Column(name = "QTY", precision = 10, comment = "품목수량")
    private BigDecimal gclQty;

    @Column(name = "CUR_C", length = 3, comment = "통화코드")
    private String curC;

    @Column(name = "XCR", precision = 9, scale = 4, comment = "환율")
    private BigDecimal xcr;

    @Column(name = "XCR_BSE_DT", comment = "환율기준일자")
    private String xcrBseDt;

    @Column(name = "CNCD_FDTN_CONE", length = 300, comment = "예산근거내용")
    private String bgFdtnCone;

    @Column(name = "BSE_YM", length = 6, comment = "추진년월")
    private String itdYm;

    @Column(name = "DFR_CLE_C", length = 3, comment = "지급주기코드")
    private String dfrCleC;

    @Column(name = "SECT_SYS_UTZ_YN", length = 1, comment = "정보보호여부")
    private String infPrtYn;

    @Column(name = "ITR_INFR_YN", length = 1, comment = "통합인프라여부")
    private String itrInfrYn;

    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    @Column(name = "AMT", precision = 18, scale = 3, comment = "품목금액")
    private BigDecimal gclAmt;

    /** 외화금액 (이력 거울 — @LogTarget AOP가 마스터 fcAmt를 동명 매핑) */
    @Column(name = "FC_AMT", precision = 18, scale = 3, comment = "외화금액")
    private BigDecimal fcAmt;
}
