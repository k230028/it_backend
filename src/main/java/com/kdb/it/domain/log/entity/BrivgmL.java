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
 * 문서 검토의견(TPRMPP_BRIVGM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BRIVGL", comment = "문서 검토의견 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BrivgmL extends BaseLogEntity {

    @Column(name = "IPM_OPNN_SNO", comment = "의견일련번호")
    private Long ipmOpnnSno;

    @Column(name = "DOC_MNG_NO", length = 32, comment = "문서관리번호")
    private String docMngNo;

    @Column(name = "DOC_VRS_SNO", precision = 5, scale = 2, comment = "문서버전")
    private BigDecimal docVrsSno;

    @Column(name = "RPL_OPNN_TC", length = 1, comment = "의견유형")
    private String rplOpnnTc;

    @Column(name = "IVG_OPNN_CONE", length = 4000, comment = "의견내용")
    private String ivgOpnnCone;

    @Column(name = "RFR_ID", length = 14, comment = "표시ID")
    private String rfrId;

    @Column(name = "RFR_CONE", length = 4000, comment = "인용내용")
    private String rfrCone;

    @Column(name = "FSG_YN", length = 1, comment = "완료여부")
    private String fsgYn;
}
