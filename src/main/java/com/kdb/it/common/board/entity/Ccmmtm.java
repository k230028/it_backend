package com.kdb.it.common.board.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.CcmmtmL;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 게시판 댓글 엔티티 — TAAABB_CCMMTM
 *
 * <p>댓글 트리는 게시물과 동일한 GRP_NO / GRP_SQN / GRP_LEV 패턴을 사용한다.
 * 변경 시 {@link CcmmtmL}에 이력 자동 적재.</p>
 */
@LogTarget(entity = CcmmtmL.class)
@Entity
@Table(name = "TAAABB_CCMMTM")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Ccmmtm extends BaseEntity {

    /** 댓글관리번호 PK. 형식: CMMT-{YYYY}-{0001} */
    @Id
    @Column(name = "CMMT_MNG_NO", nullable = false, length = 32)
    private String cmmtMngNo;

    @Column(name = "NAC_MNG_NO", nullable = false, length = 32)
    private String nacMngNo;

    /** 댓글 본문 — HtmlSanitizer.sanitize() 적용 의무 */
    @Lob
    @Column(name = "CMMT_CONE", nullable = false)
    private String cmmtCone;

    @Column(name = "SRE_YN", nullable = false, length = 1)
    private String sreYn;

    /** 댓글 그룹번호 — 최상위 댓글의 CMMT_MNG_NO */
    @Column(name = "CMMT_GRP_NO", nullable = false, length = 32)
    private String cmmtGrpNo;

    @Column(name = "CMMT_GRP_SQN", nullable = false)
    private Integer cmmtGrpSqn;

    /** 댓글 트리 깊이 (0=원댓글, 1=대댓글…) */
    @Column(name = "CMMT_GRP_LEV", nullable = false)
    private Integer cmmtGrpLev;

    @Column(name = "HRK_CMMT_MNG_NO", length = 32)
    private String hrkCmmtMngNo;

    /** 댓글 본문 수정 — sanitize 완료 값을 전달해야 한다 */
    public void updateContent(String sanitizedCone) {
        this.cmmtCone = sanitizedCone;
    }

    /** 그룹 정보 설정 — 최상위 댓글 등록 시 */
    public void initGroupAsRoot() {
        this.cmmtGrpNo  = this.cmmtMngNo;
        this.cmmtGrpSqn = 0;
        this.cmmtGrpLev = 0;
    }

    /**
     * 그룹 정보 설정 — 대댓글 등록 시
     *
     * @param parentGrpNo  부모의 CMMT_GRP_NO
     * @param parentGrpSqn 부모의 CMMT_GRP_SQN
     * @param parentGrpLev 부모의 CMMT_GRP_LEV
     * @param parentPk     부모의 CMMT_MNG_NO
     */
    public void initGroupAsReply(String parentGrpNo, int parentGrpSqn, int parentGrpLev, String parentPk) {
        this.cmmtGrpNo    = parentGrpNo;
        this.cmmtGrpSqn   = parentGrpSqn + 1;
        this.cmmtGrpLev   = parentGrpLev + 1;
        this.hrkCmmtMngNo = parentPk;
    }
}
