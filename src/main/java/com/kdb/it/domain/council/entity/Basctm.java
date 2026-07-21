package com.kdb.it.domain.council.entity;

import com.kdb.it.common.util.Yyyymmdd8DateConverter;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BasctmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * 정보화실무협의회 기본정보 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_BASCTM}</p>
 *
 * <p>협의회 전체 프로세스의 루트 엔티티입니다.
 * 타당성검토표(Bpovwm), 결과서(Brsltm)가 이 엔티티를 FK로 참조합니다.</p>
 *
 * <p>협의회 ID 형식: {@code ASCT-{연도}-{4자리순번}} (예: ASCT-2026-0001)</p>
 *
 * <p>일반 협의회 상태 전이: DRAFT → SUBMITTED → APPROVAL_PENDING → APPROVED → PREPARING
 * → SCHEDULED → IN_PROGRESS → EVALUATING → RESULT_WRITING
 * → RESULT_REVIEW → FINAL_APPROVAL → RESULT_APPROVAL_PENDING → COMPLETED.
 * 계획협의회는 타당성검토·결재 단계를 생략하고 DRAFT에서 PREPARING으로 전이합니다.</p>
 */
@LogTarget(entity = BasctmL.class)
@Entity
@Table(name = "TPRMPP_BASCTM", comment = "정보화실무협의회 기본정보")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Basctm extends BaseEntity {

    /** 협의회ID: ASCT-{연도}-{4자리} 형식 (예: ASCT-2026-0001) */
    @Id
    @Column(name = "IT_PTL_ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String itPtlAsctId;

    /** 사업관리번호: 일반 협의회는 사업번호, 계획 협의회는 운영 스키마 계약에 따라 계획관리번호를 저장합니다. */
    @Column(name = "ABUS_MNG_NO", length = 30, comment = "프로젝트관리번호")
    private String abusMngNo;

    /** 프로젝트순번: TPRMPP_BPROJM.PRJ_SNO (FK) */
    @Column(name = "SNO", comment = "프로젝트순번")
    private Integer sno;

    /** 협의회상태: CCODEM ASCT_STS_C (3자리 코드, 001~013) */
    @Column(name = "IT_PTL_ASCT_PRG_STS_TC", length = 2, nullable = false, comment = "협의회상태코드")
    private String itPtlAsctPrgStsTc;

    /** IT포탈협의회심의구분코드: 2자리 코드 ('01'~'05') */
    @Column(name = "IT_PTL_ASCT_DBR_TC", length = 2, comment = "IT포탈협의회심의구분코드")
    private String itPtlAsctDbrTc;

    /** 회의일자: 일정 확정 시 설정 (DT 도메인 VARCHAR2(8), yyyyMMdd 저장 / 자바는 LocalDate) */
    @Column(name = "CNRC_DT", length = 8, comment = "회의일자")
    @Convert(converter = Yyyymmdd8DateConverter.class)
    private LocalDate cnrcDt;

    /** 회의시간: 10:00 / 14:00 / 15:00 / 16:00 중 선택 (TM 도메인 VARCHAR2(6)) */
    @Column(name = "CNRC_STT_TM", length = 6, comment = "회의시간")
    private String cnrcSttTm;

    /** 회의장소명: 일정 확정 시 입력 */
    @Column(name = "CNRC_PLC_NM", length = 100, comment = "회의장소명")
    private String cnrcPlc;

    /** 타당성검토생략여부: Y(생략) / N(검토 진행) */
    @Column(name = "PRTY_IVG_OMT_YN", length = 1, comment = "타당성검토생략여부")
    private String prtyIvgOmtYn;

    /** 타당성검토생략사유: 생략 시 필수 입력 (최대 200자) */
    @Column(name = "PRTY_IVG_OMT_RSN", length = 200, comment = "타당성검토생략사유")
    private String prtyIvgOmtRsn;

    /**
     * 대면개최여부: Y(대면개최) / N(서면개최) (PRD_c_20260620 #1).
     *
     * <p>개최준비 단계에서 위원들의 대면희망여부(BCMMTM.CSF_HP_YN)를 취합한 결과로 확정됩니다.
     * 한 명이라도 대면을 희망하면 'Y'(일정·장소 확정 후 대면 진행),
     * 전원 서면이면 'N'(회의일자/장소/시간 null, 서면질의응답 후 바로 평가).
     * null이면 미확정(대면 기본 취급).</p>
     */
    @Column(name = "CSF_HELD_YN", length = 1, comment = "대면개최여부")
    private String csfHeldYn;

    /**
     * 협의회 상태 변경
     *
     * <p>상태 전이 시 사용합니다. JPA Dirty Checking으로 자동 반영됩니다.</p>
     *
     * @param itPtlAsctPrgStsTc 변경할 상태 코드 (CCODEM ASCT_STS_C 기준)
     */
    public void changeStatus(String itPtlAsctPrgStsTc) {
        this.itPtlAsctPrgStsTc = itPtlAsctPrgStsTc;
    }

    /**
     * 회의 일정 확정 (SCHEDULED 상태 전이 시 호출)
     *
     * @param cnrcDt  회의일자
     * @param cnrcSttTm  회의시간 (10:00/14:00/15:00/16:00)
     * @param cnrcPlc 회의장소
     */
    public void confirmSchedule(LocalDate cnrcDt, String cnrcSttTm, String cnrcPlc) {
        this.cnrcDt = cnrcDt;
        this.cnrcSttTm = cnrcSttTm;
        this.cnrcPlc = cnrcPlc;
        // 일정을 확정한다는 것은 대면개최를 의미한다.
        this.csfHeldYn = "Y";
    }

    /**
     * 서면개최 확정 (PRD_c_20260620 #1)
     *
     * <p>위원 전원이 대면을 희망하지 않아 서면으로 진행할 때 호출합니다.
     * 대면개최여부를 'N'으로 설정하고 회의일자/시간/장소를 모두 비웁니다.</p>
     */
    public void markWrittenMeeting() {
        this.csfHeldYn = "N";
        this.cnrcDt = null;
        this.cnrcSttTm = null;
        this.cnrcPlc = null;
    }

    /**
     * 타당성검토 생략 판정 결과 기록 (IT기획 판정 시)
     *
     * <p>정보보호기획의 생략 판정 요청(BASKPM)에 대해 IT기획이 내린 최종 생략여부와 사유를
     * 협의회 마스터에 기록합니다. 생략 판정의 권위 저장소는 BASCTM이며, BASKPM에는
     * 요청·판정 접수 메타데이터만 남깁니다.</p>
     *
     * @param prtyIvgOmtYn  생략여부 (Y=생략 / N=개최)
     * @param prtyIvgOmtRsn 생략(판정) 사유
     */
    public void recordSkipDecision(String prtyIvgOmtYn, String prtyIvgOmtRsn) {
        this.prtyIvgOmtYn = prtyIvgOmtYn;
        this.prtyIvgOmtRsn = prtyIvgOmtRsn;
    }
}

