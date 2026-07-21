package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 협의회 사업개요(TPRMPP_BPOVWM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BPOVWL", comment = "협의회 사업개요 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BpovwmL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "ABUS_NM", length = 100, comment = "사업명")
    private String abusNm;

    @Column(name = "ABUS_TRM_CONE", length = 300, comment = "사업기간내용")
    private String abusTrmCone;

    @Column(name = "ABUS_NCS_CONE", length = 300, comment = "필요성내용")
    private String abusNcsCone;

    @Column(name = "RQM_BG_AMT", precision = 18, comment = "소요예산금액")
    private java.math.BigDecimal rqmBgAmt;

    @Column(name = "IT_PTL_EDRT_TC", length = 2, comment = "전결권자명")
    private String itPtlEdrtTc;

    @Column(name = "ABUS_CONE", length = 1000, comment = "사업내용")
    private String abusCone;

    @Column(name = "LW_RGL_YN", length = 1, comment = "법률규제대응여부")
    private String lwRglYn;

    @Column(name = "LW_FDTN", length = 300, comment = "관련법률규제명")
    private String lwFdtn;

    @Column(name = "DGOG_PPO_CONE", length = 4000, comment = "기대효과내용")
    private String dgogPpoCone;

    @Column(name = "KPN_TP_TC", length = 2, comment = "저장유형구분코드")
    private String kpnTpTc;

    @Column(name = "FL_MPN_ID", length = 36, comment = "첨부파일관리번호")
    private String flMpnId;
}
