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
 * 타당성 자체점검(TAAABB_BCHKLC) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BCHKLL", comment = "타당성 자체점검 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BchklcL extends BaseLogEntity {

    @Column(name = "ASCT_ID", length = 32, comment = "협의회ID")
    private String asctId;

    @Column(name = "CKG_ITM_C", length = 20, comment = "점검항목코드")
    private String ckgItmC;

    @Column(name = "CKG_CONE", length = 2000, comment = "점검내용")
    private String ckgCone;

    @Column(name = "CKG_RCRD", comment = "점검점수")
    private Integer ckgRcrd;
}
