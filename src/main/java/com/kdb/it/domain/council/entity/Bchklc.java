package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BchklcL;
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
 * 타당성 자체점검 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_BCHKLC}</p>
 *
 * <p>협의회 1건당 6개 고정 항목이 생성됩니다 (CCODEM CKG_ITM_C 기준):</p>
 * <ol>
 *   <li>MGMT_STR — 경영전략/계획 부합</li>
 *   <li>FIN_EFC — 재무 효과</li>
 *   <li>RISK_IMP — 리스크 개선 효과</li>
 *   <li>REP_IMP — 평판/이미지 개선 효과</li>
 *   <li>DUP_SYS — 유사/중복 시스템 유무</li>
 *   <li>ETC — 기타</li>
 * </ol>
 *
 * <p>복합키: ({@code ASCT_ID}, {@code CKG_ITM_C})</p>
 */
@LogTarget(entity = BchklcL.class)
@Entity
@Table(name = "TPRMPP_BCHKLC", comment = "타당성 자체점검")
@IdClass(BchklcId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bchklc extends BaseEntity {

    /** 협의회ID: 복합키 첫 번째 컬럼 */
    @Id
    @Column(name = "IT_PTL_ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String itPtlAsctId;

    /** 점검항목코드: 복합키 두 번째 컬럼 (MGMT_STR/FIN_EFC/RISK_IMP/REP_IMP/DUP_SYS/ETC) */
    @Id
    @Column(name = "IT_PTL_CKG_ITM_TC", length = 2, nullable = false, comment = "점검항목코드")
    private String itPtlCkgItmTc;

    /** 점검내용: 소관부서 담당자가 입력하는 자체 검토 내용 (최대 2000자) */
    @Column(name = "CKG_OPNN_CONE", length = 1000, comment = "점검의견내용")
    private String ckgOpnnCone;

    /** 점검점수: 1~5점 척도 */
    @Column(name = "QUEL_RCRD", comment = "점검점수")
    private Integer quelRcrd;

    /**
     * 자체점검 내용 및 점수 업데이트
     *
     * @param ckgOpnnCone 점검내용
     * @param quelRcrd 점검점수 (1~5)
     */
    public void update(String ckgOpnnCone, Integer quelRcrd) {
        this.ckgOpnnCone = ckgOpnnCone;
        this.quelRcrd = quelRcrd;
    }
}

