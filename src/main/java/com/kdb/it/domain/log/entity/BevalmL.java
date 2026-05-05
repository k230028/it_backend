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
 * 평가위원 평가의견(TAAABB_BEVALM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BEVALL", comment = "평가위원 평가의견 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BevalmL extends BaseLogEntity {

    @Column(name = "ASCT_ID", length = 32, comment = "협의회ID")
    private String asctId;

    @Column(name = "ENO", length = 32, comment = "사번")
    private String eno;

    @Column(name = "CKG_ITM_C", length = 20, comment = "점검항목코드")
    private String ckgItmC;

    @Column(name = "CKG_RCRD", comment = "점검점수")
    private Integer ckgRcrd;

    @Column(name = "CKG_OPNN", length = 2000, comment = "점검의견")
    private String ckgOpnn;
}
