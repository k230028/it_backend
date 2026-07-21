package com.kdb.it.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 게시판 댓글 변경 로그 엔티티 — TPRMPP_CCMMTL
 */
@Entity
@Table(name = "TPRMPP_CCMMTL", comment = "댓글 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CcmmtmL extends BaseLogEntity {

    @Column(name = "CMMT_SNO",      precision = 9, comment = "댓글관리번호") private Long  cmmtMngNo;
    @Column(name = "NAC_NO",       length = 32, comment = "게시물관리번호") private String  nacMngNo;
    @Column(name = "CMMT_CONE", length = 4000, comment = "댓글내용")     private String  cmmtCone;
    @Column(name = "CMMT_TGT_SNO",      precision = 9, comment = "댓글그룹번호") private Long  cmmtGrpNo;
    @Column(name = "CMMT_SQN_SNO", comment = "댓글그룹순서")                  private Integer cmmtGrpSqn;
    @Column(name = "CMMT_DEP_NBR", comment = "댓글그룹레벨")                  private Integer cmmtGrpLev;
    @Column(name = "HRK_CMMT_SNO",  precision = 9, comment = "상위댓글관리번호") private Long  hrkCmmtMngNo;
}
