package com.kdb.it.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 게시판 메타 변경 로그 엔티티 — TPRMPP_CBLBML
 *
 * <p>{@link com.kdb.it.common.board.entity.Cblbmm}의 CUD 이벤트 발생 시
 * {@link com.kdb.it.domain.log.listener.ChangeLogEntityListener}가 자동 적재한다.</p>
 */
@Entity
@Table(name = "TPRMPP_CBLBML", comment = "게시판 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CblbmmL extends BaseLogEntity {

    @Column(name = "BLB_ID",      length = 32, comment = "게시판관리번호")  private String  blbMngNo;
    @Column(name = "BLB_NM",          length = 100, comment = "게시판명") private String  blbNm;
    @Column(name = "BLB_TC",          length = 3, comment = "게시판구분코드")  private String  blbTp;
    @Column(name = "REP_FNC_USE_YN",      length = 1, comment = "답변사용여부")   private String  repUseYn;
    @Column(name = "CMMT_USE_YN",     length = 1, comment = "댓글사용여부")   private String  cmmtUseYn;
    @Column(name = "APG_FL_USE_YN",       length = 1, comment = "파일필수여부")   private String  flEsnYn;
    @Column(name = "IOA_TC",  length = 1, comment = "상위고정사용여부")   private String  hrkFxnUseYn;
    @Column(name = "HED_TAG_USE_YN",   length = 1, comment = "게시물유형사용여부")   private String  nacTpUseYn;
    @Column(name = "KD_USE_YN",       length = 1, comment = "종류사용여부")   private String  kdUseYn;
    @Column(name = "INQ_DWN_ATH_TC",       length = 32, comment = "조회권한코드")  private String  inqAthC;
    @Column(name = "WRT_DWN_ATH_TC",       length = 32, comment = "등록권한코드")  private String  enrAthC;
    @Column(name = "SRE_SQN_SNO", comment = "화면순서번호")                    private Integer sreSqnNo;
    @Column(name = "USE_YN",          length = 1, comment = "사용여부")   private String  useYn;
    @Column(name = "RMK",             length = 300, comment = "비고") private String  rmk;
}
