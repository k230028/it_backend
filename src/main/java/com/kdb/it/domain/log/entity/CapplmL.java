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

import java.time.LocalDate;

/**
 * 신청서 마스터(TAAABB_CAPPLM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_CAPPLL", comment = "신청서 마스터 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CapplmL extends BaseLogEntity {

    @Column(name = "APF_MNG_NO", length = 32, comment = "신청서관리번호")
    private String apfMngNo;

    @Column(name = "APF_STS", length = 32, comment = "신청서상태")
    private String apfSts;

    @Column(name = "APF_NM", length = 800, comment = "신청서명")
    private String apfNm;

    @Lob
    @Column(name = "APF_DTL_CONE", comment = "신청서상세내용")
    private String apfDtlCone;

    @Column(name = "RQS_ENO", length = 32, comment = "요청자사번")
    private String rqsEno;

    @Column(name = "RQS_DT", comment = "요청일자")
    private LocalDate rqsDt;

    @Column(name = "RQS_OPNN", length = 1000, comment = "요청의견")
    private String rqsOpnn;
}
