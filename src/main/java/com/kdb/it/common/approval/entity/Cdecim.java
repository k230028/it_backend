package com.kdb.it.common.approval.entity;

import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 결재 정보 관리 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CDECIM}</p>
 *
 * <p>신청서({@link Capplm})에 대한 결재선(결재자 목록)과
 * 각 결재자의 결재 처리 정보를 관리합니다.</p>
 *
 * <p>복합키 구조: ({@code APF_DCM_NO}, {@code DCR_SQN_SNO})</p>
 *
 * <p>결재 처리 흐름:</p>
 * <pre>
 *   신청서 생성 시: DCD_STS_C='1'(미결재), 나머지 null
 *   결재 처리 후:   DCD_STS_C='2'(승인) 또는 '3'(반려)
 * </pre>
 *
 * <p>순차 결재: {@code DCR_SQN_SNO} 순서대로 결재가 진행됩니다.
 * 이전 결재자가 승인해야 다음 결재자가 결재할 수 있습니다.</p>
 */
@Entity
@Table(name = "TPRMPP_CDECIM", comment = "결재 정보 관리")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@IdClass(CdecimId.class)
public class Cdecim extends BaseEntity {

    /**
     * 신청서식별번호: 복합 기본키의 첫 번째 컬럼
     * 연결된 신청서의 식별번호(APF_DCM_NO)와 동일한 값 (예: APF-2026-00000001)
     */
    @Id
    @Column(name = "APF_DCM_NO", length = 64, nullable = false, comment = "신청서식별번호")
    private String dcdMngNo;

    /**
     * 결재자순서일련번호: 복합 기본키의 두 번째 컬럼
     * 결재 진행 순서 (1부터 시작, 순번이 낮을수록 먼저 결재)
     */
    @Id
    @Column(name = "DCR_SQN_SNO", nullable = false, comment = "결재자순서일련번호")
    private Integer dcrSqnSno;

    /** 결재자사원번호: 이 순서에서 결재를 담당하는 직원의 사번 (최대 32자) */
    @Column(name = "DCR_ENO", length = 32, comment = "결재자사원번호")
    private String dcrEno;

    /** 결재유형코드: Ccodem DCD_TP_C 참조 (미결재 시 null) */
    @Column(name = "DCD_TP_C", length = 2, comment = "결재유형코드")
    private String dcdTpC;

    /**
     * 결재일시: 실제 결재(승인/반려)가 이루어진 일자 (미결재 시 null).
     * (물리 컬럼 DCD_DTM은 DATE라 시·분·초까지 저장되나, Java 타입이 LocalDate라 시각 정보는 손실됨)
     */
    @Column(name = "DCD_DTM", comment = "결재일시")
    private LocalDate dcdDtm;

    /** 결재자의견내용: 결재자가 작성한 의견 또는 코멘트 (최대 2000자) */
    @Column(name = "DCR_OPNN_CONE", length = 2000, comment = "결재자의견내용")
    private String dcrOpnnCone;

    /** 결재상태코드: Ccodem DCD_STS_C 참조 (1:미결재, 2:승인, 3:반려, 4:회수무효). */
    @Column(name = "DCD_STS_C", length = 1, nullable = false, comment = "결재상태코드")
    private String dcdStsC;

    /**
     * 최종결재여부: 이 결재자가 결재선의 마지막 결재자인지 여부
     * 'Y' = 최종 결재자 (이 결재자 승인 시 신청서가 "결재완료"로 변경)
     * 'N' = 중간 결재자
     */
    @Column(name = "LST_DCD_YN", length = 1, comment = "최종결재여부")
    private String lstDcdYn;

    /**
     * 결재 처리.
     *
     * <p>결재자가 승인 또는 반려 처리할 때 호출됩니다.
     * 결재 상태 코드, 일자, 의견을 업데이트합니다.</p>
     *
     * <p>JPA Dirty Checking에 의해 트랜잭션 종료 시 자동으로 DB에 반영됩니다.</p>
     *
     * @param opinion 결재 의견
     * @param status  결재 상태 ({@link DecisionStatus#APPROVED} 또는 {@link DecisionStatus#REJECTED})
     */
    public void approve(String opinion, DecisionStatus status) {
        this.dcdStsC    = status.code();
        this.dcdDtm     = LocalDate.now();
        this.dcrOpnnCone = opinion;
    }

    /** 회수로 인한 미결재 항목 무효화 */
    public void invalidateByRecall() {
        this.dcdStsC = DecisionStatus.INVALIDATED.code();
    }
}
