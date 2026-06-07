package com.kdb.it.domain.estimate.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BesttmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 소요예산 산정 상세(명세) 엔티티 — 팀별·비목별 소요예산금액.
 *
 * <p>DB 테이블: {@code TPRMPP_BESTTM}. 마스터(Bestim) 1건에 (담당팀 × 비목) N행.</p>
 */
@LogTarget(entity = BesttmL.class)
@Entity
@Table(name = "TPRMPP_BESTTM", comment = "소요예산 산정 상세(팀별 산정)")
@IdClass(BesttmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Besttm extends BaseEntity {

    @Id
    @Column(name = "RQM_BG_REQ_DOC_NO", length = 30, nullable = false, comment = "소요예산요청문서번호")
    private String rqmBgReqDocNo;

    @Id
    @Column(name = "DOC_VRS_SNO", nullable = false, comment = "문서버전일련번호")
    private Integer docVrsSno;

    @Id
    @Column(name = "SVN_TEM_C", length = 5, nullable = false, comment = "담당팀코드")
    private String svnTemC;

    @Id
    @Column(name = "IOE_C", length = 7, nullable = false, comment = "비목코드")
    private String ioeC;

    @Column(name = "RQM_BG_AMT", precision = 18, comment = "소요예산금액")
    private BigDecimal rqmBgAmt;

    @Column(name = "OPNN_CONE", length = 1000, comment = "의견내용")
    private String opnnCone;

    /** 산정 금액/의견 수정 (작업자가 진행중 상태에서 호출) */
    public void updateEstimate(BigDecimal rqmBgAmt, String opnnCone) {
        this.rqmBgAmt = rqmBgAmt;
        this.opnnCone = opnnCone;
    }
}
