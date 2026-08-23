package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 정보화사업(TPRMPP_BPROJM) 변경 로그 엔티티. */
@Entity
@Table(name = "TPRMPP_BPROJL", comment = "정보화사업 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BprojmL extends BaseLogEntity {

    @Column(name = "ABUS_MNG_NO", length = 30, comment = "프로젝트관리번호")
    private String abusMngNo;

    @Column(name = "SNO", comment = "프로젝트순번")
    private Integer sno;

    @Column(name = "ABUS_NM", length = 100, comment = "사업명")
    private String abusNm;

    @Column(name = "ABUS_PPO_CONE", length = 300, comment = "사업유형명 (물리컬럼 ABUS_PPO_CONE=사업목적내용)")
    private String bzTpC;

    @Column(name = "SVN_DPM_C", length = 20, comment = "주관부서")
    private String svnDpmC;

    @Column(name = "SVN_TEM_C", length = 5, comment = "주관팀코드")
    private String svnTemC;

    @Column(name = "SVN_DPM_NM", length = 100, comment = "주관부서명")
    private String svnDpmNm;

    @Column(name = "SVN_TEM_NM", length = 100, comment = "주관팀명")
    private String svnTemNm;

    @Column(name = "DVM_DPM_C", length = 20, comment = "IT부서")
    private String dvmDpmC;

    @Column(name = "DVM_TEM_C", length = 5, comment = "개발팀코드")
    private String dvmTemC;

    @Column(name = "STT_DTM", comment = "시작일자")
    private LocalDate sttDtm;

    @Column(name = "END_DTM", comment = "종료일자")
    private LocalDate endDtm;

    @Column(name = "TOT_RQM_AMT", precision = 18, scale = 3, comment = "총소요금액")
    private BigDecimal totRqmAmt;

    @Column(name = "MPL_AMT", precision = 18, scale = 3, comment = "예정금액")
    private BigDecimal mplAmt;

    @Column(name = "DFR_AMT", precision = 18, scale = 3, comment = "지급금액")
    private BigDecimal dfrAmt;

    @Column(name = "USID", length = 14, comment = "주관부서담당자")
    private String usid;

    @Column(name = "DVM_USID", length = 14, comment = "IT부서담당자")
    private String dvmUsid;

    @Column(name = "TLR_USID", length = 14, comment = "주관부서담당팀장")
    private String tlrUsid;

    @Column(name = "TLR_NM", length = 100, comment = "주관부서담당팀장명")
    private String tlrNm;

    @Column(name = "USR_NM", length = 100, comment = "주관부서담당자명")
    private String usrNm;

    @Column(name = "DVM_TLR_USID", length = 14, comment = "IT부서담당팀장")
    private String dvmTlrUsid;

    @Column(name = "IT_PTL_EDRT_TC", length = 2, comment = "전결권")
    private String edrtTc;

    @Column(name = "ABUS_CONE", length = 1000, comment = "사업설명")
    private String abusCone;

    @Column(name = "CPN_SAF_CONE", length = 1000, comment = "현황")
    private String cpnSafCone;

    @Column(name = "ABUS_NCS_CONE", length = 300, comment = "필요성")
    private String abusNcsCone;

    @Column(name = "DGOG_PPO_CONE", length = 4000, comment = "기대효과")
    private String dgogPpoCone;

    @Column(name = "PLM_DES", length = 4000, comment = "문제")
    private String plmDes;

    @Column(name = "ABUS_RNG_CONE", length = 600, comment = "사업범위내용")
    private String abusRngCone;

    @Column(name = "MN_PRG_CONE", length = 2000, comment = "주요진행내용")
    private String mnPrgCone;

    @Column(name = "HRF_PLN_CONE", length = 300, comment = "향후계획")
    private String hrfPlnCone;

    @Column(name = "BZ_DTT_NM", length = 100, comment = "업무구분명")
    private String bzDttNm;

    @Column(name = "SKL_FLD_NM", length = 500, comment = "기술분야명 (물리컬럼 SKL_FLD_NM)")
    private String sklTpTc;

    @Column(name = "CST_TP_TC_NM", length = 1000, comment = "고객유형구분코드명 (물리컬럼 CST_TP_TC_NM)")
    private String cstTpTc;

    @Column(name = "DPL_YN", length = 1, comment = "중복여부")
    private String dplYn;

    @Column(name = "FLF_FSG_DT", length = 8, comment = "의무완료기한")
    private String flfFsgDt;

    @Column(name = "IT_PTL_RPR_STS_TC", length = 2, comment = "보고상태 (공통코드 2자리)")
    private String rprStsTc;

    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    @Column(name = "EXE_PTT_YN", length = 1, comment = "프로젝트추진가능성")
    private String exePttYn;

    @Column(name = "BSE_YY", length = 4, comment = "예산연도")
    private String bseYy;

    @Column(name = "PRLM_HRK_OGZ_C_CONE", length = 100, comment = "주관본부")
    private String prlmHrkOgzCCone;

    @Column(name = "ODN_YN", length = 1, comment = "경상여부")
    private String odnYn;

    @Column(name = "ABUS_TC", length = 2, nullable = false, comment = "사업구분")
    private String abusTc;

    /** 관련프로젝트관리번호 (이력 거울 — @LogTarget AOP가 마스터 cncdRfrNo를 동명 매핑) */
    @Column(name = "CNCD_RFR_NO", length = 30, comment = "관련참조번호")
    private String cncdRfrNo;
}
