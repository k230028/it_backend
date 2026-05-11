package com.kdb.it.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDate;

/**
 * 게시물 변경 로그 엔티티 — TAAABB_CBLBCL
 */
@Entity
@Table(name = "TAAABB_CBLBCL")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CblbcmL extends BaseLogEntity {

    @Column(name = "NAC_MNG_NO",     length = 32)  private String    nacMngNo;
    @Column(name = "BLB_MNG_NO",     length = 32)  private String    blbMngNo;
    @Column(name = "NAC_NM",         length = 300) private String    nacNm;
    @Lob
    @Column(name = "NAC_CONE")                     private String    nacCone;
    @Column(name = "NAC_INQ_NBR")                  private Integer   nacInqNbr;
    @Column(name = "NAC_TP",         length = 32)  private String    nacTp;
    @Column(name = "KD_C",           length = 32)  private String    kdC;
    @Column(name = "PRIT_C",         length = 32)  private String    pritC;
    @Column(name = "HRK_FXN_YN",     length = 1)   private String    hrkFxnYn;
    @Column(name = "SRE_YN",         length = 1)   private String    sreYn;
    @Column(name = "BBR_C",          length = 8)   private String    bbrC;
    @Column(name = "STT_YMD")                      private LocalDate sttYmd;
    @Column(name = "END_YMD")                      private LocalDate endYmd;
    @Column(name = "FL_APG_YN",      length = 1)   private String    flApgYn;
    @Column(name = "FL_NBR")                       private Integer   flNbr;
    @Column(name = "NAC_GRP_NO",     length = 32)  private String    nacGrpNo;
    @Column(name = "NAC_GRP_SQN")                  private Integer   nacGrpSqn;
    @Column(name = "NAC_GRP_LEV")                  private Integer   nacGrpLev;
    @Column(name = "HRK_NAC_MNG_NO", length = 32)  private String    hrkNacMngNo;
}
