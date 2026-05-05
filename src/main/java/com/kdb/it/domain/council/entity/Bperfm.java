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

import java.time.LocalDate;

/**
 * 성과관리 자체계획(성과지표) 엔티티
 *
 * <p>DB 테이블: {@code TAAABB_BPERFM}</p>
 *
 * <p>협의회 1건당 1개 이상의 성과지표를 등록하며, 담당자가 동적으로 추가/삭제할 수 있습니다.
 * DTP_SNO는 클라이언트 측에서 순번을 관리합니다 (1부터 시작).</p>
 *
 * <p>복합키: ({@code ASCT_ID}, {@code DTP_SNO})</p>
 */
@LogTarget(entity = BperfmL.class)
@Entity
@Table(name = "TAAABB_BPERFM", comment = "성과관리 자체계획(성과지표)")
@IdClass(BperfmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bperfm extends BaseEntity {

    /** 협의회ID: 복합키 첫 번째 컬럼 */
    @Id
    @Column(name = "ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String asctId;

    /** 지표순번: 복합키 두 번째 컬럼 (1부터 시작, 클라이언트 관리) */
    @Id
    @Column(name = "DTP_SNO", nullable = false, comment = "지표순번")
    private Integer dtpSno;

    /** 성과지표명: 지표를 식별하는 명칭 (최대 200자) */
    @Column(name = "DTP_NM", length = 200, comment = "성과지표명")
    private String dtpNm;

    /** 성과지표정의: 지표의 개념과 범위를 설명 (최대 1000자) */
    @Column(name = "DTP_CONE", length = 1000, comment = "성과지표정의")
    private String dtpCone;

    /** 측정방법: 지표 측정 방법론 설명 (최대 1000자) */
    @Column(name = "MSM_MANR", length = 1000, comment = "측정방법")
    private String msmManr;

    /** 산식: 지표 계산 공식 (최대 1000자) */
    @Column(name = "CLF", length = 1000, comment = "산식")
    private String clf;

    /** 목표치: 달성 목표값 (예: 95% 이상, 최대 200자) */
    @Column(name = "GL_NV", length = 200, comment = "목표치")
    private String glNv;

    /** 측정시작일: 지표 측정 시작 날짜 */
    @Column(name = "MSM_STT_DT", comment = "측정시작일")
    private LocalDate msmSttDt;

    /** 측정종료일: 지표 측정 종료 날짜 */
    @Column(name = "MSM_END_DT", comment = "측정종료일")
    private LocalDate msmEndDt;

    /** 측정시점: 측정 시점 설명 (예: 시스템 오픈 후, 최대 100자) */
    @Column(name = "MSM_TPM", length = 100, comment = "측정시점")
    private String msmTpm;

    /** 측정주기: 측정 반복 주기 (예: 매년말, 반기별, 최대 100자) */
    @Column(name = "MSM_CLE", length = 100, comment = "측정주기")
    private String msmCle;

    /**
     * 성과지표 정보 업데이트
     *
     * @param dtpNm    성과지표명
     * @param dtpCone  성과지표정의
     * @param msmManr  측정방법
     * @param clf      산식
     * @param glNv     목표치
     * @param msmSttDt 측정시작일
     * @param msmEndDt 측정종료일
     * @param msmTpm   측정시점
     * @param msmCle   측정주기
     */
    public void update(String dtpNm, String dtpCone, String msmManr, String clf, String glNv,
                       LocalDate msmSttDt, LocalDate msmEndDt, String msmTpm, String msmCle) {
        this.dtpNm = dtpNm;
        this.dtpCone = dtpCone;
        this.msmManr = msmManr;
        this.clf = clf;
        this.glNv = glNv;
        this.msmSttDt = msmSttDt;
        this.msmEndDt = msmEndDt;
        this.msmTpm = msmTpm;
        this.msmCle = msmCle;
    }
}

