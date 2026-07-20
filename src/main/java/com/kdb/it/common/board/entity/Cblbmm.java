package com.kdb.it.common.board.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.CblbmmL;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 게시판 메타 엔티티 — TPRMPP_CBLBMM
 *
 * <p>게시판 단위 정책(답변·댓글·첨부필수·권한 등)을 관리한다.
 * 변경 시 {@link CblbmmL}에 이력이 자동 적재된다.</p>
 */
@LogTarget(entity = CblbmmL.class)
@Entity
@Table(name = "TPRMPP_CBLBMM", comment = "게시판")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Cblbmm extends BaseEntity {

    /** 게시판관리번호 PK. 형식: BLBM-{0001} */
    @Id
    @Column(name = "BLB_ID", nullable = false, length = 10, comment = "게시판관리번호 (물리컬럼 BLB_ID=게시판ID)")
    private String blbMngNo;

    @Column(name = "BLB_NM", nullable = false, length = 300, comment = "게시판명")
    private String blbNm;

    /** 게시판구분코드 (공통코드 IT_PTL_BLB_TC: 001=공지사항, 002=자료실) */
    @Column(name = "IT_PTL_BLB_TC", nullable = false, length = 3, comment = "IT포탈게시판구분코드")
    private String itPtlBlbTc;

    @Column(name = "REP_FNC_USE_YN", nullable = false, length = 1, comment = "답변사용여부 (물리컬럼 REP_FNC_USE_YN=답변기능사용여부)")
    private String repUseYn;

    @Column(name = "CMMT_USE_YN", nullable = false, length = 1, comment = "댓글사용여부")
    private String cmmtUseYn;

    @Column(name = "APG_FL_USE_YN", nullable = false, length = 1, comment = "첨부필수여부 (물리컬럼 APG_FL_USE_YN=첨부파일사용여부)")
    private String flEsnYn;

    @Column(name = "HED_TAG_USE_YN", nullable = false, length = 1, comment = "머리말태그사용여부")
    private String hedTagUseYn;

    @Column(name = "SRE_SQN_SNO", nullable = false, comment = "화면순서번호 (물리컬럼 SRE_SQN_SNO=화면순서일련번호)")
    private Integer sreSqnNo;

    @Column(name = "USE_YN", nullable = false, length = 1, comment = "사용여부")
    private String useYn;

    @Column(name = "RMK", length = 300, comment = "비고")
    private String rmk;

    /**
     * 게시판 메타 수정 커맨드
     *
     * @param blbNm         게시판명
     * @param repUseYn      답변사용여부
     * @param cmmtUseYn     댓글사용여부
     * @param flEsnYn       첨부필수여부
     * @param hedTagUseYn   머리말태그사용여부
     * @param sreSqnNo      화면순서번호
     * @param useYn         사용여부
     * @param rmk           비고
     */
    public record UpdateCommand(
        String blbNm, String repUseYn, String cmmtUseYn,
        String flEsnYn, String hedTagUseYn,
        Integer sreSqnNo, String useYn, String rmk
    ) {}

    /** 게시판 메타 정보 수정 — JPA Dirty Checking 활용 */
    public void update(UpdateCommand cmd) {
        this.blbNm        = cmd.blbNm();
        this.repUseYn     = cmd.repUseYn();
        this.cmmtUseYn    = cmd.cmmtUseYn();
        this.flEsnYn      = cmd.flEsnYn();
        this.hedTagUseYn  = cmd.hedTagUseYn();
        this.sreSqnNo     = cmd.sreSqnNo();
        this.useYn        = cmd.useYn();
        this.rmk          = cmd.rmk();
    }
}
