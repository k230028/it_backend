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
 * 정보화사업(TAAABB_BPROJM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BPROJL", comment = "정보화사업 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BprojmL extends BaseLogEntity {

    @Column(name = "PRJ_MNG_NO", length = 32, comment = "프로젝트관리번호")
    private String prjMngNo;

    @Column(name = "PRJ_SNO", comment = "프로젝트순번")
    private Integer prjSno;

    @Column(name = "PRJ_NM", length = 200, comment = "프로젝트명")
    private String prjNm;

    @Column(name = "PRJ_TP", length = 100, comment = "프로젝트유형")
    private String prjTp;

    @Column(name = "SVN_DPM", length = 100, comment = "주관부서")
    private String svnDpm;

    @Column(name = "IT_DPM", length = 100, comment = "IT부서")
    private String itDpm;

    @Column(name = "PRJ_BG", precision = 15, scale = 2, comment = "프로젝트예산")
    private BigDecimal prjBg;

    @Column(name = "NYY_PRJ_BG", precision = 15, scale = 2, comment = "익년프로젝트예산")
    private BigDecimal nyyPrjBg;

    @Column(name = "STT_DT", comment = "시작일자")
    private LocalDate sttDt;

    @Column(name = "END_DT", comment = "종료일자")
    private LocalDate endDt;

    @Column(name = "SVN_DPM_CGPR", length = 32, comment = "주관부서담당자")
    private String svnDpmCgpr;

    @Column(name = "IT_DPM_CGPR", length = 32, comment = "IT부서담당자")
    private String itDpmCgpr;

    @Column(name = "SVN_DPM_TLR", length = 32, comment = "주관부서담당팀장")
    private String svnDpmTlr;

    @Column(name = "IT_DPM_TLR", length = 32, comment = "IT부서담당팀장")
    private String itDpmTlr;

    @Column(name = "EDRT", length = 32, comment = "전결권")
    private String edrt;

    @Column(name = "PRJ_DES", length = 1000, comment = "사업설명")
    private String prjDes;

    @Column(name = "SAF", length = 1000, comment = "현황")
    private String saf;

    @Column(name = "NCS", length = 1000, comment = "필요성")
    private String ncs;

    @Column(name = "XPT_EFF", length = 1000, comment = "기대효과")
    private String xptEff;

    @Column(name = "PLM", length = 1000, comment = "문제")
    private String plm;

    @Column(name = "PRJ_RNG", length = 1000, comment = "사업범위")
    private String prjRng;

    @Column(name = "PUL_PSG", length = 1000, comment = "추진경과")
    private String pulPsg;

    @Column(name = "HRF_PLN", length = 1000, comment = "향후계획")
    private String hrfPln;

    @Column(name = "BZ_DTT", length = 32, comment = "업무구분")
    private String bzDtt;

    @Column(name = "TCHN_TP", length = 32, comment = "기술유형")
    private String tchnTp;

    @Column(name = "MN_USR", length = 32, comment = "주요사용자")
    private String mnUsr;

    @Column(name = "DPL_YN", length = 1, comment = "중복여부")
    private String dplYn;

    @Column(name = "LBL_FSG_TLM", comment = "의무완료기한")
    private LocalDate lblFsgTlm;

    @Column(name = "RPR_STS", length = 32, comment = "보고상태")
    private String rprSts;

    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    @Column(name = "PRJ_PUL_PTT", precision = 3, scale = 0, comment = "프로젝트추진가능성")
    private Integer prjPulPtt;

    @Column(name = "PRJ_STS", length = 32, comment = "프로젝트상태")
    private String prjSts;

    @Column(name = "BG_YY", length = 4, comment = "예산연도")
    private String bgYy;

    @Column(name = "SVN_HDQ", length = 32, comment = "주관본부")
    private String svnHdq;

    @Column(name = "ORN_YN", length = 1, comment = "경상여부")
    private String ornYn;

    @Column(name = "PUL_DTT", length = 32, comment = "사업구분")
    private String pulDtt;
}
