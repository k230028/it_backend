package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 요구사항 정의서(TPRMPP_BRDOCM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BRDOCL", comment = "요구사항 정의서 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BrdocmL extends BaseLogEntity {

    @Column(name = "DOC_MNG_NO", length = 32, comment = "문서관리번호")
    private String docMngNo;

    @Column(name = "DOC_VRS_SNO", precision = 4, scale = 2, comment = "문서버전")
    private BigDecimal docVrs;

    @Column(name = "REQ_TTL", length = 200, comment = "요구사항명")
    private String reqNm;

    @Lob
    @Column(name = "REDT_CONE_INF", comment = "요구사항정보")
    private String reqInf;

    @Column(name = "REQ_DTT_NO", length = 32, comment = "요구사항구분")
    private String reqDtt;

    @Column(name = "BZ_DTT_NM", length = 32, comment = "업무구분")
    private String bzDtt;

    @Column(name = "RVW_FSG_TLM_DT", comment = "완료기한")
    private String fsgTlm;
}
