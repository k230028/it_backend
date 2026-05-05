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
 * 예산(TAAABB_BBUGTM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BBUGTL", comment = "예산 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BbugtL extends BaseLogEntity {

    @Column(name = "BG_MNG_NO", length = 32, comment = "예산관리번호")
    private String bgMngNo;

    @Column(name = "BG_SNO", comment = "예산일련번호")
    private Integer bgSno;

    @Column(name = "BG_YY", length = 4, comment = "예산년도")
    private String bgYy;

    @Column(name = "ORC_TB", length = 10, comment = "원본테이블")
    private String orcTb;

    @Column(name = "ORC_PK_VL", length = 32, comment = "원본PK값")
    private String orcPkVl;

    @Column(name = "ORC_SNO_VL", comment = "원본일련번호값")
    private Integer orcSnoVl;

    @Column(name = "IOE_C", length = 100, comment = "비목코드")
    private String ioeC;

    @Column(name = "DUP_BG", precision = 15, scale = 2, comment = "편성예산")
    private BigDecimal dupBg;

    @Column(name = "DUP_RT", precision = 3, scale = 0, comment = "편성률")
    private Integer dupRt;
}
