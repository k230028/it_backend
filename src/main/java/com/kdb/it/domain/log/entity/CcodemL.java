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
 * 코드 마스터(TPRMPP_CCODEM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TPRMPP_CCODEL", comment = "프로젝트관리_공통코드기본변경로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CcodemL extends BaseLogEntity {

    // 변경로그 복사는 @Column(name)으로 매칭하므로 마스터(Ccodem)와 컬럼명이 일치해야 한다.
    @Column(name = "CO_C_ID", length = 20, comment = "공통코드ID")
    private String cId;

    @Column(name = "CDVA_ID", length = 40, comment = "코드값ID")
    private String cdva;

    @Column(name = "STT_DTM", comment = "시작일시")
    private LocalDate sttDt;

    @Column(name = "END_DTM", comment = "종료일시")
    private LocalDate endDt;

    @Column(name = "CO_C_NM", length = 100, comment = "공통코드명")
    private String cNm;

    @Column(name = "CO_CDVA_ABV_NM", length = 100, comment = "공통코드값약어명")
    private String cdvaDes;

    @Column(name = "CO_CDVA_SPS", length = 2000, comment = "공통코드값적요")
    private String cdvaDtl;

    @Column(name = "CO_C_INTN_NM", length = 200, comment = "공통코드인스턴스명")
    private String cTp;

    @Column(name = "CO_C_INTN_CONE", length = 500, comment = "공통코드인스턴스내용")
    private String cTpDes;

    @Column(name = "HRK_CDVA_ID", length = 40, comment = "상위코드값ID")
    private String hrkC;

    @Column(name = "C_SQN_SNO", comment = "코드순서일련번호")
    private Integer cSqn;

    @Column(name = "CO_CDVA_NM", length = 500, comment = "공통코드값명")
    private String cdvaDtlC;
}
