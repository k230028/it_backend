package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BpqnamL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 사전질의응답 엔티티
 *
 * <p>DB 테이블: {@code TAAABB_BPQNAM}</p>
 *
 * <p>협의회 개최 전 평가위원이 사전 질의를 등록하고,
 * 추진부서 담당자(ITPZZ001)가 답변합니다.
 * REP_YN='N'인 항목이 미답변 상태입니다.</p>
 *
 * <p>QTN_ID 형식: QTN-{협의회ID}-{순번} (예: QTN-ASCT-2026-0001-01)</p>
 */
@LogTarget(entity = BpqnamL.class)
@Entity
@Table(name = "TAAABB_BPQNAM", comment = "사전질의응답")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bpqnam extends BaseEntity {

    /** 질의응답ID: PK (QTN-{협의회ID}-{순번} 형식) */
    @Id
    @Column(name = "QTN_ID", length = 32, nullable = false, comment = "질의응답ID")
    private String qtnId;

    /** 협의회ID: BASCTM.ASCT_ID FK */
    @Column(name = "ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String asctId;

    /** 질의자사번: 질의를 등록한 평가위원 사번 (CUSERI.ENO FK) */
    @Column(name = "QTN_ENO", length = 32, comment = "질의자사번")
    private String qtnEno;

    /** 질의내용: 평가위원이 입력한 사전 질의 (최대 4000자) */
    @Column(name = "QTN_CONE", length = 4000, comment = "질의내용")
    private String qtnCone;

    /** 답변자사번: 답변을 작성한 담당자 사번 (CUSERI.ENO FK) */
    @Column(name = "REP_ENO", length = 32, comment = "답변자사번")
    private String repEno;

    /** 답변내용: 추진부서 담당자가 작성한 답변 (최대 4000자) */
    @Column(name = "REP_CONE", length = 4000, comment = "답변내용")
    private String repCone;

    /** 답변여부: N(미답변) / Y(답변완료), 기본값 N */
    @Column(name = "REP_YN", length = 1, comment = "답변여부")
    private String repYn;

    /**
     * 답변 등록 (추진부서 담당자가 호출)
     *
     * @param repEno  답변자 사번
     * @param repCone 답변내용
     */
    public void reply(String repEno, String repCone) {
        this.repEno = repEno;
        this.repCone = repCone;
        this.repYn = "Y";
    }

    /**
     * 질의 내용 수정 (질의 등록자가 호출)
     *
     * @param qtnCone 수정할 질의내용
     */
    public void updateQuestion(String qtnCone) {
        this.qtnCone = qtnCone;
    }
}

