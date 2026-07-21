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

/** 대금지급 기본(TPRMPP_BPAYMM) 변경 로그. */
@Entity
@Table(name = "TPRMPP_BPAYML", comment = "대금지급 기본 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BpaymmL extends BaseLogEntity {
    @Column(name = "DOC_MNG_NO", length = 20, comment = "문서관리번호")
    private String docMngNo;

    @Column(name = "DOC_VRS_SNO", comment = "문서버전일련번호")
    private Integer docVrsSno;

    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    @Column(name = "IOE_C", length = 7, comment = "IT포탈예산성격구분코드")
    private String ioeC;

    @Column(name = "CNCD_RFR_NO", length = 30, comment = "관련참조번호(대상관리번호)")
    private String cncdRfrNo;

    @Column(name = "IT_PTL_STS_TC", length = 2, comment = "IT포탈상태구분코드")
    private String stsTc;

    @Column(name = "REQ_CONE", length = 300, comment = "요청내용")
    private String reqCone;

    @Column(name = "CTT_NM", length = 100, comment = "계약명")
    private String cttNm;

    @Column(name = "CTT_AMT", precision = 18, scale = 3, comment = "계약금액")
    private BigDecimal cttAmt;
}
