package com.kdb.it.domain.menu.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.CmenumL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** 공통메뉴기본(메뉴 마스터). 변경 시 CmenumL로 자동 스냅샷 로깅(@LogTarget). */
@LogTarget(entity = CmenumL.class)
@Entity
@Table(name = "TPRMPP_CMENUM", comment = "공통메뉴기본")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Cmenum extends BaseEntity {

    @Id
    @Column(name = "MNU_ID", length = 10, nullable = false, comment = "메뉴ID")
    private String mnuId;

    @Column(name = "HRK_MNU_ID", length = 10, comment = "상위메뉴ID")
    private String hrkMnuId;

    @Column(name = "MNU_NM", length = 100, nullable = false, comment = "메뉴명")
    private String mnuNm;

    @Column(name = "MNU_TP_C", length = 3, nullable = false, comment = "메뉴유형코드")
    private String mnuTpC;

    @Column(name = "SRE_PTH", length = 300, comment = "화면경로")
    private String srePth;

    @Column(name = "MNU_SOT_SQN_SNO", nullable = false, comment = "메뉴정렬순서일련번호")
    private Integer mnuSotSqnSno;

    @Column(name = "HID_YN", length = 1, nullable = false, comment = "숨김여부")
    private String hidYn;

    @Column(name = "MNU_DEP", nullable = false, comment = "메뉴깊이")
    private Integer mnuDep;

    @Column(name = "WHL_MNU_PTH", length = 500, nullable = false, comment = "전체메뉴경로")
    private String whlMnuPth;
}
