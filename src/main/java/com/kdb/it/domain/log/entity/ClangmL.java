package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 구분언어마스터(TPRMPP_CLANGM) 변경 로그 엔티티. */
@Entity
@Table(name = "TPRMPP_CLANGL", comment = "프로젝트관리_구분언어기본변경로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ClangmL extends BaseLogEntity {

    // 변경로그 복사는 @Column(name)으로 매칭하므로 마스터(Clangm)와 컬럼명이 일치해야 한다.
    @Column(name = "TC_ID_CONE", length = 255, comment = "구분코드ID내용")
    private String tcIdCone;

    @Column(name = "DTT_LAN_C", length = 2, comment = "구분언어코드")
    private String dttLanC;

    @Column(name = "TC_COL_NM", length = 255, comment = "구분코드컬럼명")
    private String tcColNm;

    @Column(name = "TC_DES", length = 2000, comment = "구분코드설명")
    private String tcDes;

    @Column(name = "DTT_NM", length = 100, comment = "구분명")
    private String dttNm;
}
