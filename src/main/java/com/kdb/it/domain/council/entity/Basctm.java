package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BasctmL;
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
 * 정보화실무협의회 기본정보 엔티티
 *
 * <p>DB 테이블: {@code TAAABB_BASCTM}</p>
 *
 * <p>협의회 전체 프로세스의 루트 엔티티입니다.
 * 타당성검토표(Bpovwm), 결과서(Brsltm)가 이 엔티티를 FK로 참조합니다.</p>
 *
 * <p>협의회 ID 형식: {@code ASCT-{연도}-{4자리순번}} (예: ASCT-2026-0001)</p>
 *
 * <p>상태 전이: DRAFT → SUBMITTED → APPROVAL_PENDING → APPROVED → PREPARING
 * → SCHEDULED → IN_PROGRESS → EVALUATING → RESULT_WRITING
 * → RESULT_REVIEW → FINAL_APPROVAL → COMPLETED</p>
 */
@LogTarget(entity = BasctmL.class)
@Entity
@Table(name = "TAAABB_BASCTM", comment = "정보화실무협의회 기본정보")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Basctm extends BaseEntity {

    /** 협의회ID: ASCT-{연도}-{4자리} 형식 (예: ASCT-2026-0001) */
    @Id
    @Column(name = "ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String asctId;

    /** 프로젝트관리번호: TAAABB_BPROJM.PRJ_MNG_NO (FK, 협의회 대상 사업) */
    @Column(name = "PRJ_MNG_NO", length = 32, comment = "프로젝트관리번호")
    private String prjMngNo;

    /** 프로젝트순번: TAAABB_BPROJM.PRJ_SNO (FK) */
    @Column(name = "PRJ_SNO", comment = "프로젝트순번")
    private Integer prjSno;

    /** 협의회상태: CCODEM ASCT_STS (DRAFT~COMPLETED, 12단계) */
    @Column(name = "ASCT_STS", length = 20, nullable = false, comment = "협의회상태")
    private String asctSts;

    /** 심의유형: INFO_SYS(정보시스템) / INFO_SEC(정보보호시스템) / ETC(기타) */
    @Column(name = "DBR_TP", length = 20, comment = "심의유형")
    private String dbrTp;

    /** 회의일자: 일정 확정 시 설정 */
    @Column(name = "CNRC_DT", comment = "회의일자")
    private LocalDate cnrcDt;

    /** 회의시간: 10:00 / 14:00 / 15:00 / 16:00 중 선택 */
    @Column(name = "CNRC_TM", length = 10, comment = "회의시간")
    private String cnrcTm;

    /** 회의장소: 일정 확정 시 입력 */
    @Column(name = "CNRC_PLC", length = 200, comment = "회의장소")
    private String cnrcPlc;

    /**
     * 협의회 상태 변경
     *
     * <p>상태 전이 시 사용합니다. JPA Dirty Checking으로 자동 반영됩니다.</p>
     *
     * @param asctSts 변경할 상태 코드 (CCODEM ASCT_STS 기준)
     */
    public void changeStatus(String asctSts) {
        this.asctSts = asctSts;
    }

    /**
     * 회의 일정 확정 (SCHEDULED 상태 전이 시 호출)
     *
     * @param cnrcDt  회의일자
     * @param cnrcTm  회의시간 (10:00/14:00/15:00/16:00)
     * @param cnrcPlc 회의장소
     */
    public void confirmSchedule(LocalDate cnrcDt, String cnrcTm, String cnrcPlc) {
        this.cnrcDt = cnrcDt;
        this.cnrcTm = cnrcTm;
        this.cnrcPlc = cnrcPlc;
    }
}

