package com.kdb.it.domain.budget.work.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BbugtL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 예산 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_BBUGTM}
 *
 * <p>결재완료된 정보화사업(BPROJM→BITEMM) 및 전산업무비(BCOSTM)의 비목별 금액에 편성률을 적용한 편성예산을 관리합니다.
 *
 * <p>복합키 구조: ({@code BG_NO}, {@code SNO}) 관리번호 채번 형식: {@code BG-{예산년도}-{SQ_TPRMPP_BBUGTM_1 시퀀스
 * 4자리}} (예: {@code BG-2026-0001}) // Design Ref: §2.1 — Bbugtm 엔티티 (Option C Pragmatic Balance)
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
    @Column(
            name = "SNO",
            nullable = false,
            precision = 9,
            scale = 0,
            comment = "예산일련번호 (물리컬럼 SNO=일련번호)")
    private Integer sno;

    /** 예산년도 (예: "2026") */
    @Column(name = "BSE_YY", length = 4, comment = "예산년도 (물리컬럼 BSE_YY=기준연도)")
    private String bseYy;

    /** 원본테이블: 집계 대상 테이블 (BITEMM 또는 BCOSTM) */
    @Column(name = "FNT_TB_NM", length = 120, comment = "원본테이블 (물리컬럼 FNT_TB_NM=원천테이블명)")
    private String fntTbNm;

    /** 원본PK값: BITEMM이면 품목관리번호(GCL_MNG_NO), BCOSTM이면 전산업무비코드(BG_NO) */
    @Column(name = "PK_COL_NM", length = 4000, comment = "원본PK값 (물리컬럼 PK_COL_NM=주식별자컬럼명)")
    private String pkColNm;

    /** 원본일련번호값: 원본 레코드의 일련번호 */
    @Column(
            name = "FNT_TB_CRY_SNO",
            precision = 10,
            scale = 0,
            comment = "원본일련번호값 (물리컬럼 FNT_TB_CRY_SNO=원천테이블적재일련번호)")
    private Integer fntTbCrySno;

    /** 비목코드: 편성비목에 매칭된 비목코드 */
    @Column(name = "IOE_C", length = 7, comment = "비목코드")
    private String ioeC;

    /** 편성예산: 요청금액 × (편성률 / 100) */
    @Column(
            name = "BG_DUP_AMT",
            precision = 18,
            scale = 3,
            comment = "편성예산금액 (물리컬럼 BG_DUP_AMT=예산편성금액)")
    private BigDecimal bgDupAmt;

    /**
     * 편성률: 0~100 범위의 소수 5자리.
     *
     * <p>물리컬럼 ASG_RT(메타표준=배정률)은 NUMBER(8,5)다. 종전에는 Integer로 매핑했으나, 하반기 계획 조정의 확정 편성금액
     * (예: 요청 1,406에 대한 편성 416)은 29.58748%처럼 정수로 담기지 않는다. 물리 스케일을 그대로 쓰면
     * `요청금액 × 편성률 = 편성금액` 불변식이 확정금액 조정에서도 성립한다.
     */
    @Column(name = "ASG_RT", precision = 8, scale = 5, comment = "편성률 (물리컬럼 ASG_RT=배정률)")
    private BigDecimal asgRt;

    /**
     * 편성 정보 업데이트 메서드
     *
     * <p>JPA Dirty Checking을 활용하여 트랜잭션 내에서 편성예산과 편성률을 변경합니다. Upsert 시 기존 레코드가 존재하면 이 메서드로 UPDATE
     * 처리합니다.
     *
     * @param bgDupAmt 편성예산 (요청금액 × 편성률/100)
     * @param asgRt 편성률 (0~100, 소수 5자리)
     */
    public void update(BigDecimal bgDupAmt, BigDecimal asgRt) {
        this.bgDupAmt = bgDupAmt;
        this.asgRt = asgRt;
    }
}
