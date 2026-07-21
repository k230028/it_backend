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

    @Column(name = "NAC_NO",     length = 32, comment = "게시물번호")  private String    nacMngNo;
    @Column(name = "BLB_ID",     length = 32, comment = "게시판ID")  private String    blbMngNo;
    @Column(name = "NAC_TTL",         length = 300, comment = "게시물제목") private String    nacNm;
    @Column(name = "NAC_CONE", length = 4000, comment = "게시물내용")     private String    nacCone;
    @Column(name = "NAC_INQ_NBR", comment = "게시물조회수")                  private Integer   nacInqNbr;
    @Column(name = "NAC_UNQ_ID", length = 16, comment = "게시물고유ID")  private String    nacUnqId;
    @Column(name = "ANC_YN",     length = 1, comment = "공지여부")   private String    ancYn;
    @Column(name = "XPO_YN",         length = 1, comment = "노출여부")   private String    xpoYn;
    @Column(name = "BBR_C",          length = 8, comment = "부점코드")   private String    bbrC;
    @Column(name = "STT_DTM", comment = "시작일시")                      private LocalDate sttDt;
    @Column(name = "END_DTM", comment = "종료일시")                      private LocalDate endDt;
    @Column(name = "FL_APG_YN",      length = 1, comment = "파일첨부여부")   private String    flApgYn;
    @Column(name = "APG_FL_NBR", comment = "첨부파일수")                       private Integer   flNbr;
    @Column(name = "GRP_SQN_SNO", comment = "그룹순서일련번호")                  private Integer   nacGrpSqn;
    @Column(name = "NAC_LEV_MNG_SNO", comment = "게시물레벨관리일련번호")                  private Integer   nacGrpLev;
    @Column(name = "HRK_NAC_NO", length = 16, comment = "상위게시물번호")  private String    hrkNacNo;
}
