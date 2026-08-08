package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BmqnamL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 본회의 질의응답 엔티티 (PRD §26)
 *
 * <p>DB 테이블: {@code TPRMPP_BMQNAM}
 *
 * <p>협의회 본회의 동안 오간 질의를 IT관리자(ITPAD001)가 정리해 등록하고, 같은 IT관리자가 답변까지 함께 정리합니다. 평가위원은 등록된 본회의 질의응답을 참고하여
 * 평가의견(1~5점)을 작성합니다.
 *
 * <p>컬럼 구조는 사전질의응답({@link Bpqnam})과 동일하게 유지하되, 권한 정책과 라이프사이클(IN_PROGRESS 이후 정리)만 다릅니다.
 *
 * <p>QTN_ID 형식: {@code MQT-{협의회ID}-{2자리순번}} (예: MQT-ASCT-2026-0001-01)
 */
@LogTarget(entity = BmqnamL.class)
@Entity
@Table(name = "TPRMPP_BMQNAM", comment = "본회의질의응답")
@IdClass(BmqnamId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bmqnam extends BaseEntity {

    /** 질의응답ID: 협의회ID와 함께 복합 PK를 구성합니다. (MQT-{협의회ID}-{순번} 형식) */
    @Id
    @Column(name = "QTN_ID", length = 36, nullable = false, comment = "질의응답ID")
    private String qtnId;

    /** 협의회ID: 질의응답ID와 함께 복합 PK를 구성합니다. BASCTM.ASCT_ID FK */
    @Id
    @Column(name = "IT_PTL_ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String itPtlAsctId;

    /** 질의자사번: 질의를 정리해 등록한 IT관리자 사번 (CUSERI.ENO FK) */
    @Column(name = "QTN_DWU_USID", length = 14, comment = "질의자사번")
    private String qtnDwuUsid;

    /** 질의내용: 본회의에서 나온 질의를 IT관리자가 정리한 내용 (최대 4000자) */
    @Column(name = "QTN_CONE", length = 4000, comment = "질의내용")
    private String qtnCone;

    /** 답변자사번: 답변을 정리해 등록한 IT관리자 사번 (CUSERI.ENO FK) */
    @Column(name = "REP_DWU_USID", length = 14, comment = "답변자사번")
    private String repDwuUsid;

    /** 답변내용: IT관리자가 정리한 답변 내용 (최대 2000자) */
    @Column(name = "REP_CONE", length = 2000, comment = "답변내용")
    private String repCone;

    /** 답변여부: N(미답변) / Y(답변완료), 기본값 N */
    @Column(name = "QTN_RPD_RLT_YN", length = 1, comment = "답변여부")
    private String qtnRpdRltYn;

    /**
     * 답변 등록/수정 (IT관리자가 호출)
     *
     * @param repDwuUsid 답변자 사번
     * @param repCone 답변내용
     */
    public void reply(String repDwuUsid, String repCone) {
        this.repDwuUsid = repDwuUsid;
        this.repCone = repCone;
        this.qtnRpdRltYn = "Y";
    }

    /**
     * 질의 내용 수정 (질의 등록자 또는 IT관리자)
     *
     * @param qtnCone 수정할 질의내용
     */
    public void updateQuestion(String qtnCone) {
        this.qtnCone = qtnCone;
    }
}
