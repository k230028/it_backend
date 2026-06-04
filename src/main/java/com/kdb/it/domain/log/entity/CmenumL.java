package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 공통메뉴기본(Cmenum) 변경 스냅샷 로그. */
@Entity
@Table(name = "TPRMPP_CMENUL", comment = "공통메뉴로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CmenumL extends BaseLogEntity {

    @Column(name = "MNU_ID", length = 10, comment = "메뉴ID")
    private String mnuId;

    @Column(name = "HRK_MNU_ID", length = 10, comment = "상위메뉴ID")
    private String hrkMnuId;

    @Column(name = "SRE_TC", length = 2, comment = "화면구분코드")
    private String sreTc;

    @Column(name = "MNU_NM", length = 100, comment = "메뉴명")
    private String mnuNm;

    @Column(name = "MNU_TP_C", length = 3, comment = "메뉴유형코드")
    private String mnuTpC;

    @Column(name = "SRE_PTH", length = 300, comment = "화면경로")
    private String srePth;

    @Column(name = "MNU_SOT_SQN_SNO", comment = "메뉴정렬순서일련번호")
    private Integer mnuSotSqnSno;

    @Column(name = "HID_YN", length = 1, comment = "숨김여부")
    private String hidYn;

    @Column(name = "MNU_DEP", comment = "메뉴깊이")
    private Integer mnuDep;

    @Column(name = "WHL_MNU_PTH", length = 500, comment = "전체메뉴경로")
    private String whlMnuPth;
}
