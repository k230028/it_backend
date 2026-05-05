package com.kdb.it.domain.budget.plan.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BplanmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 정보기술부문계획(TAAABB_BPLANM) 엔티티
 *
 * <p>
 * 연도별 IT 부문 계획을 저장하는 마스터 엔티티입니다.
 * 계획에 포함된 전체 프로젝트 데이터는 {@code plnDtlCone}에 JSON 형태로 스냅샷 저장됩니다.
 * </p>
 *
 * <p>
 * 계획관리번호({@code plnMngNo}) 채번 규칙:
 * {@code PLN-{대상년도}-{S_PLN_MNG_NO 시퀀스 4자리}} (예: PLN-2026-0001)
 * </p>
 */
@LogTarget(entity = BplanmL.class)
@Entity
@Table(name = "TAAABB_BPLANM", comment = "정보기술부문계획")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Bplanm extends BaseEntity {

    /** 계획관리번호 (PK, 형식: PLN-{연도}-{seq:04d}) */
    @Id
    @Column(name = "PLN_MNG_NO", length = 32, comment = "계획관리번호")
    private String plnMngNo;

    /** 계획구분 (신규, 조정) */
    @Column(name = "PLN_TP", length = 16, comment = "계획구분")
    private String plnTp;

    /** 대상년도 (형식: YYYY) */
    @Column(name = "PLN_YY", length = 4, comment = "대상년도")
    private String plnYy;

    /**
     * 계획세부내용 (JSON 스냅샷)
     * <p>
     * 계획 저장 시점의 전체 프로젝트 데이터를 JSON으로 직렬화하여 보관합니다.
     * 계획 상세 화면에서 이 값을 파싱하여 표시합니다.
     * </p>
     */
    @Lob
    @Column(name = "PLN_DTL_CONE", comment = "계획세부내용")
    private String plnDtlCone;

    /** 총예산 (전체 대상사업의 프로젝트 예산 합계) */
    @Column(name = "TTL_BG", precision = 15, scale = 2, comment = "총예산")
    private BigDecimal ttlBg;

    /** 자본예산 (전체 대상사업의 자본예산 합계) */
    @Column(name = "CPT_BG", precision = 15, scale = 2, comment = "자본예산")
    private BigDecimal cptBg;

    /** 일반관리비 (전체 대상사업의 일반관리비 합계) */
    @Column(name = "MNGC", precision = 15, scale = 2, comment = "일반관리비")
    private BigDecimal mngc;
}
