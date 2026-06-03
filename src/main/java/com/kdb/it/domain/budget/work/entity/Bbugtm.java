package com.kdb.it.domain.budget.work.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BbugtL;
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

import java.math.BigDecimal;

/**
 * 예산 엔티티
 *
 * <p>
 * DB 테이블: {@code TPRMPP_BBUGTM}
 * </p>
 *
 * <p>
 * 결재완료된 정보화사업(BPROJM→BITEMM) 및 전산업무비(BCOSTM)의
 * 비목별 금액에 편성률을 적용한 편성예산을 관리합니다.
 * </p>
 *
 * <p>
 * 복합키 구조: ({@code BG_NO}, {@code SNO})
 * 관리번호 채번 형식: {@code BG-{예산년도}-{SEQ_BBUGTM 시퀀스 4자리}} (예: {@code BG-2026-0001})
 * </p>
 *
 * // Design Ref: §2.1 — Bbugtm 엔티티 (Option C Pragmatic Balance)
 */
@LogTarget(entity = BbugtL.class)
@Entity
@Table(name = "TPRMPP_BBUGTM", comment = "예산")
@IdClass(BbugtmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bbugtm extends BaseEntity {

    /** 예산관리번호: 기본키 (예: BG-2026-0001) */
    @Id
    @Column(name = "BG_NO", nullable = false, length = 15, comment = "예산관리번호 (물리컬럼 BG_NO=예산번호)")
    private String bgNo;

    /** 예산일련번호: 복합 기본키의 두 번째 컬럼 */
    @Id
    @Column(name = "SNO", nullable = false, precision = 9, scale = 0, comment = "예산일련번호 (물리컬럼 SNO=일련번호)")
    private Integer sno;

    /** 예산년도 (예: "2026") */
    @Column(name = "BSE_YY", length = 4, comment = "예산년도 (물리컬럼 BSE_YY=기준연도)")
    private String bseYy;

    /** 원본테이블: 집계 대상 테이블 (BPROJM 또는 BCOSTM) */
    @Column(name = "FNT_TB_NM", length = 120, comment = "원본테이블 (물리컬럼 FNT_TB_NM=원천테이블명)")
    private String fntTbNm;

    /** 원본PK값: 원본 레코드의 관리번호 */
    @Column(name = "PK_COL_NM", length = 4000, comment = "원본PK값 (물리컬럼 PK_COL_NM=주식별자컬럼명)")
    private String pkColNm;

    /** 원본일련번호값: 원본 레코드의 일련번호 */
    @Column(name = "FNT_TB_CRY_SNO", precision = 10, scale = 0, comment = "원본일련번호값 (물리컬럼 FNT_TB_CRY_SNO=원천테이블적재일련번호)")
    private Integer fntTbCrySno;

    /** 비목코드: 편성비목에 매칭된 비목코드 */
    @Column(name = "IOE_C", length = 7, comment = "비목코드")
    private String ioeC;

    /** 편성예산: 요청금액 × (편성률 / 100) */
    @Column(name = "RQM_BG_AMT", precision = 18, scale = 3, comment = "편성예산금액 (물리컬럼 RQM_BG_AMT=소요예산금액)")
    private BigDecimal bugRqmBgAmt;

    /**
     * 편성률: 0~100 사이의 정수.
     * <p>물리컬럼 ASG_RT(메타표준=배정률)은 NUMBER(8,5)로 소수 5자리까지 저장 가능하나,
     * 현재 도메인은 정수 편성률만 사용하므로 Integer로 매핑합니다.</p>
     */
    @Column(name = "ASG_RT", precision = 8, scale = 5, comment = "편성률 (물리컬럼 ASG_RT=배정률)")
    private Integer asgRt;

    /**
     * 편성 정보 업데이트 메서드
     *
     * <p>
     * JPA Dirty Checking을 활용하여 트랜잭션 내에서 편성예산과 편성률을 변경합니다.
     * Upsert 시 기존 레코드가 존재하면 이 메서드로 UPDATE 처리합니다.
     * </p>
     *
     * @param bugRqmBgAmt 편성예산 (요청금액 × 편성률/100)
     * @param asgRt 편성률 (0~100)
     */
    public void update(BigDecimal bugRqmBgAmt, Integer asgRt) {
        this.bugRqmBgAmt = bugRqmBgAmt;
        this.asgRt = asgRt;
    }
}
