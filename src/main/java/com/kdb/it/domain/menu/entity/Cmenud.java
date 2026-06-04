package com.kdb.it.domain.menu.entity;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 공통화면상세(라우트 카탈로그). PK는 화면경로(SRE_PTH). */
@Entity
@Table(name = "TPRMPP_CMENUD", comment = "공통화면상세")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Cmenud extends BaseEntity {

    @Id
    @Column(name = "SRE_PTH", length = 300, nullable = false, comment = "화면경로")
    private String srePth;

    @Column(name = "SRE_MNU_NM", length = 100, nullable = false, comment = "화면메뉴명")
    private String sreMnuNm;

    @Column(name = "SYS_HRK_MNU_ID", length = 10, comment = "시스템상위메뉴ID")
    private String sysHrkMnuId;

    @Column(name = "USE_YN", length = 1, nullable = false, comment = "사용여부")
    private String useYn;

    @Column(name = "RMK", length = 300, comment = "비고")
    private String rmk;
}
