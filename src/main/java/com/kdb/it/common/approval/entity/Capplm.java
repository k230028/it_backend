package com.kdb.it.common.approval.entity;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.CapplmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 신청서 마스터 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CAPPLM}
 *
 * <p>결재 신청서의 헤더 정보를 관리합니다. 신청서와 원본 데이터 연결({@link Cappla}), 결재선({@link Cdecim})과 연관됩니다.
 *
 * <p>신청서 상태({@code IT_PTL_APF_PRG_STS_C}) 흐름:
 *
 * <pre>
 *   "결재중" → (모든 결재자 승인 시) → "결재완료"
 *           → (중간 반려 시)       → "반려"
 * </pre>
 *
 * <p>관리번호 형식: {@code APF-{연도}-{8자리 시퀀스}} (예: {@code APF-2026-00000001})
 */
@LogTarget(entity = CapplmL.class)
@Entity
@Table(name = "TPRMPP_CAPPLM", comment = "신청서 마스터")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Capplm extends BaseEntity {

    /** 신청서식별번호: 기본키 형식: {@code APF-{연도}-{8자리 시퀀스}} (예: {@code APF-2026-00000001}) */
    @Id
    @Column(name = "APF_DCM_NO", length = 64, nullable = false, comment = "신청서식별번호")
    private String apfMngNo;

    /** 신청서진행상태코드: Ccodem APF_STS 참조 (1:결재중, 2:결재완료, 3:반려, 4:회수) */
    @Column(name = "IT_PTL_APF_PRG_STS_C", length = 2, nullable = false, comment = "IT포탈신청서진행상태코드")
    private String itPtlApfPrgStsC;

    /** 결재요청제목: 신청서의 제목 (최대 255자) */
    @Column(name = "DCD_REQ_TTL", length = 255, comment = "결재요청제목")
    private String dcdReqTtl;

    /** 결재요청정보: 신청서 상세 내용 (LOB 타입, 대용량 텍스트) */
    @Lob
    @Column(name = "DCD_REQ_INF", comment = "결재요청정보")
    private String dcdReqInf;

    /** 결재요청사용자ID: 신청서를 작성한 직원의 사번 (최대 14자) */
    @Column(name = "DCD_REQ_USID", length = 14, comment = "결재요청사용자ID")
    private String dcdReqUsid;

    /**
     * 결재요청일시: 신청서를 제출한 날짜. (물리 컬럼 DCD_REQ_DTM은 DATE라 시·분·초까지 저장되나, Java 타입이 LocalDate라 시각 정보는 손실됨)
     */
    @Column(name = "DCD_REQ_DTM", comment = "결재요청일시")
    private LocalDate dcdReqDtm;

    /** 등록자결재요청내용: 신청자가 작성한 의견 또는 요청 사항 (최대 300자) */
    @Column(name = "RGPR_DCD_REQ_CONE", length = 300, comment = "등록자결재요청내용")
    private String rgprDcdReqCone;

    /** 결재요청부점코드: 신청서 요청 부점 코드 (최대 3자) */
    @Column(name = "DCD_REQ_BBR_C", length = 3, comment = "결재요청부점코드")
    private String dcdReqBbrC;

    /**
     * 신청서 상태 변경 메서드
     *
     * <p>결재 처리 후 신청서의 상태를 업데이트합니다. (예: "결재중" → "결재완료" 또는 "반려")
     *
     * @param status 변경할 상태
     */
    public void updateStatus(ApprovalStatus status) {
        this.itPtlApfPrgStsC = status.code();
    }

    /**
     * 신청서 세부내용 업데이트 메서드
     *
     * <p>결재 처리 후 신청서 결재요청정보(JSON) 내의 결재 정보를 갱신합니다.
     *
     * @param detailContent 업데이트할 결재요청정보 JSON 문자열
     */
    public void updateDetailContent(String detailContent) {
        this.dcdReqInf = detailContent;
    }
}
