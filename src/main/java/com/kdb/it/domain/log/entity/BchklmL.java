package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 타당성 자체점검(TPRMPP_BCHKLM) 변경 로그 엔티티.
 *
 * <p>PK(LOG_HIS_TGR_SNO)는 AuditLogIdGenerator가 SEQ_BCHKLL.NEXTVAL로 채번한다.</p>
 */
@Entity
@Table(name = "TPRMPP_BCHKLL", comment = "타당성 자체점검 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BchklmL extends BaseLogEntity {

    @Column(name = "IT_PTL_ASCT_ID", length = 32, comment = "협의회ID")
    private String itPtlAsctId;

    @Column(name = "IT_PTL_CKG_ITM_TC", length = 2, comment = "점검항목코드")
    private String itPtlCkgItmTc;

    @Column(name = "QUEL_RCRD", comment = "문항점수")
    private Integer quelRcrd;

    @Column(name = "CKG_OPNN_CONE", length = 1000, comment = "점검의견내용")
    private String ckgOpnn;
}
