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

/** 소요예산 산정 상세(TPRMPP_BESTTM) 변경 로그. */
@Entity
@Table(name = "TPRMPP_BESTTL", comment = "소요예산 산정 상세 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BesttmL extends BaseLogEntity {

    @Column(name = "RQM_BG_REQ_DOC_NO", length = 30, comment = "소요예산요청문서번호")
    private String rqmBgReqDocNo;

    @Column(name = "DOC_VRS_SNO", comment = "문서버전일련번호")
    private Integer docVrsSno;

    @Column(name = "SVN_TEM_C", length = 5, comment = "담당팀코드")
    private String svnTemC;

    @Column(name = "IOE_C", length = 7, comment = "비목코드")
    private String ioeC;

    @Column(name = "RQM_BG_AMT", precision = 18, comment = "소요예산금액")
    private BigDecimal rqmBgAmt;

    @Column(name = "OPNN_CONE", length = 1000, comment = "의견내용")
    private String opnnCone;
}
