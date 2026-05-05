package com.kdb.it.domain.council.entity;

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
 * 협의회 평가위원 엔티티
 *
 * <p>DB 테이블: {@code TAAABB_BCMMTM}</p>
 *
 * <p>IT관리자(ITPAD001)가 심의유형에 따라 위원을 선정합니다.
 * 위원유형(VLR_TP) 분류:</p>
 * <ul>
 *   <li>MAND — 당연위원 (심의유형별 고정 부서 자동 매핑)</li>
 *   <li>CALL — 소집위원 (IT관리자가 추가 지정)</li>
 *   <li>SECR — 간사 (회의 진행 담당)</li>
 * </ul>
 *
 * <p>당연위원 자동 매핑 규칙 (TEM_C 기준):</p>
 * <ul>
 *   <li>INFO_SYS: 예산(12004), PMO(18010), 디지털기획(18501), 정보보호기획(18301)</li>
 *   <li>INFO_SEC: 예산(12004), IT기획(18001), PMO(18010), 디지털기획(18501)</li>
 *   <li>ETC: 예산(12004), PMO(18010), 디지털기획(18501)</li>
 * </ul>
 *
 * <p>복합키: ({@code ASCT_ID}, {@code ENO})</p>
 */
@Entity
@Table(name = "TAAABB_BCMMTM")
@IdClass(BcmmtmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bcmmtm extends BaseEntity {

    /** 협의회ID: 복합키 첫 번째 컬럼 */
    @Id
    @Column(name = "ASCT_ID", length = 32, nullable = false)
    private String asctId;

    /** 사번: 복합키 두 번째 컬럼 (TAAABB_CUSERI.ENO FK) */
    @Id
    @Column(name = "ENO", length = 32, nullable = false)
    private String eno;

    /** 위원유형: MAND(당연위원) / CALL(소집위원) / SECR(간사), CCODEM VLR_TP 기준 */
    @Column(name = "VLR_TP", length = 32, nullable = false)
    private String vlrTp;

    /**
     * 결과서 검토 확인 여부
     * 평가위원이 RESULT_REVIEW 단계에서 결과서 확인 완료 시 'Y'로 변경됩니다.
     * 전원 'Y'가 되면 협의회 상태가 FINAL_APPROVAL로 전이됩니다.
     */
    @Column(name = "CNFM_YN", length = 1, nullable = false)
    @lombok.Builder.Default
    private String cnfmYn = "N";

    /**
     * 위원유형 변경 (소집→당연 또는 간사 재지정 시)
     *
     * @param vlrTp 변경할 위원유형 코드
     */
    public void changeType(String vlrTp) {
        this.vlrTp = vlrTp;
    }

    /**
     * 결과서 검토 확인 처리
     * 평가위원이 결과서를 확인하면 호출됩니다.
     */
    public void confirmReview() {
        this.cnfmYn = "Y";
    }
}
