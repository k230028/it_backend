package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 정보화사업(TPRMPP_BPROJM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_BPROJL", comment = "정보화사업 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BprojmL extends BaseLogEntity {

    @Column(name = "ABUS_MNG_NO", length = 32, comment = "프로젝트관리번호")
    private String prjMngNo;

    @Column(name = "SNO", comment = "프로젝트순번")
    private Integer prjSno;

    @Column(name = "PRJ_NM", length = 200, comment = "프로젝트명")
    private String prjNm;

    @Column(name = "PRJ_BZ_TC", length = 100, comment = "프로젝트유형")
    private String prjTp;

    @Column(name = "SVN_DPM_C", length = 100, comment = "주관부서")
    private String svnDpm;

    @Column(name = "DVM_DPM_C", length = 100, comment = "IT부서")
    private String itDpm;

    @Column(name = "RQM_BG_AMT", precision = 15, scale = 2, comment = "프로젝트예산")
    private BigDecimal prjBg;

    @Column(name = "MPL_AMT", precision = 15, scale = 2, comment = "익년프로젝트예산")
    private BigDecimal nyyPrjBg;

    @Column(name = "STT_DTM", comment = "시작일자")
    private LocalDate sttDt;

    @Column(name = "END_DTM", comment = "종료일자")
    private LocalDate endDt;

    @Column(name = "SVN_DPM_USID", length = 32, comment = "주관부서담당자")
    private String svnDpmCgpr;

    @Column(name = "DVM_USID", length = 32, comment = "IT부서담당자")
    private String itDpmCgpr;

    @Column(name = "SVN_DPM_DCD_USID", length = 32, comment = "주관부서담당팀장")
    private String svnDpmTlr;

    @Column(name = "TLR_USID", length = 32, comment = "IT부서담당팀장")
    private String itDpmTlr;

    @Column(name = "EDRT_TC", length = 32, comment = "전결권")
    private String edrt;

    @Column(name = "ABUS_CONE", length = 1000, comment = "사업설명")
    private String prjDes;

    @Column(name = "CPN_SAF_CONE", length = 1000, comment = "현황")
    private String saf;

    @Column(name = "ABUS_NCS_CONE", length = 1000, comment = "필요성")
    private String ncs;

    @Column(name = "DGOG_PPO_CONE", length = 1000, comment = "기대효과")
    private String xptEff;

    @Column(name = "PLM_DES", length = 1000, comment = "문제")
    private String plm;

    @Column(name = "PRJ_TGT_RNG_CONE", length = 1000, comment = "사업범위")
    private String prjRng;

    @Column(name = "MN_PRG_CONE", length = 2000, comment = "주요진행내용")
    private String pulPsg;

    @Column(name = "HRF_PLN_CONE", length = 1000, comment = "향후계획")
    private String hrfPln;

    @Column(name = "BZ_DTT_NM", length = 32, comment = "업무구분")
    private String bzDtt;

    @Column(name = "SKL_TP_TC", length = 32, comment = "기술유형")
    private String tchnTp;

    @Column(name = "CST_TP_TC", length = 32, comment = "주요사용자")
    private String mnUsr;

    @Column(name = "DPL_YN", length = 1, comment = "중복여부")
    private String dplYn;

    @Column(name = "FLF_FSG_DT", comment = "의무완료기한")
    private String lblFsgTlm;

    @Column(name = "RPR_STS_TC", length = 32, comment = "보고상태")
    private String rprSts;

    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    @Column(name = "EXE_PTT_YN", length = 3, comment = "프로젝트추진가능성")
    private String prjPulPtt;

    @Column(name = "STS_TC", length = 32, comment = "프로젝트상태")
    private String prjSts;

    @Column(name = "BSE_YY", length = 4, comment = "예산연도")
    private String bgYy;

    @Column(name = "PRLM_HRK_OGZ_C_CONE", length = 32, comment = "주관본부")
    private String svnHdq;

    @Column(name = "ODN_YN", length = 1, comment = "경상여부")
    private String ornYn;

    @Column(name = "ABUS_TC", length = 32, comment = "사업구분")
    private String pulDtt;
}
