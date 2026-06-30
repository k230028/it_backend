package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 정보화실무협의회 타당성검토 생략판정요청(TPRMPP_BASKPM) 변경 로그 엔티티. (PRD_c_20260620 #3)
 */
@Entity
@Table(name = "TPRMPP_BASKPL", comment = "정보화실무협의회 타당성검토 생략판정요청 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BaskpmL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "PRTY_IVG_OMT_RSN_TC", length = 2, comment = "타당성검토생략사유구분코드")
    private String prtyIvgOmtRsnTc;

    @Column(name = "CGPR_OPNN_CONE", length = 4000, comment = "담당자의견내용")
    private String cgprOpnnCone;

    @Column(name = "FL_MPN_ID", length = 36, comment = "첨부파일관리번호")
    private String flMpnId;

    @Column(name = "RQS_USID", length = 14, comment = "신청사용자ID")
    private String rqsUsid;

    @Column(name = "RQS_DTM", comment = "신청일시")
    private LocalDateTime rqsDtm;

    @Column(name = "PRTY_IVG_OMT_YN", length = 1, comment = "타당성검토생략여부")
    private String prtyIvgOmtYn;

    @Column(name = "CGPR_RPD_CONE", length = 4000, comment = "담당자응답내용")
    private String cgprRpdCone;

    @Column(name = "CNFM_USID", length = 14, comment = "확인사용자ID")
    private String cnfmUsid;

    @Column(name = "CNFM_DTM", comment = "확인일시")
    private LocalDateTime cnfmDtm;

    @Column(name = "APF_MNG_NO", length = 30, comment = "신청관리번호")
    private String apfMngNo;
}
