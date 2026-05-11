package com.kdb.it.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 게시판 댓글 변경 로그 엔티티 — TAAABB_CCMMTL
 */
@Entity
@Table(name = "TAAABB_CCMMTL")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CcmmtmL extends BaseLogEntity {

    @Column(name = "CMMT_MNG_NO",      length = 32) private String  cmmtMngNo;
    @Column(name = "NAC_MNG_NO",       length = 32) private String  nacMngNo;
    @Lob
    @Column(name = "CMMT_CONE")                     private String  cmmtCone;
    @Column(name = "SRE_YN",           length = 1)  private String  sreYn;
    @Column(name = "CMMT_GRP_NO",      length = 32) private String  cmmtGrpNo;
    @Column(name = "CMMT_GRP_SQN")                  private Integer cmmtGrpSqn;
    @Column(name = "CMMT_GRP_LEV")                  private Integer cmmtGrpLev;
    @Column(name = "HRK_CMMT_MNG_NO",  length = 32) private String  hrkCmmtMngNo;
}
