package com.kdb.it.domain.payment.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BpaymtL;
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
 * 대금지급 상세(명세) 엔티티 — 회차별 지급.
 *
 * <p>DB 테이블: {@code TPRMPP_BPAYTM}. 마스터(Bpaymm) 1건에 회차(DFR_TOD)별 N행.</p>
 */
@LogTarget(entity = BpaymtL.class)
@Entity
@Table(name = "TPRMPP_BPAYTM", comment = "대금지급 상세(회차별 지급)")
@IdClass(BpaymtId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bpaymt extends BaseEntity {

    @Id
    @Column(name = "DOC_MNG_NO", length = 20, nullable = false, comment = "문서관리번호")
    private String docMngNo;

    @Id
    @Column(name = "DOC_VRS_SNO", nullable = false, comment = "문서버전일련번호")
    private Integer docVrsSno;

    @Id
    @Column(name = "DFR_TOD", nullable = false, comment = "지급회차")
    private Integer dfrTod;

    @Column(name = "DFR_AMT", precision = 18, scale = 3, comment = "지급금액")
    private BigDecimal dfrAmt;

    @Column(name = "DFR_DT", length = 8, comment = "지급일자")
    private String dfrDt;

    @Column(name = "DFR_MPL_DT", length = 8, comment = "지급예정일자")
    private String dfrMplDt;

    @Column(name = "OPNN_CONE", length = 1000, comment = "의견내용")
    private String opnnCone;

    /** 회차 지급 정보 수정 (작업자가 진행중 상태에서 호출) */
    public void updatePayment(BigDecimal dfrAmt, String dfrDt, String dfrMplDt, String opnnCone) {
        this.dfrAmt = dfrAmt;
        this.dfrDt = dfrDt;
        this.dfrMplDt = dfrMplDt;
        this.opnnCone = opnnCone;
    }
}
