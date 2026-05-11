package com.kdb.it.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 게시판 메타 변경 로그 엔티티 — TAAABB_CBLBML
 *
 * <p>{@link com.kdb.it.common.board.entity.Cblbmm}의 CUD 이벤트 발생 시
 * {@link com.kdb.it.domain.log.listener.ChangeLogEntityListener}가 자동 적재한다.</p>
 */
@Entity
@Table(name = "TAAABB_CBLBML", comment = "게시판 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CblbmmL extends BaseLogEntity {

    @Column(name = "BLB_MNG_NO",      length = 32, comment = "게시판관리번호")  private String  blbMngNo;
    @Column(name = "BLB_NM",          length = 100, comment = "게시판명") private String  blbNm;
    @Column(name = "BLB_TP",          length = 32, comment = "게시판유형")  private String  blbTp;
    @Column(name = "REP_USE_YN",      length = 1, comment = "답변사용여부")   private String  repUseYn;
    @Column(name = "CMMT_USE_YN",     length = 1, comment = "댓글사용여부")   private String  cmmtUseYn;
    @Column(name = "FL_ESN_YN",       length = 1, comment = "파일필수여부")   private String  flEsnYn;
    @Column(name = "HRK_FXN_USE_YN",  length = 1, comment = "상위고정사용여부")   private String  hrkFxnUseYn;
    @Column(name = "NAC_TP_USE_YN",   length = 1, comment = "게시물유형사용여부")   private String  nacTpUseYn;
    @Column(name = "KD_USE_YN",       length = 1, comment = "종류사용여부")   private String  kdUseYn;
    @Column(name = "INQ_ATH_C",       length = 32, comment = "조회권한코드")  private String  inqAthC;
    @Column(name = "ENR_ATH_C",       length = 32, comment = "등록권한코드")  private String  enrAthC;
    @Column(name = "BBR_LMTN_USE_YN", length = 1, comment = "부점한정사용여부")   private String  bbrLmtnUseYn;
    @Column(name = "BBR_LMTN_C",      length = 8, comment = "부점한정코드")   private String  bbrLmtnC;
    @Column(name = "SRE_SQN_NO", comment = "화면순서번호")                    private Integer sreSqnNo;
    @Column(name = "USE_YN",          length = 1, comment = "사용여부")   private String  useYn;
    @Column(name = "RMK",             length = 500, comment = "비고") private String  rmk;
}
