package com.kdb.it.domain.budget.cost.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BtermmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 단말기관리마스터 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_BTERMM}
 *
 * <p>금융정보단말기 관련 상세 정보를 관리하며, 전산관리비({@link Bcostm})와 1:N 관계를 가집니다.
 */
@LogTarget(entity = BtermmL.class)
@Entity
@Table(name = "TPRMPP_BTERMM", comment = "단말기관리마스터")
@IdClass(BtermmId.class)
@Getter
@AllArgsConstructor
@SuperBuilder
public class Btermm extends BaseEntity {

    /** JPA가 단말기관리 엔티티를 복원할 때 사용하는 기본 생성자입니다. */
    protected Btermm() {}

    /** 단말기관리번호 */
    @Id
    @Column(name = "TMN_MNG_NO", nullable = false, length = 16, comment = "단말관리번호")
    private String tmnMngNo;

    /** 단말기일련번호 */
    @Id
    @Column(name = "SNO", nullable = false, precision = 9, comment = "일련번호")
    private Integer sno;

    /** 예산번호 (조인용 필드) */
    @Column(name = "BG_NO", length = 15, comment = "예산번호")
    private String termBgNo;

    /** 예산일련번호 (조인용 필드) */
    @Column(name = "BG_SNO", precision = 9, comment = "예산일련번호")
    private Integer termBgSno;

    /** 전산관리비와의 연관관계 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(
                name = "BG_NO",
                referencedColumnName = "BG_NO",
                insertable = false,
                updatable = false),
        @JoinColumn(
                name = "BG_SNO",
                referencedColumnName = "BG_SNO",
                insertable = false,
                updatable = false)
    })
    private Bcostm bcostm;

    /** 단말기명 */
    @Column(name = "SPF_TMN_NM", length = 100, comment = "단말기명 (물리컬럼 SPF_TMN_NM=특정단말명)")
    private String spfTmnNm;

    /** 단말기이용방법 (최대 2자) */
    @Column(
            name = "IT_PTL_TMN_KD_TC",
            length = 2,
            comment = "단말기이용방법 (물리컬럼 IT_PTL_TMN_KD_TC=단말종류구분코드)")
    private String tmnKdTc;

    /** 소요자금용도내용 */
    @Column(name = "NSF_USG_CONE", length = 200, comment = "소요자금용도내용")
    private String nsfUsgCone;

    /** 단말기서비스 (최대 2자) */
    @Column(
            name = "IT_PTL_TMN_SVC_TC",
            length = 2,
            comment = "단말기서비스 (물리컬럼 IT_PTL_TMN_SVC_TC=단말서비스구분코드)")
    private String tmnClsfC;

    /** 단말기금액 (최대 18자리, 소수점 3자리) */
    @Column(name = "AMT", precision = 18, scale = 3, comment = "단말기금액 (물리컬럼 AMT=금액)")
    private BigDecimal termRqmBgAmt;

    /** 통화 */
    @Column(name = "CUR_C", length = 3, comment = "통화코드")
    private String curC;

    /** 환율 */
    @Column(name = "XCR", precision = 9, scale = 4, comment = "환율")
    private BigDecimal xcr;

    /** 환율기준일자 (YYYYMMDD, 8자리) */
    @Column(name = "XCR_BSE_DT", length = 8, comment = "환율기준일자")
    private String xcrBseDt;

    /** 지급주기코드 (최대 1자) */
    @Column(name = "DFR_CLE_C", length = 1, nullable = false, comment = "지급주기코드")
    private String dfrCleC;

    /** 증감사유 (최대 200자) */
    @Column(name = "IND_RSN", length = 200, comment = "증감사유")
    private String indRsn;

    /** 담당자 (최대 14자) */
    @Column(name = "CGPR_ID", length = 14, comment = "담당자행번 (물리컬럼 CGPR_ID=담당자ID)")
    private String cgprId;

    /** 담당팀 (최대 5자) */
    @Column(name = "SVN_TEM_C", length = 5, comment = "담당팀코드 (물리컬럼 SVN_TEM_C=주관팀코드)")
    private String termSvnTemC;

    /** 담당부서 (최대 20자) */
    @Column(name = "SVN_DPM_C", length = 20, comment = "담당부서코드 (물리컬럼 SVN_DPM_C=주관부서코드)")
    private String termSvnDpmC;

    /** 비고 */
    @Column(name = "RMK", length = 300, comment = "비고")
    private String rmk;

    /**
     * 외화금액(단말기 외화 원금 — 환율 적용 전).
     *
     * <p>원화(KRW) 행은 NULL. 외화 행은 사용자 입력 외화 원금이며, 서버 재계산 로직(plan 03/04)에서 {@code termRqmBgAmt = fcAmt
     * × xcr}로 환산된다. 참고: CONTEXT.md 결정 B (KRW 행 FC_AMT = NULL).
     */
    @Column(name = "FC_AMT", precision = 18, scale = 3, comment = "외화금액")
    private BigDecimal fcAmt;

    /**
     * 단말기 관리 정보를 변경합니다.
     *
     * @param spfTmnNm 특정단말명
     * @param tmnKdTc IT포탈 단말종류구분코드
     * @param nsfUsgCone 소요자금용도내용
     * @param tmnClsfC IT포탈 단말서비스구분코드
     * @param termRqmBgAmt 단말기 금액
     * @param curC 통화코드
     * @param xcr 환율
     * @param xcrBseDt 환율기준일자
     * @param dfrCleC 지급주기코드
     * @param indRsn 증감사유
     * @param cgprId 담당자 ID
     * @param termSvnTemC 주관팀코드
     * @param termSvnDpmC 주관부서코드
     * @param rmk 비고
     * @param fcAmt 외화금액 (원화 행은 null, 외화 행은 사용자 입력 외화 원금)
     */
    public void update(
            String spfTmnNm,
            String tmnKdTc,
            String nsfUsgCone,
            String tmnClsfC,
            BigDecimal termRqmBgAmt,
            String curC,
            BigDecimal xcr,
            String xcrBseDt,
            String dfrCleC,
            String indRsn,
            String cgprId,
            String termSvnTemC,
            String termSvnDpmC,
            String rmk,
            BigDecimal fcAmt) {
        this.spfTmnNm = spfTmnNm;
        this.tmnKdTc = tmnKdTc;
        this.nsfUsgCone = nsfUsgCone;
        this.tmnClsfC = tmnClsfC;
        this.termRqmBgAmt = termRqmBgAmt;
        this.curC = curC;
        this.xcr = xcr;
        this.xcrBseDt = xcrBseDt;
        this.dfrCleC = dfrCleC;
        this.indRsn = indRsn;
        this.cgprId = cgprId;
        this.termSvnTemC = termSvnTemC;
        this.termSvnDpmC = termSvnDpmC;
        this.rmk = rmk;
        this.fcAmt = fcAmt;
    }

    /**
     * 단말기에 연결할 전산관리비의 외래키를 설정합니다.
     *
     * @param termBgNo 예산번호
     * @param termBgSno 예산일련번호
     */
    public void setBcostmInfo(String termBgNo, Integer termBgSno) {
        this.termBgNo = termBgNo;
        this.termBgSno = termBgSno;
    }
}
