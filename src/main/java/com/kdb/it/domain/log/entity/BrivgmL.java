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
 * 문서 검토의견(TAAABB_BRIVGM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BRIVGL", comment = "문서 검토의견 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BrivgmL extends BaseLogEntity {

    @Column(name = "IVG_SNO", length = 32, comment = "의견일련번호")
    private String ivgSno;

    @Column(name = "DOC_MNG_NO", length = 32, comment = "문서관리번호")
    private String docMngNo;

    @Column(name = "DOC_VRS", precision = 5, scale = 2, comment = "문서버전")
    private BigDecimal docVrs;

    @Column(name = "IVG_TP", length = 1, comment = "의견유형")
    private String ivgTp;

    @Column(name = "IVG_CONE", length = 4000, comment = "의견내용")
    private String ivgCone;

    @Column(name = "IDC_ID", length = 64, comment = "표시ID")
    private String idcId;

    @Column(name = "QOT_CONE", length = 4000, comment = "인용내용")
    private String qotCone;

    @Column(name = "FSG_YN", length = 1, comment = "완료여부")
    private String fsgYn;
}
