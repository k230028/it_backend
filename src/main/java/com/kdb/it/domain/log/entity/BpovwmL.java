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
 * 협의회 사업개요(TAAABB_BPOVWM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BPOVWL", comment = "협의회 사업개요 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BpovwmL extends BaseLogEntity {

    @Column(name = "ASCT_ID", length = 32, comment = "협의회ID")
    private String asctId;

    @Column(name = "PRJ_NM", length = 200, comment = "사업명")
    private String prjNm;

    @Column(name = "PRJ_TRM", length = 100, comment = "사업기간")
    private String prjTrm;

    @Column(name = "NCS", length = 1000, comment = "필요성")
    private String ncs;

    @Column(name = "PRJ_BG", comment = "소요예산")
    private Long prjBg;

    @Column(name = "EDRT", length = 32, comment = "전결권자")
    private String edrt;

    @Column(name = "PRJ_DES", length = 1000, comment = "사업내용")
    private String prjDes;

    @Column(name = "LGL_RGL_YN", length = 1, comment = "법률규제대응여부")
    private String lglRglYn;

    @Column(name = "LGL_RGL_NM", length = 500, comment = "관련법률규제명")
    private String lglRglNm;

    @Column(name = "XPT_EFF", length = 1000, comment = "기대효과")
    private String xptEff;

    @Column(name = "KPN_TP", length = 10, comment = "저장유형")
    private String kpnTp;

    @Column(name = "FL_MNG_NO", length = 32, comment = "첨부파일관리번호")
    private String flMngNo;
}
