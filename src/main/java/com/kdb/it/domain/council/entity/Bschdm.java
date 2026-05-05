package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BschdmL;
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
 * 협의회 일정 엔티티
 *
 * <p>DB 테이블: {@code TAAABB_BSCHDM}</p>
 *
 * <p>평가위원별 가능 일정을 수집합니다.
 * IT관리자가 후보 날짜/시간대를 설정하면, 각 위원이 PSB_YN으로 가능 여부를 응답합니다.
 * 전원 응답 완료 후 IT관리자가 최종 일정을 확정하여 BASCTM.CNRC_DT/TM/PLC에 반영합니다.</p>
 *
 * <p>허용 시간대: 10:00 / 14:00 / 15:00 / 16:00</p>
 *
 * <p>복합키: ({@code ASCT_ID}, {@code ENO}, {@code DSD_DT}, {@code DSD_TM})</p>
 */
@LogTarget(entity = BschdmL.class)
@Entity
@Table(name = "TAAABB_BSCHDM", comment = "협의회 일정")
@IdClass(BschdmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bschdm extends BaseEntity {

    /** 협의회ID: 복합키 첫 번째 컬럼 */
    @Id
    @Column(name = "ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String asctId;

    /** 사번: 복합키 두 번째 컬럼 (TAAABB_CUSERI.ENO FK, 평가위원) */
    @Id
    @Column(name = "ENO", length = 32, nullable = false, comment = "사번")
    private String eno;

    /** 일정일자: 복합키 세 번째 컬럼 (후보 날짜) */
    @Id
    @Column(name = "DSD_DT", nullable = false, comment = "일정일자")
    private LocalDate dsdDt;

    /** 일정시간: 복합키 네 번째 컬럼 (10:00/14:00/15:00/16:00) */
    @Id
    @Column(name = "DSD_TM", length = 10, nullable = false, comment = "일정시간")
    private String dsdTm;

    /** 가능여부: Y(가능) / N(불가), 기본값 N */
    @Column(name = "PSB_YN", length = 1, comment = "가능여부")
    private String psbYn;

    /**
     * 일정 가능 여부 응답 (위원이 입력)
     *
     * @param psbYn 가능여부 (Y/N)
     */
    public void respond(String psbYn) {
        this.psbYn = psbYn;
    }
}

