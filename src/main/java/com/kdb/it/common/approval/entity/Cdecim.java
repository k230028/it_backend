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
 * <p>복합키 구조: ({@code DCD_MNG_NO}, {@code DCD_SQN})</p>
 *
 * <p>결재 처리 흐름:</p>
 * <pre>
 *   신청서 생성 시: DCD_TP=null, DCD_STS=null (미결재)
 *   결재 처리 후:   DCD_TP="결재", DCD_STS="승인" 또는 "반려"
 * </pre>
 *
 * <p>순차 결재: {@code DCD_SQN} 순서대로 결재가 진행됩니다.
 * 이전 결재자가 승인해야 다음 결재자가 결재할 수 있습니다.</p>
 */
@Entity                                              // JPA 엔티티로 등록
@Table(name = "TPRMPP_CDECIM", comment = "결재 정보 관리")                       // 매핑할 DB 테이블명
@Getter                                              // 모든 필드의 getter 자동 생성 (Lombok)
@SuperBuilder                                        // 상속 구조에서 Builder 패턴 지원
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // protected 기본 생성자 (JPA 요구사항)
@AllArgsConstructor                                  // 전체 필드 생성자 자동 생성
@IdClass(CdecimId.class)                             // 복합키 클래스 지정
public class Cdecim extends BaseEntity {

    /**
     * 결재관리번호: 복합 기본키의 첫 번째 컬럼
     * 신청서 관리번호(APF_MNG_NO)와 동일한 값 (예: APF_202600000001)
     */
    @Id
    @Column(name = "DCD_MNG_NO", length = 32, nullable = false, comment = "결재관리번호")
    private String dcdMngNo;

    /**
     * 결재순서: 복합 기본키의 두 번째 컬럼
     * 결재 진행 순서 (1부터 시작, 순번이 낮을수록 먼저 결재)
     */
    @Id
    @Column(name = "DCD_SQN", nullable = false, comment = "결재순서")
    private Integer dcdSqn;

    /** 결재직원번호: 이 순서에서 결재를 담당하는 직원의 사번 */
    @Column(name = "DCD_ENO", length = 10, comment = "결재직원번호")
    private String dcdEno;

    /** 결재일자: 실제 결재(승인/반려)가 이루어진 날짜 (미결재 시 null) */
    @Column(name = "DCD_DT", comment = "결재일자")
    private LocalDate dcdDt;

    /** 결재의견: 결재자가 작성한 의견 또는 코멘트 (최대 1000자) */
    @Column(name = "DCD_OPNN", length = 1000, comment = "결재의견")
    private String dcdOpnn;

    /** 결재선상태코드: Ccodem DCD_STS 참조 (001:미결재, 002:승인, 003:반려, 004:회수무효) */
    @Column(name = "DCD_STS_C", length = 3, nullable = false, comment = "결재선상태코드")
    private String dcdStsC;

    /**
     * 최종결재자여부: 이 결재자가 결재선의 마지막 결재자인지 여부
     * 'Y' = 최종 결재자 (이 결재자 승인 시 신청서가 "결재완료"로 변경)
     * 'N' = 중간 결재자
     */
    @Column(name = "LST_DCD_YN", length = 1, comment = "최종결재자여부")
    private String lstDcdYn;

    /**
     * 결재 처리 (코드 기반 API).
     *
     * <p>결재자가 승인 또는 반려 처리할 때 호출됩니다.
     * 결재 상태 코드, 일자, 의견을 업데이트합니다.</p>
     *
     * <p>JPA Dirty Checking에 의해 트랜잭션 종료 시 자동으로 DB에 반영됩니다.</p>
     *
     * @param opinion 결재 의견 (결재자 코멘트)
     * @param status  결재 상태 ({@link DecisionStatus#APPROVED} 또는 {@link DecisionStatus#REJECTED})
     */
    public void approve(String opinion, DecisionStatus status) {
        this.dcdStsC = status.code();
        this.dcdDt   = LocalDate.now();
        this.dcdOpnn = opinion;
    }

    /** 회수로 인한 미결재 항목 무효화 */
    public void invalidateByRecall() {
        this.dcdStsC = DecisionStatus.INVALIDATED.code();
    }
}

