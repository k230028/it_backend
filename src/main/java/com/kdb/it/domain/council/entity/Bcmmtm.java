package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BcmmtmL;
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
 * <p>DB 테이블: {@code TPRMPP_BCMMTM}
 *
 * <p>IT관리자(ITPAD001)가 심의유형에 따라 위원을 선정합니다. 위원유형(VLR_TC) 분류:
 *
 * <ul>
 *   <li>MAND — 당연위원 (심의유형별 고정 부서 자동 매핑)
 *   <li>CALL — 소집위원 (IT관리자가 추가 지정)
 *   <li>SECR — 간사 (회의 진행 담당)
 * </ul>
 *
 * <p>당연위원 자동 매핑 규칙 (TEM_C 기준):
 *
 * <ul>
 *   <li>INFO_SYS: 예산(12004), PMO(18010), 디지털기획(18501), 정보보호기획(18301)
 *   <li>INFO_SEC: 예산(12004), IT기획(18001), PMO(18010), 디지털기획(18501)
 *   <li>ETC: 예산(12004), PMO(18010), 디지털기획(18501)
 * </ul>
 *
 * <p>복합키: ({@code ASCT_ID}, {@code ENO})
 */
@LogTarget(entity = BcmmtmL.class)
@Entity
@Table(name = "TPRMPP_BCMMTM", comment = "협의회 평가위원")
@IdClass(BcmmtmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bcmmtm extends BaseEntity {

    /** 협의회ID: 복합키 첫 번째 컬럼 */
    @Id
    @Column(name = "IT_PTL_ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String itPtlAsctId;

    /** 사번: 복합키 두 번째 컬럼 (TPRMPP_CUSERI.ENO FK) */
    @Id
    @Column(name = "ENO", length = 32, nullable = false, comment = "사번")
    private String eno;

    /** 위원유형구분코드: MAND(당연위원) / CALL(소집위원) / SECR(간사), CCODEM VLR_TC 기준 */
    @Column(name = "IT_PTL_ASCT_MEB_TC", length = 2, nullable = false, comment = "위원유형구분코드")
    private String itPtlAsctMebTc;

    /**
     * 결과서 검토 확인 여부 평가위원이 RESULT_REVIEW 단계에서 결과서 확인 완료 시 'Y'로 변경됩니다. 전원 'Y'가 되면 협의회 상태가
     * FINAL_APPROVAL로 전이됩니다.
     */
    @Column(name = "CNFM_YN", length = 1, nullable = false, comment = "확인여부")
    @lombok.Builder.Default
    private String cnfmYn = "N";

    /**
     * 대면희망여부: Y(대면 희망) / N(서면 가능) (PRD_c_20260620 #1).
     *
     * <p>개최준비(PREPARING) 단계에서 위원이 가능 일정을 응답할 때 함께 선택합니다. 대면을 희망하지 않더라도 가능 일정 선택은 필수입니다. 위원 전원이
     * 'N'이면 협의회는 서면으로 개최됩니다. null이면 미응답.
     */
    @Column(name = "CSF_HP_YN", length = 1, comment = "대면희망여부")
    private String csfHpYn;

    /**
     * 위원유형 변경 (소집→당연 또는 간사 재지정 시)
     *
     * @param itPtlAsctMebTc 변경할 위원유형 코드
     */
    public void changeType(String itPtlAsctMebTc) {
        this.itPtlAsctMebTc = itPtlAsctMebTc;
    }

    /** 결과서 검토 확인 처리 평가위원이 결과서를 확인하면 호출됩니다. */
    public void confirmReview() {
        this.cnfmYn = "Y";
    }

    /**
     * 대면희망여부 응답 (위원이 일정 응답 시 호출)
     *
     * @param csfHpYn 대면희망여부 (Y/N)
     */
    public void respondFaceToFace(String csfHpYn) {
        this.csfHpYn = csfHpYn;
    }
}
