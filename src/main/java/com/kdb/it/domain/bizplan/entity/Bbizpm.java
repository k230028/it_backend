package com.kdb.it.domain.bizplan.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BbizpmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 사업계획 기본(마스터) 엔티티.
 *
 * <p>DB 테이블: {@code TPRMPP_BBIZPM}. 정보화사업(BPROJM)과 1:1(PK=ABUS_MNG_NO)이며,
 * 상태(21 작성중 / 29 작성완료)는 본 테이블이 아니라 BPROJA에
 * {@code CNCD_RFR_NO='BIZ-'+ABUS_MNG_NO} 행으로 기록한다.</p>
 */
@LogTarget(entity = BbizpmL.class)
@Entity
@Table(name = "TPRMPP_BBIZPM", comment = "사업계획기본")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bbizpm extends BaseEntity {

    @Id
    @Column(name = "ABUS_MNG_NO", length = 30, nullable = false, comment = "사업관리번호")
    private String abusMngNo;

    @Column(name = "ABUS_NM", length = 100, comment = "사업명(생성 시 BPROJM 스냅샷)")
    private String abusNm;

    @Lob
    @Column(name = "ABUS_PUL_NCS_INF", comment = "사업추진필요성정보")
    private String abusPulNcsInf;

    @Lob
    @Column(name = "ABUS_PUL_DRCN_INF", comment = "사업추진방향정보")
    private String abusPulDrcnInf;

    @Lob
    @Column(name = "ABUS_PUL_CONE_INF", comment = "사업추진내용정보")
    private String abusPulConeInf;

    @Lob
    @Column(name = "ABUS_XPT_EFF_INF", comment = "사업기대효과정보")
    private String abusXptEffInf;

    @Lob
    @Column(name = "REDT_CONE_INF", comment = "보고서내용정보")
    private String redtConeInf;

    @Column(name = "BG_NO", length = 15, comment = "예산번호(BPROJA 예산편성 행에서 자동 연계)")
    private String bgNo;

    @Column(name = "TOT_RQM_AMT", precision = 18, scale = 3, comment = "총소요금액(품목 금액 합계 자동 계산)")
    private BigDecimal totRqmAmt;

    @Column(name = "IT_PTL_EDRT_TC", length = 2, comment = "IT포탈전결권구분코드")
    private String itPtlEdrtTc;

    /**
     * 보고서/전결권 저장 (save에서 호출, 상태 무관 반복 수정 허용).
     *
     * @param redtConeInf 보고서 HTML(서비스에서 새니타이즈 후 전달)
     * @param itPtlEdrtTc 전결권구분코드
     */
    public void updateBasics(String redtConeInf, String itPtlEdrtTc) {
        this.redtConeInf = redtConeInf;
        this.itPtlEdrtTc = itPtlEdrtTc;
    }

    /** 총소요금액 재계산 결과 반영 (save 마지막 단계에서 호출) */
    public void changeTotalAmount(BigDecimal totRqmAmt) {
        this.totRqmAmt = totRqmAmt;
    }
}
