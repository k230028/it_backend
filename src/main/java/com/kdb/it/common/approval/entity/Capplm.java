package com.kdb.it.common.approval.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.CapplmL;
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

import java.time.LocalDate;

/**
 * 신청서 마스터 엔티티
 *
 * <p>
 * DB 테이블: {@code TAAABB_CAPPLM}
 * </p>
 *
 * <p>
 * 결재 신청서의 헤더 정보를 관리합니다.
 * 신청서와 원본 데이터 연결({@link Cappla}),
 * 결재선({@link Cdecim})과 연관됩니다.
 * </p>
 *
 * <p>
 * 신청서 상태({@code APF_STS}) 흐름:
 * </p>
 *
 * <pre>
 *   "결재중" → (모든 결재자 승인 시) → "결재완료"
 *           → (중간 반려 시)       → "반려"
 * </pre>
 *
 * <p>
 * 관리번호 형식: {@code APF_{연도}{8자리 시퀀스}} (예: {@code APF_202600000001})
 * </p>
 */
@LogTarget(entity = CapplmL.class)
@Entity // JPA 엔티티로 등록
@Table(name = "TAAABB_CAPPLM", comment = "신청서 마스터") // 매핑할 DB 테이블명
@Getter // 모든 필드의 getter 자동 생성 (Lombok)
@SuperBuilder // 상속 구조에서 Builder 패턴 지원
@NoArgsConstructor(access = AccessLevel.PROTECTED) // protected 기본 생성자 (JPA 요구사항)
@AllArgsConstructor // 전체 필드 생성자 자동 생성
public class Capplm extends BaseEntity {

    /**
     * 신청서관리번호: 기본키
     * 형식: {@code APF_{연도}{8자리 시퀀스}} (예: {@code APF_202600000001})
     */
    @Id
    @Column(name = "APF_MNG_NO", length = 32, nullable = false, comment = "신청서관리번호")
    private String apfMngNo;

    /**
     * 신청서상태: 결재 진행 상태
     * 값: "결재중"(기본), "결재완료"(최종 승인), "반려"(중간 반려)
     */
    @Column(name = "APF_STS", length = 32, comment = "신청서상태")
    private String apfSts;

    /** 신청서명: 신청서의 제목 (최대 800자) */
    @Column(name = "APF_NM", length = 800, comment = "신청서명")
    private String apfNm;

    /**
     * 신청서세부내용: 신청서 상세 내용 (LOB 타입, 대용량 텍스트)
     * 결재선 정보가 JSON 형태로 포함될 수 있음
     */
    @jakarta.persistence.Lob
    @Column(name = "APF_DTL_CONE", comment = "신청서세부내용")
    private String apfDtlCone;

    /** 신청 사원번호: 신청서를 작성한 직원의 사번 */
    @Column(name = "RQS_ENO", length = 32, comment = "신청 사원번호")
    private String rqsEno;

    /** 신청일자: 신청서를 제출한 날짜 */
    @Column(name = "RQS_DT", comment = "신청일자")
    private LocalDate rqsDt;

    /** 신청의견: 신청자가 작성한 의견 또는 요청 사항 (최대 1000자) */
    @Column(name = "RQS_OPNN", length = 1000, comment = "신청의견")
    private String rqsOpnn;

    /**
     * 신청서 상태 변경 메서드
     *
     * <p>
     * 결재 처리 후 신청서의 상태를 업데이트합니다.
     * (예: "결재중" → "결재완료" 또는 "반려")
     * </p>
     *
     * @param status 변경할 상태 값 ("결재완료" | "반려")
     */
    public void updateStatus(String status) {
        this.apfSts = status;
    }

    /**
     * 신청서 세부내용 업데이트 메서드
     *
     * <p>
     * 결재 처리 후 신청서 세부내용(JSON) 내의 결재 정보를 갱신합니다.
     * 결재일자 등의 정보가 JSON 내 approvalLine 항목에 반영됩니다.
     * </p>
     *
     * @param detailContent 업데이트할 신청서 세부내용 JSON 문자열
     */
    public void updateDetailContent(String detailContent) {
        this.apfDtlCone = detailContent;
    }
}

