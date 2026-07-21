package com.kdb.it.domain.bizplan.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BbizgmL;
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
 * 사업품목 기본 엔티티. DB 테이블: {@code TPRMPP_BBIZGM}, PK=(ABUS_MNG_NO, SNO).
 *
 * <p>{@code CTT_SNO}는 같은 사업계획의 사업계약({@code TPRMPP_BBIZCM}) SNO를 참조한다(선택).
 */
@LogTarget(entity = BbizgmL.class)
@Entity
@Table(name = "TPRMPP_BBIZGM", comment = "사업품목기본")
@IdClass(BbizgmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bbizgm extends BaseEntity {

    @Id
    @Column(name = "ABUS_MNG_NO", length = 30, nullable = false, comment = "사업관리번호")
    private String abusMngNo;

    @Id
    @Column(name = "SNO", nullable = false, comment = "일련번호")
    private Integer sno;

    @Column(name = "GCL_NM", length = 100, comment = "품목명")
    private String gclNm;

    @Column(name = "IOE_C", length = 7, comment = "비목코드")
    private String ioeC;

    @Column(name = "QTY", comment = "수량")
    private Long qty;

    @Column(name = "AMT", precision = 18, scale = 3, comment = "금액(KRW 환산)")
    private BigDecimal amt;

    @Column(name = "FC_AMT", precision = 18, scale = 3, comment = "외화금액")
    private BigDecimal fcAmt;

    @Column(name = "CUR_C", length = 3, comment = "통화코드")
    private String curC;

    @Column(name = "XCR", precision = 9, scale = 4, comment = "환율")
    private BigDecimal xcr;

    @Column(name = "XCR_BSE_DT", length = 8, comment = "환율기준일자(YYYYMMDD)")
    private String xcrBseDt;

    @Column(name = "CTT_SNO", comment = "계약일련번호(BBIZCM.SNO 참조, 선택)")
    private Integer cttSno;

    /** 품목 행 갱신 (save 병합에서 호출) */
    public void updateItem(
            String gclNm,
            String ioeC,
            Long qty,
            BigDecimal amt,
            BigDecimal fcAmt,
            String curC,
            BigDecimal xcr,
            String xcrBseDt,
            Integer cttSno) {
        this.gclNm = gclNm;
        this.ioeC = ioeC;
        this.qty = qty;
        this.amt = amt;
        this.fcAmt = fcAmt;
        this.curC = curC;
        this.xcr = xcr;
        this.xcrBseDt = xcrBseDt;
        this.cttSno = cttSno;
    }
}
