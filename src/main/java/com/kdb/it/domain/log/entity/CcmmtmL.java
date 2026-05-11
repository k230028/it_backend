package com.kdb.it.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 게시판 댓글 변경 로그 엔티티 — TAAABB_CCMMTL
 */
@Entity
@Table(name = "TAAABB_CCMMTL", comment = "댓글 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CcmmtmL extends BaseLogEntity {

    @Column(name = "CMMT_MNG_NO",      length = 32, comment = "댓글관리번호") private String  cmmtMngNo;
    @Column(name = "NAC_MNG_NO",       length = 32, comment = "게시물관리번호") private String  nacMngNo;
    @Lob
    @Column(name = "CMMT_CONE", comment = "댓글내용")                     private String  cmmtCone;
    @Column(name = "SRE_YN",           length = 1, comment = "화면여부")  private String  sreYn;
    @Column(name = "CMMT_GRP_NO",      length = 32, comment = "댓글그룹번호") private String  cmmtGrpNo;
    @Column(name = "CMMT_GRP_SQN", comment = "댓글그룹순서")                  private Integer cmmtGrpSqn;
    @Column(name = "CMMT_GRP_LEV", comment = "댓글그룹레벨")                  private Integer cmmtGrpLev;
    @Column(name = "HRK_CMMT_MNG_NO",  length = 32, comment = "상위댓글관리번호") private String  hrkCmmtMngNo;
}
