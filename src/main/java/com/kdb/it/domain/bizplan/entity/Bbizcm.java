package com.kdb.it.domain.bizplan.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BbizcmL;
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

/** 사업계약 기본 엔티티. DB 테이블: {@code TPRMPP_BBIZCM}, PK=(ABUS_MNG_NO, SNO). */
@LogTarget(entity = BbizcmL.class)
@Entity
@Table(name = "TPRMPP_BBIZCM", comment = "사업계약기본")
@IdClass(BbizcmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bbizcm extends BaseEntity {

    @Id
    @Column(name = "ABUS_MNG_NO", length = 30, nullable = false, comment = "사업관리번호")
    private String abusMngNo;

    @Id
    @Column(name = "SNO", nullable = false, comment = "일련번호")
    private Integer sno;

    @Column(name = "CTT_NM", length = 100, comment = "계약명")
    private String cttNm;

    @Column(name = "NOW_CTT_MANR_C", length = 2, comment = "현재계약방법코드")
    private String nowCttManrC;

    @Column(name = "CTT_TRM_MM_NBR", comment = "계약기간월수")
    private Integer cttTrmMmNbr;

    /** 계약 행 갱신 (save 병합에서 호출) */
    public void updateContract(String cttNm, String nowCttManrC, Integer cttTrmMmNbr) {
        this.cttNm = cttNm;
        this.nowCttManrC = nowCttManrC;
        this.cttTrmMmNbr = cttTrmMmNbr;
    }
}
