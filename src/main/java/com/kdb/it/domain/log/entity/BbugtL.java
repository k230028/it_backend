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

/** 예산(TPRMPP_BBUGTM) 변경 로그 엔티티. */
@Entity
@Table(name = "TPRMPP_BBUGTL", comment = "예산 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BbugtL extends BaseLogEntity {

    @Column(name = "BG_NO", length = 32, comment = "예산관리번호")
    private String bgNo;

    @Column(name = "SNO", comment = "예산일련번호")
    private Integer sno;

    @Column(name = "BSE_YY", length = 4, comment = "예산년도")
    private String bseYy;

    @Column(name = "FNT_TB_NM", length = 10, comment = "원본테이블")
    private String fntTbNm;

    @Column(name = "PK_COL_NM", length = 32, comment = "원본PK값")
    private String pkColNm;

    @Column(name = "FNT_TB_CRY_SNO", comment = "원본일련번호값")
    private Integer fntTbCrySno;

    @Column(name = "IOE_C", length = 7, comment = "비목코드")
    private String ioeC;

    @Column(name = "BG_DUP_AMT", precision = 18, scale = 3, comment = "편성예산금액")
    private BigDecimal bgDupAmt;

    @Column(name = "ASG_RT", precision = 8, scale = 5, comment = "편성률")
    private BigDecimal asgRt;
}
