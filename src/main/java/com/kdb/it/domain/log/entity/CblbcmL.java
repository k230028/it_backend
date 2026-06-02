package com.kdb.it.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDate;

/**
 * 게시물 변경 로그 엔티티 — TPRMPP_CBLBCL
 */
@Entity
@Table(name = "TPRMPP_CBLBCL", comment = "게시물 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CblbcmL extends BaseLogEntity {

    @Column(name = "NAC_NO",     length = 32, comment = "게시물관리번호")  private String    nacMngNo;
    @Column(name = "BLB_ID",     length = 32, comment = "게시판관리번호")  private String    blbMngNo;
    @Column(name = "NAC_TTL",         length = 300, comment = "게시물명") private String    nacNm;
    @Column(name = "NAC_CONE", length = 4000, comment = "게시물내용")     private String    nacCone;
    @Column(name = "NAC_INQ_NBR", comment = "게시물조회수")                  private Integer   nacInqNbr;
    @Column(name = "NAC_ID",         length = 32, comment = "게시물유형")  private String    nacTp;
    @Column(name = "NAC_KD_TC",           length = 32, comment = "종류코드")  private String    kdC;
    @Column(name = "MRL_PRIT_TC",         length = 32, comment = "중요도코드")  private String    pritC;
    @Column(name = "ANC_YN",     length = 1, comment = "상위고정여부")   private String    hrkFxnYn;
    @Column(name = "SRE_USE_YN",         length = 1, comment = "화면여부")   private String    sreYn;
    @Column(name = "BBR_C",          length = 8, comment = "부점코드")   private String    bbrC;
    @Column(name = "STT_DTM", comment = "시작일자")                      private LocalDate sttDt;
    @Column(name = "END_DTM", comment = "종료일자")                      private LocalDate endDt;
    @Column(name = "FL_APG_YN",      length = 1, comment = "파일첨부여부")   private String    flApgYn;
    @Column(name = "APG_FL_NBR", comment = "파일수")                       private Integer   flNbr;
    @Column(name = "NAC_GRP_NO",     length = 32, comment = "게시물그룹번호")  private String    nacGrpNo;
    @Column(name = "GRP_SQN_SNO", comment = "게시물그룹순서")                  private Integer   nacGrpSqn;
    @Column(name = "NAC_LEV_MNG_SNO", comment = "게시물그룹레벨")                  private Integer   nacGrpLev;
    @Column(name = "HRK_NAC_MNG_NO", length = 32, comment = "상위게시물관리번호")  private String    hrkNacMngNo;
}
