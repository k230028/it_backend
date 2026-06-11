package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BperfmL;
import com.kdb.it.domain.entity.BaseEntity;
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
 * 성과관리 자체계획(성과지표) 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_BPERFM}</p>
 *
 * <p>협의회 1건당 1개 이상의 성과지표를 등록하며, 담당자가 동적으로 추가/삭제할 수 있습니다.
 * DTP_SNO는 클라이언트 측에서 순번을 관리합니다 (1부터 시작).</p>
 *
 * <p>복합키: ({@code ASCT_ID}, {@code DTP_SNO})</p>
 */
@LogTarget(entity = BperfmL.class)
@Entity
@Table(name = "TPRMPP_BPERFM", comment = "성과관리 자체계획(성과지표)")
@IdClass(BperfmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bperfm extends BaseEntity {

    /** 협의회ID: 복합키 첫 번째 컬럼 */
    @Id
    @Column(name = "IT_PTL_ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String itPtlAsctId;

    /** 지표순번: 복합키 두 번째 컬럼 (1부터 시작, 클라이언트 관리) */
    @Id
    @Column(name = "EVL_DTP_SNO", nullable = false, comment = "지표순번")
    private Integer evlDtpSno;

    /** 평가지표명: 지표를 식별하는 명칭 (최대 100자) */
    @Column(name = "EVL_DTP_NM", length = 100, comment = "평가지표명")
    private String evlDtpNm;

    /** 평가지표정의내용: 지표의 개념과 범위를 설명 (최대 4000자) */
    @Column(name = "EVL_DTP_DFNT_CONE", length = 4000, comment = "평가지표정의내용")
    private String evlDtpDfntCone;

    /** 평가지표계산식내용: 지표 계산 공식 (최대 4000자) */
    @Column(name = "EVL_DTP_CLF_CONE", length = 4000, comment = "평가지표계산식내용")
    private String evlDtpClfCone;

    /** 평가지표측정시점내용: 측정 시점 설명 (예: 시스템 오픈 후, 최대 300자) */
    @Column(name = "EVL_DTP_MSM_PTM_CONE", length = 300, comment = "평가지표측정시점내용")
    private String evlDtpMsmPtmCone;

    /** 평가지표측정주기내용: 측정 반복 주기 (예: 매년말, 반기별, 최대 300자) */
    @Column(name = "EVL_DTP_MSM_CLE_CONE", length = 300, comment = "평가지표측정주기내용")
    private String evlDtpMsmCleCone;

    /**
     * 성과지표 정보 업데이트
     *
     * @param evlDtpNm         평가지표명
     * @param evlDtpDfntCone   평가지표정의내용
     * @param evlDtpClfCone    평가지표계산식내용
     * @param evlDtpMsmPtmCone 평가지표측정시점내용
     * @param evlDtpMsmCleCone 평가지표측정주기내용
     */
    public void update(String evlDtpNm, String evlDtpDfntCone, String evlDtpClfCone,
                       String evlDtpMsmPtmCone, String evlDtpMsmCleCone) { // evlDtpSno PK은 별도
        this.evlDtpNm = evlDtpNm;
        this.evlDtpDfntCone = evlDtpDfntCone;
        this.evlDtpClfCone = evlDtpClfCone;
        this.evlDtpMsmPtmCone = evlDtpMsmPtmCone;
        this.evlDtpMsmCleCone = evlDtpMsmCleCone;
    }
}

