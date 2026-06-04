package com.kdb.it.domain.menu.entity;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 공통메뉴권한연결(메뉴↔권한). 매핑 0건=전체 공개, 1건 이상=해당 권한만 노출. */
@Entity
@Table(name = "TPRMPP_CMENUA", comment = "공통메뉴권한연결")
@IdClass(CmenuaId.class)
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Cmenua extends BaseEntity {

    @Id
    @Column(name = "MNU_ID", length = 10, nullable = false, comment = "메뉴ID")
    private String mnuId;

    @Id
    @Column(name = "ATH_ID", length = 32, nullable = false, comment = "권한ID")
    private String athId;
}
