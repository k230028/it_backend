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
 * 협의회 평가위원(TAAABB_BCMMTM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BCMMTL", comment = "협의회 평가위원 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BcmmtmL extends BaseLogEntity {

    @Column(name = "ASCT_ID", length = 32, comment = "협의회ID")
    private String asctId;

    @Column(name = "ENO", length = 32, comment = "사번")
    private String eno;

    @Column(name = "VLR_TP", length = 32, comment = "위원유형")
    private String vlrTp;
}
