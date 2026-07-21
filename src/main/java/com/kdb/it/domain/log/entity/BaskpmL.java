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
@Table(name = "TPRMPP_BASKPL", comment = "프로젝트관리_협의회제외요청변경로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BaskpmL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "CGPR_OPNN_CONE", length = 4000, comment = "담당자의견내용")
    private String cgprOpnnCone;

    @Column(name = "FL_MPN_ID", length = 36, comment = "파일매핑ID")
    private String flMpnId;

    @Column(name = "RQS_USID", length = 14, comment = "신청사용자ID")
    private String rqsUsid;

    @Column(name = "RQS_DTM", comment = "신청일시")
    private LocalDateTime rqsDtm;

    @Column(name = "CNFM_USID", length = 14, comment = "확인사용자ID")
    private String cnfmUsid;

    @Column(name = "CNFM_DTM", comment = "확인일시")
    private LocalDateTime cnfmDtm;

    @Column(name = "APF_DCM_NO", length = 64, comment = "신청서식별번호")
    private String apfMngNo;
}
