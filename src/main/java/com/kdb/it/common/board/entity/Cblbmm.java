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

    /** 게시판관리번호 PK. 형식: BLBM-{YYYY}-{0001} */
    @Id
    @Column(name = "BLB_MNG_NO", nullable = false, length = 32, comment = "게시판관리번호")
    private String blbMngNo;

    @Column(name = "BLB_NM", nullable = false, length = 100, comment = "게시판명")
    private String blbNm;

    @Column(name = "BLB_TP", nullable = false, length = 32, comment = "게시판유형")
    private String blbTp;

    @Column(name = "REP_USE_YN", nullable = false, length = 1, comment = "답변사용여부")
    private String repUseYn;

    @Column(name = "CMMT_USE_YN", nullable = false, length = 1, comment = "댓글사용여부")
    private String cmmtUseYn;

    @Column(name = "FL_ESN_YN", nullable = false, length = 1, comment = "파일필수여부")
    private String flEsnYn;

    @Column(name = "HRK_FXN_USE_YN", nullable = false, length = 1, comment = "상위고정사용여부")
    private String hrkFxnUseYn;

    @Column(name = "NAC_TP_USE_YN", nullable = false, length = 1, comment = "게시물유형사용여부")
    private String nacTpUseYn;

    @Column(name = "KD_USE_YN", nullable = false, length = 1, comment = "종류사용여부")
    private String kdUseYn;

    /** 조회권한코드 (ALL / ROLE_ADMIN / ROLE_USER / ROLE_DEPT_MANAGER) */
    @Column(name = "INQ_ATH_C", nullable = false, length = 32, comment = "조회권한코드")
    private String inqAthC;

    /** 등록권한코드 */
    @Column(name = "ENR_ATH_C", nullable = false, length = 32, comment = "등록권한코드")
    private String enrAthC;

    @Column(name = "BBR_LMTN_USE_YN", nullable = false, length = 1, comment = "부점한정사용여부")
    private String bbrLmtnUseYn;

    /** 담당부서한정코드 — Y일 때 해당 부서만 접근 가능 */
    @Column(name = "BBR_LMTN_C", length = 8, comment = "부점한정코드")
    private String bbrLmtnC;

    @Column(name = "SRE_SQN_NO", nullable = false, comment = "화면순서번호")
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
     * @param hrkFxnUseYn   상위고정사용여부
     * @param nacTpUseYn    게시물유형사용여부
     * @param kdUseYn       종류사용여부
     * @param inqAthC       조회권한코드
     * @param enrAthC       등록권한코드
     * @param bbrLmtnUseYn  담당부서한정사용여부
     * @param bbrLmtnC      담당부서한정코드
     * @param sreSqnNo      화면순서번호
     * @param useYn         사용여부
     * @param rmk           비고
     */
    public record UpdateCommand(
        String blbNm, String repUseYn, String cmmtUseYn,
        String flEsnYn, String hrkFxnUseYn, String nacTpUseYn, String kdUseYn,
        String inqAthC, String enrAthC,
        String bbrLmtnUseYn, String bbrLmtnC,
        Integer sreSqnNo, String useYn, String rmk
    ) {}

    /** 게시판 메타 정보 수정 — JPA Dirty Checking 활용 */
    public void update(UpdateCommand cmd) {
        this.blbNm        = cmd.blbNm();
        this.repUseYn     = cmd.repUseYn();
        this.cmmtUseYn    = cmd.cmmtUseYn();
        this.flEsnYn      = cmd.flEsnYn();
        this.hrkFxnUseYn  = cmd.hrkFxnUseYn();
        this.nacTpUseYn   = cmd.nacTpUseYn();
        this.kdUseYn      = cmd.kdUseYn();
        this.inqAthC      = cmd.inqAthC();
        this.enrAthC      = cmd.enrAthC();
        this.bbrLmtnUseYn = cmd.bbrLmtnUseYn();
        this.bbrLmtnC     = cmd.bbrLmtnC();
        this.sreSqnNo     = cmd.sreSqnNo();
        this.useYn        = cmd.useYn();
        this.rmk          = cmd.rmk();
    }
}
