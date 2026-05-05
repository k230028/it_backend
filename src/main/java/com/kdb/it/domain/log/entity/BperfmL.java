package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 성과관리 자체계획(TAAABB_BPERFM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BPERFL", comment = "성과관리 자체계획 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BperfmL extends BaseLogEntity {

    @Column(name = "ASCT_ID", length = 32, comment = "협의회ID")
    private String asctId;

    @Column(name = "DTP_SNO", comment = "지표순번")
    private Integer dtpSno;

    @Column(name = "DTP_NM", length = 200, comment = "성과지표명")
    private String dtpNm;

    @Column(name = "DTP_CONE", length = 1000, comment = "성과지표정의")
    private String dtpCone;

    @Column(name = "MSM_MANR", length = 1000, comment = "측정방법")
    private String msmManr;

    @Column(name = "CLF", length = 1000, comment = "산식")
    private String clf;

    @Column(name = "GL_NV", length = 200, comment = "목표치")
    private String glNv;

    @Column(name = "MSM_STT_DT", comment = "측정시작일")
    private LocalDate msmSttDt;

    @Column(name = "MSM_END_DT", comment = "측정종료일")
    private LocalDate msmEndDt;

    @Column(name = "MSM_TPM", length = 100, comment = "측정시점")
    private String msmTpm;

    @Column(name = "MSM_CLE", length = 100, comment = "측정주기")
    private String msmCle;
}
