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
 * DB 테이블: {@code TAAABB_BBUGTM}
 * </p>
 *
 * <p>
 * 결재완료된 정보화사업(BPROJM→BITEMM) 및 전산업무비(BCOSTM)의
 * 비목별 금액에 편성률을 적용한 편성예산을 관리합니다.
 * </p>
 *
 * <p>
 * 복합키 구조: ({@code BG_MNG_NO}, {@code BG_SNO})
 * 관리번호 채번 형식: {@code BG-{예산년도}-{S_BG 시퀀스 4자리}} (예: {@code BG-2026-0001})
 * </p>
 *
 * // Design Ref: §2.1 — Bbugtm 엔티티 (Option C Pragmatic Balance)
 */
@LogTarget(entity = BbugtL.class)
@Entity
@Table(name = "TAAABB_BBUGTM", comment = "예산")
@IdClass(BbugtmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bbugtm extends BaseEntity {

    /** 예산관리번호: 기본키 (예: BG-2026-0001) */
    @Id
    @Column(name = "BG_MNG_NO", nullable = false, length = 32, comment = "예산관리번호")
    private String bgMngNo;

    /** 예산일련번호: 복합 기본키의 두 번째 컬럼 */
    @Id
    @Column(name = "BG_SNO", nullable = false, comment = "예산일련번호")
    private Integer bgSno;

    /** 예산년도 (예: "2026") */
    @Column(name = "BG_YY", length = 4, comment = "예산년도")
    private String bgYy;

    /** 원본테이블: 집계 대상 테이블 (BPROJM 또는 BCOSTM) */
    @Column(name = "ORC_TB", length = 10, comment = "원본테이블")
    private String orcTb;

    /** 원본PK값: 원본 레코드의 관리번호 */
    @Column(name = "ORC_PK_VL", length = 32, comment = "원본PK값")
    private String orcPkVl;

    /** 원본일련번호값: 원본 레코드의 일련번호 */
    @Column(name = "ORC_SNO_VL", comment = "원본일련번호값")
    private Integer orcSnoVl;

    /** 비목코드: 편성비목에 매칭된 비목코드 */
    @Column(name = "IOE_C", length = 100, comment = "비목코드")
    private String ioeC;

    /** 편성예산: 요청금액 × (편성률 / 100) */
    @Column(name = "DUP_BG", precision = 15, scale = 2, comment = "편성예산")
    private BigDecimal dupBg;

    /** 편성률: 0~100 사이의 정수 */
    @Column(name = "DUP_RT", precision = 3, scale = 0, comment = "편성률")
    private Integer dupRt;

    /**
     * 편성 정보 업데이트 메서드
     *
     * <p>
     * JPA Dirty Checking을 활용하여 트랜잭션 내에서 편성예산과 편성률을 변경합니다.
     * Upsert 시 기존 레코드가 존재하면 이 메서드로 UPDATE 처리합니다.
     * </p>
     *
     * @param dupBg 편성예산 (요청금액 × 편성률/100)
     * @param dupRt 편성률 (0~100)
     */
    public void update(BigDecimal dupBg, Integer dupRt) {
        this.dupBg = dupBg;
        this.dupRt = dupRt;
    }
}
