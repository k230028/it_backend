package com.kdb.it.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDate;

/**
 * 게시물 변경 로그 엔티티 — TAAABB_CBLBCL
 */
@Entity
@Table(name = "TAAABB_CBLBCL", comment = "게시물 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CblbcmL extends BaseLogEntity {

    @Column(name = "NAC_MNG_NO",     length = 32, comment = "게시물관리번호")  private String    nacMngNo;
    @Column(name = "BLB_MNG_NO",     length = 32, comment = "게시판관리번호")  private String    blbMngNo;
    @Column(name = "NAC_NM",         length = 300, comment = "게시물명") private String    nacNm;
    @Column(name = "NAC_CONE", length = 4000, comment = "게시물내용")     private String    nacCone;
    @Column(name = "NAC_INQ_NBR", comment = "게시물조회수")                  private Integer   nacInqNbr;
    @Column(name = "NAC_TP",         length = 32, comment = "게시물유형")  private String    nacTp;
    @Column(name = "KD_C",           length = 32, comment = "종류코드")  private String    kdC;
    @Column(name = "PRIT_C",         length = 32, comment = "중요도코드")  private String    pritC;
    @Column(name = "HRK_FXN_YN",     length = 1, comment = "상위고정여부")   private String    hrkFxnYn;
    @Column(name = "SRE_YN",         length = 1, comment = "화면여부")   private String    sreYn;
    @Column(name = "BBR_C",          length = 8, comment = "부점코드")   private String    bbrC;
    @Column(name = "STT_DT", comment = "시작일자")                      private LocalDate sttDt;
    @Column(name = "END_DT", comment = "종료일자")                      private LocalDate endDt;
    @Column(name = "FL_APG_YN",      length = 1, comment = "파일첨부여부")   private String    flApgYn;
    @Column(name = "FL_NBR", comment = "파일수")                       private Integer   flNbr;
    @Column(name = "NAC_GRP_NO",     length = 32, comment = "게시물그룹번호")  private String    nacGrpNo;
    @Column(name = "NAC_GRP_SQN", comment = "게시물그룹순서")                  private Integer   nacGrpSqn;
    @Column(name = "NAC_GRP_LEV", comment = "게시물그룹레벨")                  private Integer   nacGrpLev;
    @Column(name = "HRK_NAC_MNG_NO", length = 32, comment = "상위게시물관리번호")  private String    hrkNacMngNo;
}
