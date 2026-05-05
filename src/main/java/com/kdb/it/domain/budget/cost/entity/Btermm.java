package com.kdb.it.domain.budget.cost.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BtermmL;
import com.kdb.it.domain.entity.BaseEntity;
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
import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 단말기관리마스터 엔티티
 *
 * <p>
 * DB 테이블: {@code TAAABB_BTERMM}
 * </p>
 *
 * <p>
 * 금융정보단말기 관련 상세 정보를 관리하며, 전산관리비({@link Bcostm})와 1:N 관계를 가집니다.
 * </p>
 */
@LogTarget(entity = BtermmL.class)
@Entity
@Table(name = "TAAABB_BTERMM", comment = "단말기관리마스터")
@IdClass(BtermmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Btermm extends BaseEntity {

    /** 단말기관리번호 */
    @Id
    @Column(name = "TMN_MNG_NO", nullable = false, length = 32, comment = "단말기관리번호")
    private String tmnMngNo;

    /** 단말기일련번호 */
    @Id
    @Column(name = "TMN_SNO", nullable = false, length = 32, comment = "단말기일련번호")
    private String tmnSno;

    /** IT관리비관리번호 (조인용 필드) */
    @Column(name = "IT_MNGC_NO", length = 32, comment = "IT관리비관리번호")
    private String itMngcNo;

    /** IT관리비일련번호 (조인용 필드) */
    @Column(name = "IT_MNGC_SNO", comment = "IT관리비일련번호")
    private Integer itMngcSno;

    /** 전산관리비와의 연관관계 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "IT_MNGC_NO", referencedColumnName = "IT_MNGC_NO", insertable = false, updatable = false),
        @JoinColumn(name = "IT_MNGC_SNO", referencedColumnName = "IT_MNGC_SNO", insertable = false, updatable = false)
    })
    private Bcostm bcostm;

    /** 단말기명 */
    @Column(name = "TMN_NM", length = 100, comment = "단말기명")
    private String tmnNm;

    /** 단말기이용방법 */
    @Column(name = "TMN_TUZ_MANR", length = 100, comment = "단말기이용방법")
    private String tmnTuzManr;

    /** 단말기용도 */
    @Column(name = "TMN_USG", length = 100, comment = "단말기용도")
    private String tmnUsg;

    /** 단말기서비스 */
    @Column(name = "TMN_SVC", length = 100, comment = "단말기서비스")
    private String tmnSvc;

    /** 단말기금액 */
    @Column(name = "TML_AMT", precision = 15, scale = 0, comment = "단말기금액")
    private BigDecimal tmlAmt;

    /** 통화 */
    @Column(name = "CUR", length = 10, comment = "통화")
    private String cur;

    /** 환율 */
    @Column(name = "XCR", precision = 9, comment = "환율")
    private BigDecimal xcr;

    /** 환율기준일자 */
    @Column(name = "XCR_BSE_DT", comment = "환율기준일자")
    private LocalDate xcrBseDt;

    /** 지급주기 */
    @Column(name = "DFR_CLE", length = 100, comment = "지급주기")
    private String dfrCle;

    /** 증감사유 */
    @Column(name = "IND_RSN", length = 1000, comment = "증감사유")
    private String indRsn;

    /** 담당자 */
    @Column(name = "CGPR", length = 32, comment = "담당자")
    private String cgpr;

    /** 담당팀 */
    @Column(name = "BICE_TEM", length = 100, comment = "담당팀")
    private String biceTem;

    /** 담당부서 */
    @Column(name = "BICE_DPM", length = 100, comment = "담당부서")
    private String biceDpm;

    /** 비고 */
    @Column(name = "RMK", length = 1000, comment = "비고")
    private String rmk;

    /** 정보 업데이트 메서드 */
    public void update(String tmnNm, String tmnTuzManr, String tmnUsg, String tmnSvc, BigDecimal tmlAmt,
            String cur, BigDecimal xcr, LocalDate xcrBseDt, String dfrCle, String indRsn,
            String cgpr, String biceTem, String biceDpm, String rmk) {
        this.tmnNm = tmnNm;
        this.tmnTuzManr = tmnTuzManr;
        this.tmnUsg = tmnUsg;
        this.tmnSvc = tmnSvc;
        this.tmlAmt = tmlAmt;
        this.cur = cur;
        this.xcr = xcr;
        this.xcrBseDt = xcrBseDt;
        this.dfrCle = dfrCle;
        this.indRsn = indRsn;
        this.cgpr = cgpr;
        this.biceTem = biceTem;
        this.biceDpm = biceDpm;
        this.rmk = rmk;
    }

    /** 외래키 설정을 위한 편의 메서드 */
    public void setBcostmInfo(String itMngcNo, Integer itMngcSno) {
        this.itMngcNo = itMngcNo;
        this.itMngcSno = itMngcSno;
    }
}
